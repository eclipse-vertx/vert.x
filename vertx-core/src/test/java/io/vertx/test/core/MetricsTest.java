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
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class MetricsTest extends AsyncTestBase {

  private Vertx vertx; // we rely on internal Vertx API, but I think this is ok for now (for tests at least)

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    vertx = Vertx.vertx(VertxOptions.options().setMetricsEnabled(true));
  }

  @Override
  protected void tearDown() throws Exception {
    vertx.close();
  }

  //TODO: More tests

  @Test
  public void testHttpMetrics() throws Exception {
    String uri = "/foo/bar";
    Buffer serverMin = randomBuffer(500);
    Buffer serverMax = randomBuffer(1000);
    Buffer clientMax = randomBuffer(300);
    Buffer clientMin = randomBuffer(100);

    int requests = 10;
    AtomicLong expected = new AtomicLong();
    CountDownLatch latch = new CountDownLatch(requests);
    HttpClient client = vertx.createHttpClient(HttpClientOptions.options());
    vertx.createHttpServer(HttpServerOptions.options().setHost("localhost").setPort(8080)).requestHandler(req -> {
      expected.incrementAndGet();
      if (expected.get() % 2 == 0) {
        req.response().writeBufferAndEnd(serverMin);
      } else {
        req.response().writeBufferAndEnd(serverMax);
      }
    }).listen(ar -> {
      if (ar.succeeded()) {
        for (int i = 0; i < requests; i++) {
          HttpClientRequest req = client.get(RequestOptions.options().setRequestURI(uri).setHost("localhost").setPort(8080), resp -> {
            latch.countDown();
          });
          if (i % 2 == 0) {
            req.writeBufferAndEnd(clientMax);
          } else {
            req.writeBufferAndEnd(clientMin);
          }
        }
      } else {
        fail(ar.cause().getMessage());
      }
    });

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    assertEquals(requests, expected.get());


    // Gather metrics
    Map<String, JsonObject> httpMetrics = vertx.metricsProvider().getMetrics((name, metric) -> name.startsWith("io.vertx.http"));

    // Verify http server
    String baseName = "io.vertx.http.servers.localhost:8080";
    assertCount(httpMetrics, baseName + ".requests", requests); // requests
    assertCount(httpMetrics, baseName + ".requests.uri." + uri, requests); // requests.uri.{uri}
    assertMinMax(httpMetrics, baseName + ".bytes-written", (long) serverMin.length(), (long) serverMax.length());
    assertMinMax(httpMetrics, baseName + ".bytes-read", (long) clientMin.length(), (long) clientMax.length());
    assertCount(httpMetrics, baseName + ".exceptions", 0);
    expectNonNullMetric(httpMetrics, baseName + ".connections.127.0.0.1");

    // Verify http client
    baseName = "io.vertx.http.clients.@" + Integer.toHexString(client.hashCode());
    assertCount(httpMetrics, baseName + ".requests", requests); // requests
    assertMinMax(httpMetrics, baseName + ".bytes-written", (long) clientMin.length(), (long) clientMax.length());
    assertMinMax(httpMetrics, baseName + ".bytes-read", (long) serverMin.length(), (long) serverMax.length());
    assertCount(httpMetrics, baseName + ".exceptions", 0);
    expectNonNullMetric(httpMetrics, baseName + ".connections.127.0.0.1");

    testComplete();
  }

  @Test
  public void testHttpClientChunkWrites() {
    String uri = "/blah";
    int clientChunkSize = 100;
    int chunks = 10;
    long numberOfBytes = clientChunkSize * chunks;

    HttpClient client = vertx.createHttpClient(HttpClientOptions.options());
    vertx.createHttpServer(HttpServerOptions.options().setHost("localhost").setPort(8080)).requestHandler(req -> {
      req.response().end();
    }).listen(ar -> {
      if (ar.succeeded()) {
        HttpClientRequest req = client.get(RequestOptions.options().setRequestURI(uri).setHost("localhost").setPort(8080), resp -> {
          testComplete();
        });
        req.setChunked(true);

        for (int i = 0; i < chunks; i++) {
          req.writeBuffer(randomBuffer(clientChunkSize));
        }
        req.end();
      } else {
        fail(ar.cause().getMessage());
      }
    });

    await();

    // Gather metrics
    Map<String, JsonObject> httpMetrics = vertx.metricsProvider().getMetrics((name, metric) -> name.startsWith("io.vertx.http"));

    // Verify http server
    String baseName = "io.vertx.http.servers.localhost:8080";
    assertCount(httpMetrics, baseName + ".requests", 1); // requests
    assertCount(httpMetrics, baseName + ".requests.uri." + uri, 1); // requests.uri.{uri}
    assertMinMax(httpMetrics, baseName + ".bytes-written", 0L, 0L);
    assertMinMax(httpMetrics, baseName + ".bytes-read", numberOfBytes, numberOfBytes);
    assertCount(httpMetrics, baseName + ".exceptions", 0);
    expectNonNullMetric(httpMetrics, baseName + ".connections.127.0.0.1");

    // Verify http client
    baseName = "io.vertx.http.clients.@" + Integer.toHexString(client.hashCode());
    assertCount(httpMetrics, baseName + ".requests", 1); // requests
    assertMinMax(httpMetrics, baseName + ".bytes-written", numberOfBytes, numberOfBytes);
    assertMinMax(httpMetrics, baseName + ".bytes-read", 0L, 0L);
    assertCount(httpMetrics, baseName + ".exceptions", 0);
    expectNonNullMetric(httpMetrics, baseName + ".connections.127.0.0.1");
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

    Map<String, JsonObject> metrics = vertx.metricsProvider().getMetrics((name, metric) -> name.startsWith("io.vertx.net"));

    // Verify net server
    String baseName = "io.vertx.net.servers.localhost:" + actualPort.get();
    assertMinMax(metrics, baseName + ".bytes-written", (long) serverData.length(), (long) serverData.length());
    assertMinMax(metrics, baseName + ".bytes-read", (long) clientData.length(), (long) clientData.length());

    //assertHistogram(name(serverMetricName, "bytes-read"), requests, clientData.length(), clientData.length());
    //assertHistogram(name(serverMetricName, "bytes-written"), requests, serverData.length(), serverData.length());

    // Verify net client
    assertNotNull(clientRef.get());
    baseName = "io.vertx.net.clients.@" + Integer.toHexString(clientRef.get().hashCode());
    assertMinMax(metrics, baseName + ".bytes-written", (long) clientData.length(), (long) clientData.length());
    assertMinMax(metrics, baseName + ".bytes-read", (long) serverData.length(), (long) serverData.length());

    testComplete();
  }

  private void assertCount(Map<String, JsonObject> metrics, String name, Integer expected) {
    Integer actual = expectNonNullMetric(metrics, name).getInteger("count");
    assertNotNull(actual);
    assertEquals(name + " (count)", expected, actual);
  }

  private void assertMinMax(Map<String, JsonObject> metrics, String name, Long min, Long max) {
    JsonObject metric = expectNonNullMetric(metrics, name);
    if (min != null) {
      assertEquals(name + " (min)", min, metric.getLong("min"));
    }
    if (max != null) {
      assertEquals(name + " (max)", max, metric.getLong("max"));
    }
  }

  private JsonObject expectNonNullMetric(Map<String, JsonObject> metrics, String name) {
    JsonObject metric = metrics.get(name);
    assertNotNull("Expected non null metric for '" + name + "'", metric);
    return metric;
  }
}
