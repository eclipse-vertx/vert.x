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
package io.vertx.tests.quic;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.NetUtil;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.quic.ConnectionClose;
import io.vertx.core.quic.QuicServer;
import io.vertx.core.quic.QuicServerOptions;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.tests.net.QuicNettyTest;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(LinuxOrOsx.class)
public class QuicServerTest extends VertxTestBase {

  static QuicServerOptions serverOptions() {
    QuicServerOptions options = new QuicServerOptions();
    options.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
    options.getSslOptions().setApplicationLayerProtocols(List.of("test-protocol"));
    options.getTransportOptions().setInitialMaxData(10000000L);
    options.getTransportOptions().setInitialMaxStreamDataBidirectionalLocal(1000000L);
    options.getTransportOptions().setInitialMaxStreamDataBidirectionalRemote(1000000L);
    options.getTransportOptions().setInitialMaxStreamDataUnidirectional(1000000L);
    options.getTransportOptions().setInitialMaxStreamsBidirectional(100L);
    options.getTransportOptions().setInitialMaxStreamsUnidirectional(100L);
    options.getTransportOptions().setActiveMigration(true);
    return options;
  }

  @Test
  public void testBind() {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    server.close().await();
  }

  @Test
  public void testConnect() throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      assertEquals("test-protocol", conn.applicationLayerProtocol());

      conn.handler(stream -> {
        stream.handler(buff -> {
          stream.write(buff);
        });
        stream.endHandler(v -> {
          stream.end();
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      QuicNettyTest.TestClient.Stream stream = connection.newStream();
      List<String> received = Collections.synchronizedList(new ArrayList<>());
      stream.handler(data -> {
        received.add(new String(data, StandardCharsets.UTF_8));
      });
      stream.create();
      stream.write("Hello");
      assertWaitUntil(() -> received.equals(List.of("Hello")));
      stream.close();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testClientConnectionClose() throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    AtomicInteger inflights = new AtomicInteger();
    server.handler(conn -> {
      inflights.incrementAndGet();
      conn.closeHandler(v -> {
        assertEquals(3, conn.closePayload().getError());
        assertEquals("done", conn.closePayload().getReason().toString());
        inflights.decrementAndGet();
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      assertWaitUntil(() -> inflights.get() == 1);
      connection.close(3, "done".getBytes(StandardCharsets.UTF_8));
      assertWaitUntil(() -> inflights.get() == 0);
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testServerConnectionClose() throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.handler(stream -> {
        stream.handler(buff -> {
          vertx.setTimer(1, id -> {
            conn.close(new ConnectionClose().setError(3).setReason(Buffer.buffer("done")));
          });
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
      CountDownLatch closeLatch = new CountDownLatch(1);
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      connection.closeHandler(v -> closeLatch.countDown());
      QuicNettyTest.TestClient.Stream stream = connection.newStream();
      List<String> received = Collections.synchronizedList(new ArrayList<>());
      stream.handler(data -> {
        received.add(new String(data, StandardCharsets.UTF_8));
      });
      stream.create();
      stream.write("ping");
      assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
      assertEquals(3, connection.closeError());
      assertEquals("done", new String(connection.closeReason()));
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testServerCreatesStream() throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.createStream().onComplete(onSuccess2(stream -> {
        stream.write("ping");
        stream.handler(buff -> {
          assertEquals("pong", buff.toString());
          testComplete();
        });
      }));
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      QuicNettyTest.TestClient.Connection connection = client.connection();
      connection.handler(stream -> {
        stream.write("pong");
      });
      connection.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testServerCreatesUniStream() throws Exception {
    disableThreadChecks();
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.createStream(false).onComplete(onSuccess2(stream -> {
        assertFalse(stream.isBidirectional());
        assertTrue(stream.isLocalCreated());
        stream.write("ping");
      }));
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      QuicNettyTest.TestClient.Connection connection = client.connection();
      connection.handler(stream -> {
        stream.handler(data -> {
          assertEquals("ping", new String(data, StandardCharsets.UTF_8));
          testComplete();
        });
      });
      connection.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testClientCreatesUniStream() throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.handler(stream -> {
        assertFalse(stream.isBidirectional());
        assertFalse(stream.isLocalCreated());
        Future<Void> f = stream.write("pong");
        f.onComplete(onFailure(err -> {
          testComplete();
        }));
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      connection.newStream().create(false).write("ping");
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  /*  @Test
  public void testSoReuse() throws Exception {

    InetSocketAddress addr = new InetSocketAddress("localhost", 4000);

    int num = 2;
    List<AtomicInteger> received = new ArrayList<>();
    for (int i = 0;i < num;i++) {
      AtomicInteger r = new AtomicInteger();
      received.add(r);
      new Thread(() -> {
        try {
          byte[] buff = new byte[65535];
          DatagramSocket socket = new DatagramSocket(null);
          socket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
          socket.bind(addr);
          while (true) {
            DatagramPacket packet = new DatagramPacket(buff, buff.length);
            socket.receive(packet);
            r.incrementAndGet();
          }
        } catch (Exception e) {
          e.printStackTrace(System.out);
        }
      }).start();
    }

    int count = 1;
    AtomicInteger received1 = received.get(0);
    AtomicInteger received2 = received.get(1);
    final CountDownLatch latch = new CountDownLatch(count);
    AtomicInteger sent = new AtomicInteger();
    Runnable r = () -> {
      try {
        DatagramSocket socket = new DatagramSocket();
        while (received1.get() == 0 || received2.get() == 0) {
          final byte[] bytes = "data".getBytes();
          socket.send(new java.net.DatagramPacket(bytes, 0, bytes.length, addr.getAddress(), addr.getPort()));
          Thread.sleep(1);
        }
        socket.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
      latch.countDown();
    };

    ExecutorService executor = Executors.newFixedThreadPool(count);
    for (int i = 0 ; i < count; i++) {
      executor.execute(r);
    }

    latch.await();
    executor.shutdown();

    System.out.println(received1.get());
    System.out.println(received2.get());
    System.out.println(sent.get());
  }*/
}
