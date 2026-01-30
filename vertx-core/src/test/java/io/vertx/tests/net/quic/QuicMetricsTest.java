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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.QuicClient;
import io.vertx.core.net.QuicConnection;
import io.vertx.core.net.QuicServer;
import io.vertx.core.net.QuicStream;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakemetrics.FakeMetricsFactory;
import io.vertx.test.fakemetrics.FakeQuicEndpointMetrics;
import io.vertx.test.fakemetrics.FakeTransportMetrics;
import io.vertx.test.fakemetrics.SocketMetric;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.tests.net.quic.QuicClientTest.clientOptions;
import static io.vertx.tests.net.quic.QuicServerTest.serverOptions;

@RunWith(LinuxOrOsx.class)
public class QuicMetricsTest extends VertxTestBase {

  @Override
  protected VertxMetricsFactory getMetrics() {
    return new FakeMetricsFactory();
  }

  private List<QuicServer> servers = new ArrayList<>();
  private QuicClient client;

  @Override
  protected void tearDown() throws Exception {
    if (client != null) {
      client.close().await();
    }
    List<QuicServer> toClose = new ArrayList<>(servers);
    servers.clear();
    for (QuicServer server : toClose) {
      server.close().await();
    }
    super.tearDown();
  }

  @Test
  public void testSingleServer() throws Exception {
    testMetrics(1);
  }

  @Test
  public void testMultiServer() throws Exception {
    testMetrics(2);
  }

  private void testMetrics(int numberOfServers) throws Exception {
    client = QuicClient.create(vertx, clientOptions().setMetricsName("the-metrics"), QuicClientTest.SSL_OPTIONS);
    AtomicReference<SocketMetric> serverConnectionMetric = new AtomicReference<>();
    for (int i = 0;i < numberOfServers;i++) {
      QuicServer server = QuicServer.create(vertx, serverOptions().setLoadBalanced(numberOfServers > 1), QuicServerTest.SSL_OPTIONS);
      server.handler(conn -> {
        FakeQuicEndpointMetrics serverMetrics = FakeTransportMetrics.getMetrics(server);
        assertEquals(1, serverMetrics.connectionCount());
        serverConnectionMetric.set(serverMetrics.firstMetric(conn.remoteAddress()));
        conn.streamHandler(stream -> {
          stream.handler(buff -> stream.write(buff));
          stream.endHandler(v -> stream.end());
        });
      });
      server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
      servers.add(server);
    }
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection clientConnection = client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    FakeQuicEndpointMetrics clientMetrics = FakeTransportMetrics.getMetrics(client);
    assertEquals("the-metrics", clientMetrics.name());
    assertEquals(1, clientMetrics.udpBindings().size());
    SocketAddress localAddress = clientMetrics.udpBindings().iterator().next();
    assertEquals("localhost", localAddress.hostName());
    assertTrue(localAddress.port() > 0);
    assertEquals(1, clientMetrics.connectionCount());
    SocketMetric clientConnectionMetric = clientMetrics.firstMetric(clientConnection.remoteAddress());
    QuicStream clientStream = clientConnection.openStream().await();
    List<Buffer> received = Collections.synchronizedList(new ArrayList<>());
    clientStream.handler(buff -> received.add(buff));
    CountDownLatch latch = new CountDownLatch(1);
    clientStream.endHandler(v -> {
      latch.countDown();
    });
    clientStream.end(Buffer.buffer("ping"));
    awaitLatch(latch);
    assertEquals(List.of(Buffer.buffer("ping")), received);
    FakeQuicEndpointMetrics serverMetrics = FakeTransportMetrics.getMetrics(servers.get(0));
    clientConnection.close().await();
    assertWaitUntil(() -> serverMetrics.connectionCount() == 0);
    assertWaitUntil(() -> clientMetrics.connectionCount() == 0);
    assertEquals(4, serverConnectionMetric.get().bytesRead.get());
    assertEquals(4, serverConnectionMetric.get().bytesWritten.get());
    assertEquals(4, clientConnectionMetric.bytesRead.get());
    assertEquals(4, clientConnectionMetric.bytesWritten.get());
    client.close().await();
    assertTrue(clientMetrics.udpBindings().isEmpty());
  }
}
