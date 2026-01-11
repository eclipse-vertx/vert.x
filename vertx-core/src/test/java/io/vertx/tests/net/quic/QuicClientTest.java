/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.net.quic;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.NetUtil;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.*;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.net.ssl.SSLHandshakeException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.tests.net.quic.QuicServerTest.serverOptions;

@RunWith(LinuxOrOsx.class)
public class QuicClientTest extends VertxTestBase {

  static QuicClientOptions clientOptions() {
    QuicClientOptions options = new QuicClientOptions();
    options.getSslOptions().setTrustOptions(Trust.SERVER_JKS.get());
    options.getSslOptions().setApplicationLayerProtocols(List.of("test-protocol"));
    options.getTransportOptions().setInitialMaxData(10000000L);
    options.getTransportOptions().setInitialMaxStreamDataBidirectionalLocal(1000000L);
    return options;
  }

  private QuicServer server;
  private QuicClient client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    server = QuicServer.create(vertx, serverOptions());
    client = QuicClient.create(vertx, clientOptions());
  }

  @Override
  protected void tearDown() throws Exception {
    client.close().await();
    server.close().await();
    super.tearDown();
  }

  @Test
  public void testConnection() {
    AtomicInteger inflight = new AtomicInteger();
    server.handler(conn -> {
      inflight.getAndIncrement();
      conn.closeHandler(v -> {
        inflight.getAndDecrement();
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    assertWaitUntil(() -> inflight.get() == 1);
    connection.close().await();
    assertWaitUntil(() -> inflight.get() == 0);
  }

  @Test
  public void testClientSSLOverride() {
    QuicServerOptions serverOptions = serverOptions();
    serverOptions.getSslOptions().setKeyCertOptions(Cert.CLIENT_JKS.get());
    // server.close();
    server = QuicServer.create(vertx, serverOptions);
    AtomicInteger inflight = new AtomicInteger();
    server.handler(conn -> {
      inflight.getAndIncrement();
      conn.closeHandler(v -> {
        inflight.getAndDecrement();
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    try {
      client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
      fail();
    } catch (Exception e) {
      assertSame(SSLHandshakeException.class, e.getClass());
    }
    QuicClientOptions clientOptions = clientOptions();
    clientOptions.getSslOptions().setTrustOptions(Trust.CLIENT_JKS.get());
    QuicConnectOptions connectOptions = new QuicConnectOptions().setSslOptions(clientOptions.getSslOptions());
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost"), connectOptions).await();
    assertWaitUntil(() -> inflight.get() == 1);
    connection.close().await();
    assertWaitUntil(() -> inflight.get() == 0);
  }

  @Test
  public void testCreateStream() throws Exception {
    server.handler(conn -> {
      conn.streamHandler(stream -> {
        stream.handler(buff -> stream.write(buff));
        stream.endHandler(v -> stream.end());
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicStream stream = connection.openStream().await();
    List<Buffer> received = Collections.synchronizedList(new ArrayList<>());
    stream.handler(buff -> received.add(buff));
    CountDownLatch latch = new CountDownLatch(1);
    stream.endHandler(v -> {
      latch.countDown();
    });
    stream.end(Buffer.buffer("ping"));
    awaitLatch(latch);
    assertEquals(List.of(Buffer.buffer("ping")), received);
  }

  @Test
  public void testServerReset() {

    waitFor(4);
    server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.streamHandler(stream -> {
        stream.handler(buff -> {
          stream.reset(4).onComplete(onSuccess2(v -> complete()));
        });
        stream.endHandler(v -> {
          complete();
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();

    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicStream stream = connection.openStream().await();

    stream.resetHandler(code -> {
      assertEquals(4L, (long)code);
      complete();
      stream.end();
    });
    stream.closeHandler(v -> complete());

    stream.write("ping");

    await();
  }

  @Test
  public void testClientReset() {

    waitFor(2);
    server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.streamHandler(stream -> {
        stream.resetHandler(code -> {
          assertEquals(10L, (long)code);
          vertx.setTimer(20, id -> {
            stream.end(Buffer.buffer("done"));
          });
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();

    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicStream stream = connection.openStream().await();

    AtomicBoolean isReset = new AtomicBoolean();
    Buffer buffer = Buffer.buffer();
    stream.handler(buff -> {
      if (isReset.compareAndSet(false, true)) {
        stream.reset(0).onComplete(onSuccess2(v -> complete()));
      } else {
        buffer.appendBuffer(buff);
      }
    });
    stream.endHandler(v -> {
      complete();
    });

    stream.write("ping").await();
    stream.reset(10);

    await();
  }

  @Test
  public void testClientResetHandler() {

    waitFor(2);
    server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.streamHandler(stream -> {
        AtomicBoolean isReset = new AtomicBoolean();
        Buffer buffer = Buffer.buffer();
        stream.handler(buff -> {
          if (isReset.compareAndSet(false, true)) {
            stream.reset(0).onComplete(onSuccess2(v -> complete()));
          } else {
            buffer.appendBuffer(buff);
          }
        });
        stream.endHandler(v -> {
          complete();
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();

    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicStream stream = connection.openStream().await();

    stream.exceptionHandler(t -> fail());
    stream.resetHandler(code -> {
      vertx.setTimer(20, id -> {
        stream.end(Buffer.buffer("done"));
      });
    });

    stream.write("ping");

    await();
  }

  @Test
  public void testShutdownConnection() throws Exception {
    testShutdown(true);
  }

  @Test
  public void testShutdownServer() throws Exception {
    testShutdown(false);
  }

  public void testShutdown(boolean shutdownConnection) throws Exception {

    disableThreadChecks();
    int numStreams = 5;

    AtomicInteger clientEndCount = new AtomicInteger();
    AtomicInteger serverEndCount = new AtomicInteger();

    server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.streamHandler(stream -> {
        stream.endHandler(v -> {
          vertx.setTimer(100, id -> {
            serverEndCount.incrementAndGet();
            stream.end();
          });
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();

    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();

    AtomicInteger shutdownCount = new AtomicInteger();
    for (int i = 0;i < numStreams;i++) {
      QuicStream stream = connection.openStream().await();
      stream.shutdownHandler(v -> {
        shutdownCount.incrementAndGet();
        vertx.setTimer(100, id -> {
          assertEquals(0, serverEndCount.get());
          clientEndCount.incrementAndGet();
          stream.end();
        });
      });
      stream.write("ping").await();
    }

    Future<Void> res;
    if (shutdownConnection) {
      res = connection.shutdown(Duration.ofSeconds(10));
    } else {
      res = client.shutdown(Duration.ofSeconds(10));
    }
    res.await();
    assertEquals(numStreams, shutdownCount.get());
    assertEquals(numStreams, clientEndCount.get());
    assertEquals(numStreams, serverEndCount.get());
  }

  @Test
  public void testConnectTimeout() {
    QuicConnectOptions options = new QuicConnectOptions().setTimeout(Duration.ofMillis(250));
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    long now = System.currentTimeMillis();
    try {
      client.connect(SocketAddress.inetSocketAddress(1234, TestUtils.NON_ROUTABLE_HOST), options).await();
      fail();
    } catch (Exception e) {
      assertEquals(ConnectTimeoutException.class, e.getClass());
      long delta = System.currentTimeMillis() - now;
      assertTrue(delta >= 250);
      assertTrue(delta <= 250 * 2);
    }
  }

  @Test
  public void testStreamIdleTimeout() throws Exception {
    QuicServerOptions options = serverOptions();
    options.setStreamIdleTimeout(Duration.ofMillis(100));
    QuicServer server = QuicServer.create(vertx, options);
    server.handler(conn -> {
      conn.streamHandler(stream -> {
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();

    client.close();
    client = QuicClient.create(vertx, clientOptions().setStreamIdleTimeout(Duration.ofMillis(100)));
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicStream stream = connection.openStream().await();
    long now = System.currentTimeMillis();
    AtomicInteger idleEvents = new AtomicInteger();
    ((QuicStreamInternal)stream).eventHandler(event -> {
      if (event instanceof IdleStateEvent) {
        idleEvents.incrementAndGet();
      }
    });
    stream.closeHandler(v -> {
      long delta = System.currentTimeMillis() - now;
      assertTrue(delta >= 100);
      assertTrue(delta <= 300);
      assertEquals(1, idleEvents.get());
      testComplete();
    });
    stream.write("ping").await();
    await();
  }

  @Test
  public void testServerNameIndication() {
    QuicServerOptions options = serverOptions();
    options.getSslOptions().setKeyCertOptions(Cert.SNI_JKS.get());
    server = QuicServer.create(vertx, options);
    AtomicReference<String> serverName = new AtomicReference<>();
    server.handler(conn -> {
      serverName.set(conn.indicatedServerName());
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnectOptions connectOptions = new QuicConnectOptions()
      .setServerName("host2.com")
      .setSslOptions(new ClientSSLOptions()
        .setTrustAll(true)
        .setApplicationLayerProtocols(List.of("test-protocol")));
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost"),
      connectOptions).await();
    connection.close().await();
    assertEquals("host2.com", serverName.get());
  }

  @Test
  public void testSocketAddressResolution() throws UnknownHostException {
    server.handler(conn -> {
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    String doesNotResolve = TestUtils.randomAlphaString(32);
    SocketAddress addr = SocketAddress.inetSocketAddress(new InetSocketAddress(Inet4Address.getByAddress(doesNotResolve, NetUtil.LOCALHOST4.getAddress()), 9999));
    QuicConnection connection = client.connect(addr).await();
    assertEquals(doesNotResolve, connection.remoteAddress().hostName());
    connection.close().await();
  }
}
