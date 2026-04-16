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
import io.vertx.core.net.*;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakemetrics.FakeMetricsFactory;
import io.vertx.test.fakemetrics.FakeQuicEndpointMetrics;
import io.vertx.test.fakemetrics.FakeTransportMetrics;
import io.vertx.test.fakemetrics.ConnectionMetric;
import io.vertx.test.core.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class QuicMetricsTest extends VertxTestBase {

  @Override
  protected VertxMetricsFactory getMetrics() {
    return new FakeMetricsFactory();
  }

  private List<QuicServer> servers = new ArrayList<>();
  private QuicClient client;

  public QuicMetricsTest() {
    super(ReportMode.FORBIDDEN);
  }

  @Override
  protected void tearDown() throws Exception {
    client.close().await();
    List<QuicServer> toClose = new ArrayList<>(servers);
    servers.clear();
    for (QuicServer server : toClose) {
      server.close().await();
    }
    super.tearDown();
  }

  @Repeat(times = 1000)
  @Test
  public void testSingleServer() throws Exception {
    testMetrics(1);
  }

  @Test
  public void testMultiServer() throws Exception {
    testMetrics(2);
  }

  private void testMetrics(int numberOfServers) throws Exception {
    QuicClientConfig cfg = new QuicClientConfig()
      .setConnectTimeout(Duration.ofSeconds(10))
      .setMetricsName("the-metrics");
    client = vertx.createQuicClient(cfg, QuicClientTest.SSL_OPTIONS);
    AtomicReference<ConnectionMetric> serverConnectionMetric = new AtomicReference<>();
    int port;
    int i = 0;
    do {
      QuicServer server = vertx.createQuicServer(new QuicServerConfig().setLoadBalanced(numberOfServers > 1), QuicServerTest.SSL_OPTIONS);
      server.connectHandler(conn -> {
        System.out.println("client connected");
        FakeQuicEndpointMetrics serverMetrics = FakeTransportMetrics.quicMetricsOf(server);
        Assert.assertNull(serverMetrics.protocol());
        Assert.assertEquals(1, serverMetrics.connectionCount());
        serverConnectionMetric.set(serverMetrics.firstMetric(conn.remoteAddress()));
        conn.streamHandler(stream -> {
          System.out.println("client stream open");
          stream.handler(buff -> stream.write(buff));
          stream.endHandler(v -> stream.end());
        });
      });
      port = server.bind(SocketAddress.sharedRandomPort(1, "localhost")).await().port();
      servers.add(server);
    }
    while (i++ < numberOfServers);
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnection clientConnection = client.connect(SocketAddress.inetSocketAddress(port, "localhost")).await();
    FakeQuicEndpointMetrics clientMetrics = FakeTransportMetrics.quicMetricsOf(client);
    Assert.assertNull(clientMetrics.protocol());
    Assert.assertEquals("the-metrics", clientMetrics.name());
    Assert.assertEquals(1, clientMetrics.connectionCount());
    ConnectionMetric clientConnectionMetric = clientMetrics.firstMetric(clientConnection.remoteAddress());
    List<Buffer> received = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch latch = new CountDownLatch(1);
    QuicStream clientStream = clientConnection.openStream().await();
    clientStream.handler(buff -> received.add(buff));
    clientStream.endHandler(v -> {
      System.out.println("client stream closed");
      latch.countDown();
    });
    clientStream.write(Buffer.buffer("ping")).await();
    System.out.println("step #1");
    assertWaitUntil(() -> clientConnectionMetric.openStreams.get() == 1, 30_000);
    System.out.println("step #2");
    assertWaitUntil(() -> serverConnectionMetric.get().openStreams.get() == 1, 30_000);
    System.out.println("step #3");
    clientStream.end().await();
    System.out.println("step #4");
    System.out.println("step #5");
    assertWaitUntil(() -> serverConnectionMetric.get().openStreams.get() == 0, 30_000);
    System.out.println("step #6");
    TestUtils.awaitLatch(latch);
    System.out.println("step #7");
    Assert.assertEquals(List.of(Buffer.buffer("ping")), received);
    FakeQuicEndpointMetrics serverMetrics = FakeTransportMetrics.quicMetricsOf(servers.get(0));
    Assert.assertNull(serverMetrics.protocol());
    clientConnection.close().await();
    assertWaitUntil(() -> serverMetrics.connectionCount() == 0);
    assertWaitUntil(() -> clientMetrics.connectionCount() == 0);
    Assert.assertEquals(4, serverConnectionMetric.get().bytesRead.get());
    Assert.assertEquals(4, serverConnectionMetric.get().bytesWritten.get());
    Assert.assertEquals(4, clientConnectionMetric.bytesRead.get());
    Assert.assertEquals(4, clientConnectionMetric.bytesWritten.get());
    assertWaitUntil(() -> clientConnectionMetric.openStreams.get() == 0, 30_000);
  }
}
