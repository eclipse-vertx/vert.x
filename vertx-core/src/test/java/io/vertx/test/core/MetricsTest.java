/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.codahale.metrics.MetricRegistry.*;
import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class MetricsTest extends AsyncTestBase {

  private VertxInternal vertx; // we rely on internal Vertx API, but I think this is ok for now (for tests at least)

  @Before
  public void before() {
    vertx = (VertxInternal) Vertx.vertx(VertxOptions.options().setMetricsEnabled(true).setJmxEnabled(true));
  }

  @After
  public void after() {
    vertx.close();
  }

  //TODO: More tests (probably should consume via event bus rather then rely on VertxInternal)

  @Test
  public void testHttpMetrics() throws Exception {
    String uri = "/foo/bar";
    Buffer data = randomBuffer(1000);
    int requests = 10;
    AtomicLong expected = new AtomicLong();
    CountDownLatch latch = new CountDownLatch(requests);
    HttpClient client = vertx.createHttpClient(HttpClientOptions.options());
    vertx.createHttpServer(HttpServerOptions.options().setHost("localhost").setPort(8080)).requestHandler(req -> {
      expected.incrementAndGet();
      req.response().end();
    }).listen(ar -> {
      if (ar.succeeded()) {
        for (int i = 0; i < requests; i++) {
          HttpClientRequest req = client.get(RequestOptions.options().setRequestURI(uri).setHost("localhost").setPort(8080), resp -> {
            latch.countDown();
          });
          req.writeBufferAndEnd(data);
        }
      } else {
        fail(ar.cause().getMessage());
      }
    });

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    assertEquals(requests, expected.get());
//    assertEquals(requests, vertx.metricRegistry().timer("io.vertx.http.servers.localhost:8080.requests").getCount());
//    assertEquals(requests, vertx.metricRegistry().timer("io.vertx.http.servers.localhost:8080.requests.uri." + uri).getCount());
//    assertEquals(requests, vertx.metricRegistry().timer("io.vertx.http.clients.@" + Integer.toHexString(client.hashCode()) + ".requests").getCount());

    testComplete();
  }

  @Test
  public void testNetMetrics() throws Exception {
    Buffer serverData = randomBuffer(500);
    Buffer clientData = randomBuffer(300);
    int requests = 13;
    AtomicLong expected = new AtomicLong();
    CountDownLatch latch = new CountDownLatch(requests);
    AtomicInteger actualPort = new AtomicInteger();
    AtomicReference<NetClient> clientRef = new AtomicReference<>();

    vertx.createNetServer(NetServerOptions.options().setHost("localhost")).connectHandler(socket -> {
      socket.dataHandler(buff -> {
        expected.incrementAndGet();
        socket.writeBuffer(serverData);
      });
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      actualPort.set(ar.result().actualPort());
      clientRef.set(vertx.createNetClient(NetClientOptions.options()).connect(actualPort.get(), "localhost", ar2 -> {
        assertTrue(ar2.succeeded());
        NetSocket socket = ar2.result();
        socket.dataHandler(buff -> {
          latch.countDown();
          if (latch.getCount() != 0) {
            socket.writeBuffer(clientData);
          }
        });
        socket.writeBuffer(clientData);
      }));
    });

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    assertEquals(requests, expected.get());
    String serverMetricName = "io.vertx.net.servers.localhost:" + actualPort.get();
    assertHistogram(name(serverMetricName, "bytes-read"), requests, clientData.length(), clientData.length());
    assertHistogram(name(serverMetricName, "bytes-written"), requests, serverData.length(), serverData.length());

    assertNotNull(clientRef.get());
    String clientMetricName = "io.vertx.net.clients.@" + Integer.toHexString(clientRef.get().hashCode());
    assertHistogram(name(clientMetricName, "bytes-read"), requests, serverData.length(), serverData.length());
    assertHistogram(name(clientMetricName, "bytes-written"), requests, clientData.length(), clientData.length());

    testComplete();
  }

  private void assertHistogram(String name, long count, long min, long max) {
//    Histogram histogram = vertx.metricRegistry().histogram(name);
//    assertEquals(count, histogram.getCount());
//
//    Snapshot snapshot = histogram.getSnapshot();
//    if (min != -1) {
//      assertEquals(min, snapshot.getMin());
//    }
//    if (max != -1) {
//      assertEquals(max, snapshot.getMax());
//    }
  }
}
