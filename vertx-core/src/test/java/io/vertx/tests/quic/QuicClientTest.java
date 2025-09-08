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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.quic.QuicClient;
import io.vertx.core.quic.QuicClientOptions;
import io.vertx.core.quic.QuicConnection;
import io.vertx.core.quic.QuicServer;
import io.vertx.core.quic.QuicStream;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Trust;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.tests.quic.QuicServerTest.serverOptions;

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
  public void testCreateStream() throws Exception {
    server.handler(conn -> {
      conn.handler(stream -> {
        stream.handler(buff -> stream.write(buff));
        stream.endHandler(v -> stream.end());
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection connection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicStream stream = connection.createStream().await();
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
}
