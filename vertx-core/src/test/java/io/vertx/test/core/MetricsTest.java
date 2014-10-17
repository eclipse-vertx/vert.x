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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.ScheduledMetricsConsumer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class MetricsTest extends AsyncTestBase {

  private Vertx vertx;
  private File testDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    vertx = Vertx.vertx(new VertxOptions().setMetricsEnabled(true));
    testDir = Files.createTempDirectory("vertx-test").toFile();
    testDir.deleteOnExit();
  }

  @Override
  protected void tearDown() throws Exception {
    vertx.close();
  }

  private void cleanup(HttpServer server, HttpClient client) throws Exception {
    if (client != null) {
      client.close();
    }
    CountDownLatch latch = new CountDownLatch(1);
    if (server != null) {
      server.close(ar -> {
        latch.countDown();
      });
    }
    latch.await(5, TimeUnit.SECONDS);
  }

  private void cleanup(NetServer server, NetClient client) throws Exception {
    if (client != null) {
      client.close();
    }
    CountDownLatch latch = new CountDownLatch(1);
    if (server != null) {
      server.close(ar -> {
        latch.countDown();
      });
    }
    latch.await(5, TimeUnit.SECONDS);
  }

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
    HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost("localhost").setPort(8080)).requestHandler(req -> {
      expected.incrementAndGet();
      if (expected.get() % 2 == 0) {
        req.response().end(serverMin);
      } else {
        req.response().end(serverMax);
      }
    }).listen(ar -> {
      if (ar.succeeded()) {
        for (int i = 0; i < requests; i++) {
          HttpClientRequest req = client.request(HttpMethod.GET, 8080, "localhost", uri, resp -> {
            latch.countDown();
          });
          if (i % 2 == 0) {
            req.end(clientMax);
          } else {
            req.end(clientMin);
          }
        }
      } else {
        fail(ar.cause().getMessage());
      }
    });

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    assertEquals(requests, expected.get());

    // Verify http server
    Map<String, JsonObject> metrics = server.metrics();
    assertCount(metrics.get("requests"), (long) requests); // requests
    assertMinMax(metrics.get("bytes-written"), (long) serverMin.length(), (long) serverMax.length());
    assertMinMax(metrics.get("bytes-read"), (long) clientMin.length(), (long) clientMax.length());
    assertCount(metrics.get("exceptions"), 0L);

    // Verify http client
    metrics = client.metrics();
    assertCount(metrics.get("requests"), (long) requests); // requests
    assertMinMax(metrics.get("bytes-written"), (long) clientMin.length(), (long) clientMax.length());
    assertMinMax(metrics.get("bytes-read"), (long) serverMin.length(), (long) serverMax.length());
    assertCount(metrics.get("exceptions"), 0L);

    cleanup(server, client);
  }

  @Test
  public void testHttpChunkWritesMetrics() throws Exception {
    String uri = "/foo";
    int chunks = 10;
    int max = 1000;
    int min = 50;
    AtomicLong serverWrittenBytes = new AtomicLong();
    AtomicLong clientWrittenBytes = new AtomicLong();
    Random random = new Random();

    HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost("localhost").setPort(8080)).requestHandler(req -> {
      req.response().setChunked(true);
      for (int i = 0; i < chunks; i++) {
        int size = random.nextInt(max - min) + min;
        serverWrittenBytes.addAndGet(size);
        req.response().write(randomBuffer(size));
      }
      req.response().end();
    }).listen(ar -> {
      if (ar.succeeded()) {
        HttpClientRequest req = client.request(HttpMethod.GET, 8080, "localhost", uri, resp -> {
          testComplete();
        });
        req.setChunked(true);

        for (int i = 0; i < chunks; i++) {
          int size = random.nextInt(max - min) + min;
          clientWrittenBytes.addAndGet(size);
          req.write(randomBuffer(size));
        }
        req.end();
      } else {
        fail(ar.cause().getMessage());
      }
    });

    await();

    // Gather metrics
    Map<String, JsonObject> metrics = server.metrics();

    // Verify http server
    assertCount(metrics.get("requests"), 1L); // requests
    assertMinMax(metrics.get("bytes-written"), serverWrittenBytes.get(), serverWrittenBytes.get());
    assertMinMax(metrics.get("bytes-read"), clientWrittenBytes.get(), clientWrittenBytes.get());
    assertCount(metrics.get("exceptions"), 0L);

    // Verify http client
    metrics = client.metrics();
    assertCount(metrics.get("requests"), 1L); // requests
    assertMinMax(metrics.get("bytes-written"), clientWrittenBytes.get(), clientWrittenBytes.get());
    assertMinMax(metrics.get("bytes-read"), serverWrittenBytes.get(), serverWrittenBytes.get());
    assertCount(metrics.get("exceptions"), 0L);

    cleanup(server, client);
  }

  @Test
  public void testHttpMethodAndUriMetrics() throws Exception {
    int requests = 4;
    CountDownLatch latch = new CountDownLatch(requests);

    HttpClient client = vertx.createHttpClient(new HttpClientOptions());

    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost("localhost").setPort(8080)).requestHandler(req -> {
      req.response().end();
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      client.request(HttpMethod.GET, 8080, "localhost", "/get", resp -> latch.countDown()).end();
      client.request(HttpMethod.POST, 8080, "localhost", "/post", resp -> latch.countDown()).end();
      client.request(HttpMethod.PUT, 8080, "localhost", "/put", resp -> latch.countDown()).end();
      client.request(HttpMethod.DELETE, 8080, "localhost", "/delete", resp -> latch.countDown()).end();
      client.request(HttpMethod.OPTIONS, 8080, "localhost", "/options", resp -> latch.countDown()).end();
      client.request(HttpMethod.HEAD, 8080, "localhost", "/head", resp -> latch.countDown()).end();
      client.request(HttpMethod.TRACE, 8080, "localhost", "/trace", resp -> latch.countDown()).end();
      client.request(HttpMethod.CONNECT, 8080, "localhost", "/connect", resp -> latch.countDown()).end();
      client.request(HttpMethod.PATCH, 8080, "localhost", "/patch", resp -> latch.countDown()).end();
    });

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    // This allows the metrics to be captured before we gather them
    vertx.setTimer(100, id -> {
      // Gather metrics
      Map<String, JsonObject> metrics = server.metrics();
      assertCount(metrics.get("get-requests"), 1L);
      assertCount(metrics.get("get-requests./get"), 1L);
      assertCount(metrics.get("post-requests"), 1L);
      assertCount(metrics.get("post-requests./post"), 1L);
      assertCount(metrics.get("put-requests"), 1L);
      assertCount(metrics.get("put-requests./put"), 1L);
      assertCount(metrics.get("delete-requests"), 1L);
      assertCount(metrics.get("delete-requests./delete"), 1L);
      assertCount(metrics.get("options-requests"), 1L);
      assertCount(metrics.get("options-requests./options"), 1L);
      assertCount(metrics.get("head-requests"), 1L);
      assertCount(metrics.get("head-requests./head"), 1L);
      assertCount(metrics.get("trace-requests"), 1L);
      assertCount(metrics.get("trace-requests./trace"), 1L);
      assertCount(metrics.get("connect-requests"), 1L);
      assertCount(metrics.get("connect-requests./connect"), 1L);
      assertCount(metrics.get("patch-requests"), 1L);
      assertCount(metrics.get("patch-requests./patch"), 1L);
      testComplete();
    });

    await();

    cleanup(server, client);
  }

  @Test
  public void testHttpMetricsOnClose() throws Exception {
    int requests = 6;
    CountDownLatch latch = new CountDownLatch(requests);

    HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost("localhost").setPort(8081)).requestHandler(req -> {
      req.response().end();
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      for (int i = 0; i < requests; i++) {
        client.request(HttpMethod.GET, 8081, "localhost", "/some/uri", resp -> {
          latch.countDown();
        }).end();
      }
    });

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    client.close();
    server.close(ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });

    await();

    Map<String, JsonObject> metrics = server.metrics();
    assertNotNull(metrics);
    assertTrue(metrics.isEmpty());

    metrics = client.metrics();
    assertNotNull(metrics);
    assertTrue(metrics.isEmpty());
  }

  @Test
  public void testHttpWebsocketMetrics() throws Exception {
    Buffer serverMin = randomBuffer(500);
    Buffer serverMax = randomBuffer(1000);
    Buffer clientMax = randomBuffer(300);
    Buffer clientMin = randomBuffer(100);

    AtomicBoolean sendMax = new AtomicBoolean(false);
    HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost("localhost").setPort(8080)).websocketHandler(socket -> {
      socket.handler(buff -> {
        if (sendMax.getAndSet(!sendMax.get())) {
          socket.write(serverMax);
        } else {
          socket.write(serverMin);
        }
      });
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      AtomicBoolean complete = new AtomicBoolean(false);
      client.connectWebsocket(8080, "localhost", "/blah", socket -> {
        socket.write(clientMax);
        socket.handler(buff -> {
          if (!complete.getAndSet(true)) {
            socket.write(clientMin);
          } else {
            testComplete();
          }
        });
      });
    });

    await();

    Map<String, JsonObject> metrics = server.metrics();
    String name = "bytes-written";
    assertCount(metrics.get(name), 2L);
    assertMinMax(metrics.get(name), (long) serverMin.length(), (long) serverMax.length());
    name = "bytes-read";
    assertCount(metrics.get(name), 2L);
    assertMinMax(metrics.get(name), (long) clientMin.length(), (long) clientMax.length());

    metrics = client.metrics();
    name = "bytes-written";
    assertCount(metrics.get(name), 2L);
    assertMinMax(metrics.get(name), (long) clientMin.length(), (long) clientMax.length());
    name = "bytes-read";
    assertCount(metrics.get(name), 2L);
    assertMinMax(metrics.get(name), (long) serverMin.length(), (long) serverMax.length());

    cleanup(server, client);
  }

  @Test
  public void testHttpSendFile() throws Exception {
    Buffer content = randomBuffer(10000);
    File file = new File(testDir, "send-file-metrics");
    file.deleteOnExit();
    Files.write(file.toPath(), content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

    HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    HttpServer server = vertx.createHttpServer(new HttpServerOptions().setHost("localhost").setPort(8080)).requestHandler(req -> {
      req.response().sendFile(file.getAbsolutePath());
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      client.request(HttpMethod.GET, 8080, "localhost", "/file", resp -> {
        resp.bodyHandler(buff -> {
          testComplete();
        });
      }).end();
    });

    await();

    Map<String, JsonObject> metrics = server.metrics();
    assertCount(metrics.get("bytes-written"), 1L);
    assertMinMax(metrics.get("bytes-written"), (long) content.length(), (long) content.length());

    metrics = client.metrics();
    assertCount(metrics.get("bytes-read"), 1L);
    assertMinMax(metrics.get("bytes-read"), (long) content.length(), (long) content.length());

    cleanup(server, client);
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

    NetServer server = vertx.createNetServer(new NetServerOptions().setHost("localhost")).connectHandler(socket -> {
      socket.handler(buff -> {
        expected.incrementAndGet();
        socket.write(serverData);
      });
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      actualPort.set(ar.result().actualPort());
      clientRef.set(vertx.createNetClient(new NetClientOptions()).connect(actualPort.get(), "localhost", ar2 -> {
        assertTrue(ar2.succeeded());
        NetSocket socket = ar2.result();
        socket.handler(buff -> {
          latch.countDown();
          if (latch.getCount() != 0) {
            socket.write(clientData);
          }
        });
        socket.write(clientData);
      }));
    });

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    assertEquals(requests, expected.get());

    // Verify net server
    Map<String, JsonObject> metrics = server.metrics();
    assertMinMax(metrics.get("bytes-written"), (long) serverData.length(), (long) serverData.length());
    assertMinMax(metrics.get("bytes-read"), (long) clientData.length(), (long) clientData.length());

    // Verify net client
    metrics = clientRef.get().metrics();
    assertMinMax(metrics.get("bytes-written"), (long) clientData.length(), (long) clientData.length());
    assertMinMax(metrics.get("bytes-read"), (long) serverData.length(), (long) serverData.length());

    cleanup(server, clientRef.get());
  }

  @Test
  public void testNetMetricsOnClose() throws Exception {
    int requests = 8;
    CountDownLatch latch = new CountDownLatch(requests);

    NetClient client = vertx.createNetClient(new NetClientOptions());
    NetServer server = vertx.createNetServer(new NetServerOptions().setHost("localhost").setPort(1235).setReceiveBufferSize(50)).connectHandler(socket -> {
      socket.handler(buff -> latch.countDown());
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1235, "localhost", ar2 -> {
        assertTrue(ar2.succeeded());
        for (int i = 0; i < requests; i++) {
          ar2.result().write(randomBuffer(50));
        }
      });
    });

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    client.close();
    server.close(ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });

    await();

    Map<String, JsonObject> metrics = server.metrics();
    assertNotNull(metrics);
    assertTrue(metrics.isEmpty());

    metrics = client.metrics();
    assertNotNull(metrics);
    assertTrue(metrics.isEmpty());

    cleanup(server, client);
  }

  @Test
  public void testDatagramMetrics() throws Exception {
    Buffer clientMax = randomBuffer(1823);
    Buffer clientMin = randomBuffer(123);

    AtomicBoolean complete = new AtomicBoolean(false);
    DatagramSocket datagramSocket = vertx.createDatagramSocket(new DatagramSocketOptions()).listen(1236, "localhost", ar -> {
      assertTrue(ar.succeeded());
      DatagramSocket socket = ar.result();
      socket.packetHandler(packet -> {
        if (complete.getAndSet(true)) {
          testComplete();
        }
      });
      socket.send(clientMin, 1236, "localhost", ds -> {
        assertTrue(ar.succeeded());
      });
      socket.send(clientMax, 1236, "localhost", ds -> {
        assertTrue(ar.succeeded());
      });
    });

    await();

    // Test sender/client (bytes-written)
    Map<String, JsonObject> metrics = datagramSocket.metrics();
    assertCount(metrics.get("bytes-written"), 2L);
    assertMinMax(metrics.get("bytes-written"), (long) clientMin.length(), (long) clientMax.length());

    // Test server (bytes-read)
    assertCount(metrics.get("127.0.0.1:1236.bytes-read"), 2L);
    assertMinMax(metrics.get("127.0.0.1:1236.bytes-read"), (long) clientMin.length(), (long) clientMax.length());

    CountDownLatch latch = new CountDownLatch(1);
    datagramSocket.close(ar -> {
      assertTrue(ar.succeeded());
      assertTrue(datagramSocket.metrics().isEmpty());
      latch.countDown();
    });

    latch.await(5, TimeUnit.SECONDS);
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

    Map<String, JsonObject> metrics = vertx.eventBus().metrics();
    assertCount(metrics.get("messages.sent"), send);
    assertCount(metrics.get("messages.published"), pub);
    assertCount(metrics.get("messages.received"), 0L);
  }

  @Test
  public void testEventBusMetricsWithHandler() {
    int messages = 13;
    AtomicInteger count = new AtomicInteger(messages);

    vertx.eventBus().consumer("foo").handler(msg -> {
      if (count.decrementAndGet() == 0) testComplete();
    });

    for (int i = 0; i < messages; i++) {
      vertx.eventBus().send("foo", "Hello");
    }

    await();

    Map<String, JsonObject> metrics = vertx.eventBus().metrics();
    assertCount(metrics.get("messages.sent"), (long) messages);
    assertCount(metrics.get("messages.received"), (long) messages);
  }

  @Test
  public void testEventBusMetricsReplyNoHandlers() {
    vertx.eventBus().send("foo", "bar", new DeliveryOptions().setSendTimeout(300), ar -> {
      assertTrue(ar.failed());
      testComplete();
    });

    await();

    Map<String, JsonObject> metrics = vertx.eventBus().metrics();
    assertCount(metrics.get("messages.reply-failures"), 1L);
    assertCount(metrics.get("messages.reply-failures." + ReplyFailure.NO_HANDLERS), 1L);
  }

  @Test
  public void testEventBusMetricsReplyTimeout() {
    vertx.eventBus().consumer("foo").handler(msg -> {});

    vertx.eventBus().send("foo", "bar", new DeliveryOptions().setSendTimeout(300), ar -> {
      assertTrue(ar.failed());
      testComplete();
    });

    await();

    Map<String, JsonObject> metrics = vertx.eventBus().metrics();
    assertCount(metrics.get("messages.reply-failures"), 1L);
    assertCount(metrics.get("messages.reply-failures." + ReplyFailure.TIMEOUT), 1L);
  }

  @Test
  public void testEventBusMetricsReplyRecipientFailure() {
    vertx.eventBus().consumer("foo").handler(msg -> msg.fail(1, "blah"));

    vertx.eventBus().send("foo", "bar", new DeliveryOptions().setSendTimeout(300), ar -> {
      assertTrue(ar.failed());
      testComplete();
    });

    await();

    Map<String, JsonObject> metrics = vertx.eventBus().metrics();
    assertCount(metrics.get("messages.reply-failures"), 1L);
    assertCount(metrics.get("messages.reply-failures." + ReplyFailure.RECIPIENT_FAILURE), 1L);
  }

  @Test
  public void testVertxMetrics() throws Exception {
    Map<String, JsonObject> metrics = vertx.metrics();
    assertNotNull(metrics.get("vertx.event-loop-size"));
    assertNotNull(metrics.get("vertx.worker-pool-size"));
    assertNull(metrics.get("vertx.cluster-host"));
    assertNull(metrics.get("vertx.cluster-port"));
  }

  @Test
  public void testVerticleMetrics() throws Exception {
    int verticles = 5;
    CountDownLatch latch = new CountDownLatch(verticles);
    AtomicReference<String> ref = new AtomicReference<>();
    for (int i = 0; i < 5; i++) {
      vertx.deployVerticle(new AbstractVerticle() {}, ar -> {
        assertTrue(ar.succeeded());
        ref.set(ar.result()); // just use the last deployment id to test undeploy metrics below
        latch.countDown();
      });
    }

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    Map<String, JsonObject> metrics = vertx.metrics();
    assertNotNull(metrics);
    assertFalse(metrics.isEmpty());

    assertCount(metrics.get("vertx.verticles"), (long) verticles);

    vertx.undeployVerticle(ref.get(), ar -> {
      assertTrue(ar.succeeded());
      assertCount(vertx.metrics().get("vertx.verticles"), (long) verticles - 1);
      testComplete();
    });

    await();
  }

  @Test
  public void testTimerMetrics() throws Exception {
    // Timer
    CountDownLatch latch = new CountDownLatch(1);
    vertx.setTimer(300, id -> {
      assertCount(vertx.metrics().get("vertx.timers"), 1L);
      latch.countDown();
    });
    assertTrue(latch.await(3, TimeUnit.SECONDS));
    assertCount(vertx.metrics().get("vertx.timers"), 0L);

    // Periodic
    AtomicInteger count = new AtomicInteger(3);
    vertx.setPeriodic(100, id -> {
      assertCount(vertx.metrics().get("vertx.timers"), 1L);
      if (count.decrementAndGet() == 0) {
        vertx.cancelTimer(id);
        testComplete();
      }
    });

    await();

    assertCount(vertx.metrics().get("vertx.timers"), 0L);
  }

  @Test
  public void testScheduledMetricConsumer() {
    int messages = 18;
    AtomicInteger count = new AtomicInteger(messages);
    String baseName = vertx.eventBus().metricBaseName();

    ScheduledMetricsConsumer consumer = new ScheduledMetricsConsumer(vertx).filter((name, metric) -> {
      return name.startsWith(baseName);
    });

    consumer.start(300, TimeUnit.MILLISECONDS, (name, metric) -> {
      assertTrue(name.startsWith(baseName));
      if (count.get() == 0) {
        if (name.equals(baseName + ".messages.sent")) {
          assertCount(metric, (long) messages);
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

  private void assertCount(JsonObject metric, Long expected) {
    assertNotNull(metric);
    Long actual = metric.getLong("count");
    String name = metric.getString("name");
    assertNotNull(actual);
    assertEquals(name + " (count)", expected, actual);
  }

  private void assertMinMax(JsonObject metric, Long min, Long max) {
    assertNotNull(metric);
    String name = metric.getString("name");
    if (min != null) {
      assertEquals(name + " (min)", min, metric.getLong("min"));
    }
    if (max != null) {
      assertEquals(name + " (max)", max, metric.getLong("max"));
    }
  }
}
