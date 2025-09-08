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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.quic.ConnectionClose;
import io.vertx.core.quic.QuicClientOptions;
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
      // assertFalse(connection.closeApplicationClose());
    } finally {
      client.close();
      server.close().await();
    }
  }
}
