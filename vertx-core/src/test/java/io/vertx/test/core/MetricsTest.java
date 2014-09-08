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
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.ScheduledMetricsConsumer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import org.junit.Test;

import java.util.Map;
import java.util.Random;
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
    assertCount(httpMetrics, baseName + ".requests", (long) requests); // requests
    assertMinMax(httpMetrics, baseName + ".bytes-written", (long) serverMin.length(), (long) serverMax.length());
    assertMinMax(httpMetrics, baseName + ".bytes-read", (long) clientMin.length(), (long) clientMax.length());
    assertCount(httpMetrics, baseName + ".exceptions", 0L);
    expectNonNullMetric(httpMetrics, baseName + ".connections.127.0.0.1");

    // Verify http client
    baseName = "io.vertx.http.clients.@" + Integer.toHexString(client.hashCode());
    assertCount(httpMetrics, baseName + ".requests", (long) requests); // requests
    assertMinMax(httpMetrics, baseName + ".bytes-written", (long) clientMin.length(), (long) clientMax.length());
    assertMinMax(httpMetrics, baseName + ".bytes-read", (long) serverMin.length(), (long) serverMax.length());
    assertCount(httpMetrics, baseName + ".exceptions", 0L);
    expectNonNullMetric(httpMetrics, baseName + ".connections.127.0.0.1");

    testComplete();
  }

  @Test
  public void testHttpChunkWritesMetrics() {
    String uri = "/foo";
    int chunks = 10;
    int max = 1000;
    int min = 50;
    AtomicLong serverWrittenBytes = new AtomicLong();
    AtomicLong clientWrittenBytes = new AtomicLong();
    Random random = new Random();

    HttpClient client = vertx.createHttpClient(HttpClientOptions.options());
    vertx.createHttpServer(HttpServerOptions.options().setHost("localhost").setPort(8080)).requestHandler(req -> {
      req.response().setChunked(true);
      for (int i = 0; i < chunks; i++) {
        int size = random.nextInt(max - min) + min;
        serverWrittenBytes.addAndGet(size);
        req.response().writeBuffer(randomBuffer(size));
      }
      req.response().end();
    }).listen(ar -> {
      if (ar.succeeded()) {
        HttpClientRequest req = client.get(RequestOptions.options().setRequestURI(uri).setHost("localhost").setPort(8080), resp -> {
          testComplete();
        });
        req.setChunked(true);

        for (int i = 0; i < chunks; i++) {
          int size = random.nextInt(max - min) + min;
          clientWrittenBytes.addAndGet(size);
          req.writeBuffer(randomBuffer(size));
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
    assertCount(httpMetrics, baseName + ".requests", 1L); // requests
    assertMinMax(httpMetrics, baseName + ".bytes-written", serverWrittenBytes.get(), serverWrittenBytes.get());
    assertMinMax(httpMetrics, baseName + ".bytes-read", clientWrittenBytes.get(), clientWrittenBytes.get());
    assertCount(httpMetrics, baseName + ".exceptions", 0L);
    expectNonNullMetric(httpMetrics, baseName + ".connections.127.0.0.1");

    // Verify http client
    baseName = "io.vertx.http.clients.@" + Integer.toHexString(client.hashCode());
    assertCount(httpMetrics, baseName + ".requests", 1L); // requests
    assertMinMax(httpMetrics, baseName + ".bytes-written", clientWrittenBytes.get(), clientWrittenBytes.get());
    assertMinMax(httpMetrics, baseName + ".bytes-read", serverWrittenBytes.get(), serverWrittenBytes.get());
    assertCount(httpMetrics, baseName + ".exceptions", 0L);
    expectNonNullMetric(httpMetrics, baseName + ".connections.127.0.0.1");
  }

  @Test
  public void testHttpMethodAndUriMetrics() throws Exception {
    int requests = 4;
    CountDownLatch latch = new CountDownLatch(requests);

    vertx.createHttpServer(HttpServerOptions.options().setHost("localhost").setPort(8080)).requestHandler(req -> {
      req.response().end();
    }).listen();

    RequestOptions requestOptions = RequestOptions.options().setHost("localhost").setPort(8080);
    HttpClient client = vertx.createHttpClient(HttpClientOptions.options());
    client.get(requestOptions.setRequestURI("/get"), resp -> latch.countDown()).end();
    client.post(requestOptions.setRequestURI("/post"), resp -> latch.countDown()).end();
    client.put(requestOptions.setRequestURI("/put"), resp -> latch.countDown()).end();
    client.delete(requestOptions.setRequestURI("/delete"), resp -> latch.countDown()).end();
    // We don't have to test all http methods...

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    // Gather metrics
    Map<String, JsonObject> httpMetrics = vertx.metricsProvider().getMetrics((name, metric) -> name.startsWith("io.vertx.http"));

    String baseName = "io.vertx.http.servers.localhost:8080";
    assertCount(httpMetrics, baseName + ".get-requests", 1L);
    assertCount(httpMetrics, baseName + ".get-requests./get", 1L);
    assertCount(httpMetrics, baseName + ".post-requests", 1L);
    assertCount(httpMetrics, baseName + ".post-requests./post", 1L);
    assertCount(httpMetrics, baseName + ".put-requests", 1L);
    assertCount(httpMetrics, baseName + ".put-requests./put", 1L);
    assertCount(httpMetrics, baseName + ".delete-requests", 1L);
    assertCount(httpMetrics, baseName + ".delete-requests./delete", 1L);
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

    // Verify net client
    assertNotNull(clientRef.get());
    baseName = "io.vertx.net.clients.@" + Integer.toHexString(clientRef.get().hashCode());
    assertMinMax(metrics, baseName + ".bytes-written", (long) clientData.length(), (long) clientData.length());
    assertMinMax(metrics, baseName + ".bytes-read", (long) serverData.length(), (long) serverData.length());

    testComplete();
  }

  @Test
  public void testEventBusMetricsWithoutHandler() {
    long send = 12;
    for (int i = 0; i < send; i++) {
      vertx.eventBus().send("foo", "Hello");
    }
    long pub = 7;
    for (int i = 0; i < pub; i++) {
      vertx.eventBus().publish("foo", "Hello");
    }

    Map<String, JsonObject> metrics = vertx.metricsProvider().getMetrics((name, metric) -> name.startsWith("io.vertx.eventbus"));
    assertCount(metrics, "io.vertx.eventbus.messages.sent",send);
    assertCount(metrics, "io.vertx.eventbus.messages.published", pub);
    assertCount(metrics, "io.vertx.eventbus.messages.received", 0L);
  }

  @Test
  public void testEventBusMetricsWithHandler() {
    int messages = 13;
    AtomicInteger count = new AtomicInteger(messages);

    vertx.eventBus().registerHandler("foo", msg -> {
      if (count.decrementAndGet() == 0) testComplete();
    });

    for (int i = 0; i < messages; i++) {
      vertx.eventBus().send("foo", "Hello");
    }

    await();

    Map<String, JsonObject> metrics = vertx.metricsProvider().getMetrics((name, metric) -> name.startsWith("io.vertx.eventbus"));
    assertCount(metrics, "io.vertx.eventbus.messages.sent", (long) messages);
    assertCount(metrics, "io.vertx.eventbus.messages.received", (long) messages);
  }

  @Test
  public void testEventBusMetricsReplyNoHandlers() {
    vertx.eventBus().sendWithOptions("foo", "bar", DeliveryOptions.options().setSendTimeout(300), ar -> {
      assertTrue(ar.failed());
      testComplete();
    });

    await();

    Map<String, JsonObject> metrics = vertx.metricsProvider().getMetrics((name, metric) -> name.startsWith("io.vertx.eventbus"));
    assertCount(metrics, "io.vertx.eventbus.messages.reply-failures", 1L);
    assertCount(metrics, "io.vertx.eventbus.messages.reply-failures." + ReplyFailure.NO_HANDLERS, 1L);
  }

  @Test
  public void testEventBusMetricsReplyTimeout() {
    vertx.eventBus().registerHandler("foo", msg -> {});

    vertx.eventBus().sendWithOptions("foo", "bar", DeliveryOptions.options().setSendTimeout(300), ar -> {
      assertTrue(ar.failed());
      testComplete();
    });

    await();

    Map<String, JsonObject> metrics = vertx.metricsProvider().getMetrics((name, metric) -> name.startsWith("io.vertx.eventbus"));
    assertCount(metrics, "io.vertx.eventbus.messages.reply-failures", 1L);
    assertCount(metrics, "io.vertx.eventbus.messages.reply-failures." + ReplyFailure.TIMEOUT, 1L);
  }

  @Test
  public void testEventBusMetricsReplyRecipientFailure() {
    vertx.eventBus().registerHandler("foo", msg -> msg.fail(1, "blah"));

    vertx.eventBus().sendWithOptions("foo", "bar", DeliveryOptions.options().setSendTimeout(300), ar -> {
      assertTrue(ar.failed());
      testComplete();
    });

    await();

    Map<String, JsonObject> metrics = vertx.metricsProvider().getMetrics((name, metric) -> name.startsWith("io.vertx.eventbus"));
    assertCount(metrics, "io.vertx.eventbus.messages.reply-failures", 1L);
    assertCount(metrics, "io.vertx.eventbus.messages.reply-failures." + ReplyFailure.RECIPIENT_FAILURE, 1L);
  }



  @Test
  public void testScheduledMetricConsumer() {
    int messages = 18;
    AtomicInteger count = new AtomicInteger(messages);
    String baseName = "io.vertx.eventbus";

    ScheduledMetricsConsumer consumer = new ScheduledMetricsConsumer(vertx).filter((name, metric) -> {
      return name.startsWith(baseName);
    });

    consumer.start(300, TimeUnit.MILLISECONDS, (name, metric) -> {
      assertTrue(name.startsWith(baseName));
      if (count.get() == 0) {
        if (name.equals(baseName + ".messages.sent")) {
          assertCount(metric, name, (long) messages);
          testComplete();
        }
      }
    });

    for (int i = 0; i < messages; i++) {
      vertx.eventBus().send("foo", "Hello");
      count.decrementAndGet();
    }

    await();
  }

  private void assertCount(Map<String, JsonObject> metrics, String name, Long expected) {
    assertCount(expectNonNullMetric(metrics, name), name, expected);
  }

  private void assertCount(JsonObject metric, String name, Long expected) {
    Long actual = metric.getLong("count");
    assertNotNull(actual);
    assertEquals(name + " (count)", expected, actual);
  }

  private void assertMinMax(Map<String, JsonObject> metrics, String name, Long min, Long max) {
    assertMinMax(expectNonNullMetric(metrics, name), name, min, max);
  }

  private void assertMinMax(JsonObject metric, String name, Long min, Long max) {
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
