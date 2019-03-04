/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.metrics;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SingleThreadEventLoop;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.http.*;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakemetrics.*;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.core.Is.is;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MetricsTest extends VertxTestBase {

  private static final String ADDRESS1 = "some-address1";

  private HttpServer server;
  private HttpClient client;

  protected void tearDown() throws Exception {
    if (client != null) {
      try {
        client.close();
      } catch (IllegalStateException ignore) {
        // Client was already closed by the test
      }
    }
    if (server != null) {
      CountDownLatch latch = new CountDownLatch(1);
      server.close((asyncResult) -> {
        assertTrue(asyncResult.succeeded());
        latch.countDown();
      });
      awaitLatch(latch);
    }
    super.tearDown();
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(new FakeMetricsFactory()));
    return options;
  }

  @Test
  public void testSendMessage() {
    testBroadcastMessage(vertx, new Vertx[]{vertx}, false, new SentMessage(ADDRESS1, false, true, false));
  }

  @Test
  public void testSendMessageInCluster() {
    startNodes(2);
    testBroadcastMessage(vertices[0], new Vertx[]{vertices[1]}, false, new SentMessage(ADDRESS1, false, false, true));
  }

  @Test
  public void testPublishMessageToSelf() {
    testBroadcastMessage(vertx, new Vertx[]{vertx}, true, new SentMessage(ADDRESS1, true, true, false));
  }

  @Test
  public void testPublishMessageToRemote() {
    startNodes(2);
    testBroadcastMessage(vertices[0], new Vertx[]{vertices[1]}, true, new SentMessage(ADDRESS1, true, false, true));
  }

  @Test
  public void testPublishMessageToCluster() {
    startNodes(2);
    testBroadcastMessage(vertices[0], vertices, true, new SentMessage(ADDRESS1, true, false, true), new SentMessage(ADDRESS1, true, true, false));
  }

  private void testBroadcastMessage(Vertx from, Vertx[] to, boolean publish, SentMessage... expected) {
    FakeEventBusMetrics eventBusMetrics = FakeMetricsBase.getMetrics(from.eventBus());
    AtomicInteger broadcastCount = new AtomicInteger();
    AtomicInteger receiveCount = new AtomicInteger();
    for (Vertx vertx : to) {
      MessageConsumer<Object> consumer = vertx.eventBus().consumer(ADDRESS1);
      consumer.completionHandler(done -> {
        assertTrue(done.succeeded());
        if (broadcastCount.incrementAndGet() == to.length) {
          String msg = TestUtils.randomAlphaString(10);
          if (publish) {
            from.eventBus().publish(ADDRESS1, msg);
          } else {
            from.eventBus().send(ADDRESS1, msg);
          }
        }
      });
      consumer.handler(msg -> {
        if (receiveCount.incrementAndGet() == to.length) {
          assertEquals(new HashSet<>(Arrays.asList(expected)), new HashSet<>(eventBusMetrics.getSentMessages()));
          testComplete();
        }
      });
    }
    await();
  }

  @Test
  public void testReceiveSentMessageFromSelf() {
    testReceiveMessageSent(vertx, vertx, true, 1);
  }

  @Test
  public void testReceiveMessageSentFromRemote() {
    startNodes(2);
    testReceiveMessageSent(vertices[0], vertices[1], false, 1);
  }

  private void testReceiveMessageSent(Vertx from, Vertx to, boolean expectedLocal, int expectedHandlers) {
    FakeEventBusMetrics eventBusMetrics = FakeMetricsBase.getMetrics(to.eventBus());
    MessageConsumer<Object> consumer = to.eventBus().consumer(ADDRESS1);
    consumer.completionHandler(done -> {
      assertTrue(done.succeeded());
      String msg = TestUtils.randomAlphaString(10);
      from.eventBus().send(ADDRESS1, msg);
    });
    consumer.handler(msg -> {
      assertEquals(Arrays.asList(new ReceivedMessage(ADDRESS1, false, expectedLocal, expectedHandlers)), eventBusMetrics.getReceivedMessages());
      testComplete();
    });
    await();
  }

  @Test
  public void testReceivePublishedMessageFromSelf() {
    testReceiveMessagePublished(vertx, vertx, true, 3);
  }

  @Test
  public void testReceiveMessagePublishedFromRemote() {
    startNodes(2);
    testReceiveMessagePublished(vertices[0], vertices[1], false, 3);
  }

  private void testReceiveMessagePublished(Vertx from, Vertx to, boolean expectedLocal, int expectedHandlers) {
    FakeEventBusMetrics eventBusMetrics = FakeMetricsBase.getMetrics(to.eventBus());
    AtomicInteger count = new AtomicInteger();
    for (int i = 0; i < expectedHandlers; i++) {
      MessageConsumer<Object> consumer = to.eventBus().consumer(ADDRESS1);
      consumer.completionHandler(done -> {
        assertTrue(done.succeeded());
        if (count.incrementAndGet() == expectedHandlers) {
          String msg = TestUtils.randomAlphaString(10);
          from.eventBus().publish(ADDRESS1, msg);
        }
      });
      int index = i;
      consumer.handler(msg -> {
        if (index == 0) {
          assertEquals(Arrays.asList(new ReceivedMessage(ADDRESS1, true, expectedLocal, expectedHandlers)), eventBusMetrics.getReceivedMessages());
          testComplete();
        }
      });
    }
    await();
  }

  @Test
  public void testReplyMessageFromSelf() {
    testReply(vertx, vertx, true, false);
  }

  @Test
  public void testReplyMessageFromRemote() {
    startNodes(2);
    testReply(vertices[0], vertices[1], false, true);
  }

  private void testReply(Vertx from, Vertx to, boolean expectedLocal, boolean expectedRemote) {
    FakeEventBusMetrics fromMetrics = FakeMetricsBase.getMetrics(from.eventBus());
    FakeEventBusMetrics toMetrics = FakeMetricsBase.getMetrics(to.eventBus());
    MessageConsumer<Object> consumer = to.eventBus().consumer(ADDRESS1);
    consumer.completionHandler(done -> {
      assertTrue(done.succeeded());
      String msg = TestUtils.randomAlphaString(10);
      from.eventBus().send(ADDRESS1, msg, reply -> {
        assertEquals(1, fromMetrics.getReceivedMessages().size());
        ReceivedMessage receivedMessage = fromMetrics.getReceivedMessages().get(0);
        assertEquals(false, receivedMessage.publish);
        assertEquals(expectedLocal, receivedMessage.local);
        assertEquals(1, receivedMessage.handlers);
        assertEquals(1, toMetrics.getSentMessages().size());
        SentMessage sentMessage = toMetrics.getSentMessages().get(0);
        assertEquals(false, sentMessage.publish);
        assertEquals(expectedLocal, sentMessage.local);
        assertEquals(expectedRemote, sentMessage.remote);
        assertEquals(sentMessage.address, receivedMessage.address);
        testComplete();
      });
    });
    consumer.handler(msg -> {
      toMetrics.getReceivedMessages().clear();
      toMetrics.getSentMessages().clear();
      msg.reply(TestUtils.randomAlphaString(10));
    });
    await();
  }

  @Test
  public void testHandlerRegistration() throws Exception {
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(vertx.eventBus());
    MessageConsumer<Object> consumer = vertx.eventBus().consumer(ADDRESS1, msg -> {
    });
    CountDownLatch latch = new CountDownLatch(1);
    consumer.completionHandler(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    assertEquals(1, metrics.getRegistrations().size());
    HandlerMetric registration = metrics.getRegistrations().get(0);
    assertEquals(ADDRESS1, registration.address);
    assertEquals(null, registration.repliedAddress);
    consumer.unregister(ar -> {
      assertTrue(ar.succeeded());
      assertEquals(0, metrics.getRegistrations().size());
      testComplete();
    });
    await();
  }

  @Test
  public void testHandlerProcessMessage() {
    testHandlerProcessMessage(vertx, vertx, 1);
  }

  @Test
  public void testHandlerProcessMessageFromRemote() {
    startNodes(2);
    testHandlerProcessMessage(vertices[0], vertices[1], 0);
  }

  private HandlerMetric assertRegistration(FakeEventBusMetrics metrics) {
    Optional<HandlerMetric> registration = metrics.getRegistrations().stream().filter(reg -> reg.address.equals(ADDRESS1)).findFirst();
    assertTrue(registration.isPresent());
    return registration.get();
  }

  private void testHandlerProcessMessage(Vertx from, Vertx to, int expectedLocalCount) {
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(to.eventBus());
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    to.runOnContext(v -> {
      to.eventBus().consumer(ADDRESS1, msg -> {
        HandlerMetric registration = assertRegistration(metrics);
        assertEquals(ADDRESS1, registration.address);
        assertEquals(null, registration.repliedAddress);
        assertEquals(1, registration.scheduleCount.get());
        assertEquals(expectedLocalCount, registration.localScheduleCount.get());
        assertEquals(1, registration.beginCount.get());
        assertEquals(0, registration.endCount.get());
        assertEquals(0, registration.failureCount.get());
        assertEquals(expectedLocalCount, registration.localBeginCount.get());
        msg.reply("pong");
      }).completionHandler(onSuccess(v2 -> {
        to.runOnContext(v3 -> {
          latch1.countDown();
          try {
            awaitLatch(latch2);
          } catch (InterruptedException e) {
            fail(e);
          }
        });
      }));
    });
    try {
      awaitLatch(latch1);
    } catch (InterruptedException e) {
      fail(e);
      return;
    }
    HandlerMetric registration = assertRegistration(metrics);
    assertEquals(ADDRESS1, registration.address);
    assertEquals(null, registration.repliedAddress);
    from.eventBus().send(ADDRESS1, "ping", reply -> {
      assertEquals(1, registration.scheduleCount.get());
      assertEquals(1, registration.beginCount.get());
      // This might take a little time
      assertWaitUntil(() -> 1 == registration.endCount.get());
      assertEquals(0, registration.failureCount.get());
      assertEquals(expectedLocalCount, registration.localBeginCount.get());
      testComplete();
    });
    assertWaitUntil(() -> registration.scheduleCount.get() == 1);
    assertEquals(0, registration.beginCount.get());
    latch2.countDown();
    await();
  }

  @Test
  public void testHandlerProcessMessageFailure() throws Exception {
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(vertx.eventBus());
    MessageConsumer<Object> consumer = vertx.eventBus().consumer(ADDRESS1, msg -> {
      assertEquals(1, metrics.getReceivedMessages().size());
      HandlerMetric registration = metrics.getRegistrations().get(0);
      assertEquals(1, registration.scheduleCount.get());
      assertEquals(1, registration.beginCount.get());
      assertEquals(0, registration.endCount.get());
      assertEquals(0, registration.failureCount.get());
      throw new RuntimeException();
    });
    CountDownLatch latch = new CountDownLatch(1);
    consumer.completionHandler(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    vertx.eventBus().send(ADDRESS1, "ping");
    assertEquals(1, metrics.getReceivedMessages().size());
    HandlerMetric registration = metrics.getRegistrations().get(0);
    long now = System.currentTimeMillis();
    while (registration.failureCount.get() < 1 && (System.currentTimeMillis() - now) < 10 * 1000) {
      Thread.sleep(10);
    }
    assertEquals(1, registration.scheduleCount.get());
    assertEquals(1, registration.beginCount.get());
    assertEquals(1, registration.endCount.get());
    assertEquals(1, registration.failureCount.get());
  }

  @Test
  public void testHandlerMetricReply() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(vertx.eventBus());
    vertx.eventBus().consumer(ADDRESS1, msg -> {
      assertEquals(ADDRESS1, metrics.getRegistrations().get(0).address);
      assertWaitUntil(() -> metrics.getRegistrations().size() == 2);
      HandlerMetric registration = metrics.getRegistrations().get(1);
      assertEquals(ADDRESS1, registration.repliedAddress);
      assertEquals(0, registration.scheduleCount.get());
      assertEquals(0, registration.beginCount.get());
      assertEquals(0, registration.endCount.get());
      assertEquals(0, registration.localBeginCount.get());
      msg.reply("pong");
    }).completionHandler(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    vertx.eventBus().send(ADDRESS1, "ping", reply -> {
      assertEquals(ADDRESS1, metrics.getRegistrations().get(0).address);
      HandlerMetric registration = metrics.getRegistrations().get(1);
      assertEquals(ADDRESS1, registration.repliedAddress);
      assertEquals(1, registration.scheduleCount.get());
      assertEquals(1, registration.beginCount.get());
      assertEquals(0, registration.endCount.get());
      assertEquals(1, registration.localBeginCount.get());
      vertx.runOnContext(v -> {
        assertEquals(ADDRESS1, metrics.getRegistrations().get(0).address);
        assertEquals(ADDRESS1, registration.repliedAddress);
        assertEquals(1, registration.scheduleCount.get());
        assertEquals(1, registration.beginCount.get());
        assertEquals(1, registration.endCount.get());
        assertEquals(1, registration.localBeginCount.get());
      });
      testComplete();
    });
    await();
  }

  @Test
  public void testBytesCodec() throws Exception {
    startNodes(2);
    FakeEventBusMetrics fromMetrics = FakeMetricsBase.getMetrics(vertices[0].eventBus());
    FakeEventBusMetrics toMetrics = FakeMetricsBase.getMetrics(vertices[1].eventBus());
    vertices[1].eventBus().consumer(ADDRESS1, msg -> {
      int encoded = fromMetrics.getEncodedBytes(ADDRESS1);
      int decoded = toMetrics.getDecodedBytes(ADDRESS1);
      assertTrue("Expected to have more " + encoded + " > 1000 encoded bytes", encoded > 1000);
      assertTrue("Expected to have more " + decoded + " > 1000 decoded bytes", decoded > 1000);
      testComplete();
    }).completionHandler(ar -> {
      assertTrue(ar.succeeded());
      assertEquals(0, fromMetrics.getEncodedBytes(ADDRESS1));
      assertEquals(0, toMetrics.getDecodedBytes(ADDRESS1));
      vertices[0].eventBus().send(ADDRESS1, Buffer.buffer(new byte[1000]));
    });
    await();
  }

  @Test
  public void testReplyFailureNoHandlers() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    EventBus eb = vertx.eventBus();
    eb.send(ADDRESS1, "bar", new DeliveryOptions().setSendTimeout(10), ar -> {
      assertTrue(ar.failed());
      latch.countDown();
    });
    awaitLatch(latch);
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(eb);
    assertEquals(Collections.singletonList("__vertx.reply.1"), metrics.getReplyFailureAddresses());
    assertEquals(Collections.singletonList(ReplyFailure.NO_HANDLERS), metrics.getReplyFailures());
  }

  @Test
  public void testReplyFailureTimeout1() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    EventBus eb = vertx.eventBus();
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(eb);
    eb.consumer(ADDRESS1, msg -> {
      // Do not reply
    });
    eb.send(ADDRESS1, "bar", new DeliveryOptions().setSendTimeout(10), ar -> {
      assertTrue(ar.failed());
      latch.countDown();
    });
    awaitLatch(latch);
    assertEquals(1, metrics.getReplyFailureAddresses().size());
    assertEquals(Collections.singletonList(ReplyFailure.TIMEOUT), metrics.getReplyFailures());
  }

  @Test
  public void testReplyFailureTimeout2() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    EventBus eb = vertx.eventBus();
    eb.consumer(ADDRESS1, msg -> {
      msg.reply("juu", new DeliveryOptions().setSendTimeout(10), ar -> {
        assertTrue(ar.failed());
        latch.countDown();
      });
    });
    eb.send(ADDRESS1, "bar", ar -> {
      // Do not reply
    });
    awaitLatch(latch);
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(eb);
    assertEquals(1, metrics.getReplyFailureAddresses().size());
    assertEquals(Collections.singletonList(ReplyFailure.TIMEOUT), metrics.getReplyFailures());
  }

  @Test
  public void testReplyFailureRecipientFailure() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    EventBus eb = vertx.eventBus();
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(eb);
    AtomicReference<String> replyAddress = new AtomicReference<>();
    CountDownLatch regLatch = new CountDownLatch(1);
    eb.consumer("foo", msg -> {
      replyAddress.set(msg.replyAddress());
      msg.fail(0, "whatever");
    }).completionHandler(onSuccess(v -> {
      regLatch.countDown();
    }));
    awaitLatch(regLatch);
    eb.send("foo", "bar", new DeliveryOptions(), ar -> {
      assertTrue(ar.failed());
      latch.countDown();
    });
    awaitLatch(latch);
    assertEquals(Collections.singletonList(replyAddress.get()), metrics.getReplyFailureAddresses());
    assertEquals(Collections.singletonList(ReplyFailure.RECIPIENT_FAILURE), metrics.getReplyFailures());
  }

  @Test
  public void testServerWebSocket() {
    server = vertx.createHttpServer();
    server.websocketHandler(ws -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      WebSocketMetric metric = metrics.getMetric(ws);
      assertNotNull(metric);
      assertNotNull(metric.soMetric);
      ws.handler(ws::write);
      ws.closeHandler(closed -> {
        assertNull(metrics.getMetric(ws));
        testComplete();
      });
    });
    server.listen(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, ar -> {
      assertTrue(ar.succeeded());
      client = vertx.createHttpClient();
      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        ws.write(Buffer.buffer("wibble"));
        ws.handler(buff -> ws.close());
      });
    });
    await();
  }

  @Test
  public void testServerWebSocketUpgrade() {
    server = vertx.createHttpServer();
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      assertNotNull(metrics.getMetric(req));
      ServerWebSocket ws = req.upgrade();
      assertNull(metrics.getMetric(req));
      WebSocketMetric metric = metrics.getMetric(ws);
      assertNotNull(metric);
      assertNotNull(metric.soMetric);
      ws.handler(buffer -> ws.write(buffer));
      ws.closeHandler(closed -> {
        WebSocketMetric a = metrics.getMetric(ws);
        assertNull(a);
        testComplete();
      });
    });
    server.listen(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, ar -> {
      assertTrue(ar.succeeded());
      client = vertx.createHttpClient();
      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        ws.write(Buffer.buffer("wibble"));
        ws.handler(buff -> {
          ws.close();
        });
      });
    });
    await();
  }

  @Test
  public void testWebSocket() {
    server = vertx.createHttpServer();
    server.websocketHandler(ws -> {
      ws.write(Buffer.buffer("wibble"));
      ws.handler(buff -> ws.close());
    });
    server.listen(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, ar -> {
      assertTrue(ar.succeeded());
      client = vertx.createHttpClient();
      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(client);
        WebSocketMetric metric = metrics.getMetric(ws);
        assertNotNull(metric);
        assertNotNull(metric.soMetric);
        ws.closeHandler(closed -> {
          assertNull(metrics.getMetric(ws));
          testComplete();
        });
        ws.handler(ws::write);
      });
    });
    await();
  }

  @Test
  public void testHttpClientName() throws Exception {
    HttpClient client1 = vertx.createHttpClient();
    try {
      FakeHttpClientMetrics metrics1 = FakeMetricsBase.getMetrics(client1);
      assertEquals("", metrics1.getName());
      String name = TestUtils.randomAlphaString(10);
      HttpClient client2 = vertx.createHttpClient(new HttpClientOptions().setMetricsName(name));
      try {
        FakeHttpClientMetrics metrics2 = FakeMetricsBase.getMetrics(client2);
        assertEquals(name, metrics2.getName());
      } finally {
        client2.close();
      }
    } finally {
      client1.close();
    }
  }

  @Test
  public void testHttpClientMetricsQueueLength() throws Exception {
    server = vertx.createHttpServer();
    List<Runnable> requests = Collections.synchronizedList(new ArrayList<>());
    server.requestHandler(req -> {
      requests.add(() -> {
        vertx.runOnContext(v -> {
          req.response().end();
        });
      });
    });
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.listen(8080, "localhost", onSuccess(s -> { listenLatch.countDown(); }));
    awaitLatch(listenLatch);
    client = vertx.createHttpClient();
    FakeHttpClientMetrics metrics = FakeHttpClientMetrics.getMetrics(client);
    CountDownLatch responsesLatch = new CountDownLatch(5);
    for (int i = 0;i < 5;i++) {
      client.getNow(8080, "localhost", "/somepath", resp -> {
        responsesLatch.countDown();
      });
    }
    assertWaitUntil(() -> requests.size() == 5);
    assertEquals(Collections.singleton("localhost:8080"), metrics.endpoints());
    assertEquals(0, (int)metrics.queueSize("localhost:8080"));
    assertEquals(5, (int)metrics.connectionCount("localhost:8080"));
    for (int i = 0;i < 8;i++) {
      client.getNow(8080, "localhost", "/somepath", resp -> {
      });
    }
    assertEquals(Collections.singleton("localhost:8080"), metrics.endpoints());
    assertEquals(8, (int)metrics.queueSize("localhost:8080"));
    assertEquals(5, (int)metrics.connectionCount("localhost:8080"));
    ArrayList<Runnable> copy = new ArrayList<>(requests);
    requests.clear();
    copy.forEach(Runnable::run);
    awaitLatch(responsesLatch);
    assertWaitUntil(() -> requests.size() == 5);
    assertEquals(Collections.singleton("localhost:8080"), metrics.endpoints());
    assertEquals(3, (int)metrics.queueSize("localhost:8080"));
    assertEquals(5, (int)metrics.connectionCount("localhost:8080"));
    copy = new ArrayList<>(requests);
    requests.clear();
    copy.forEach(Runnable::run);
    assertWaitUntil(() -> requests.size() == 3);
    assertEquals(Collections.singleton("localhost:8080"), metrics.endpoints());
    assertEquals(0, (int)metrics.queueSize("localhost:8080"));
    waitUntil(() -> metrics.connectionCount("localhost:8080") == 3);
  }

  @Test
  public void testHttpClientMetricsQueueClose() throws Exception {
    server = vertx.createHttpServer();
    List<Runnable> requests = Collections.synchronizedList(new ArrayList<>());
    server.requestHandler(req -> {
      requests.add(() -> {
        vertx.runOnContext(v -> {
          req.connection().close();
        });
      });
    });
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.listen(8080, "localhost", onSuccess(s -> { listenLatch.countDown(); }));
    awaitLatch(listenLatch);
    client = vertx.createHttpClient();
    FakeHttpClientMetrics metrics = FakeHttpClientMetrics.getMetrics(client);
    for (int i = 0;i < 5;i++) {
      client.getNow(8080, "localhost", "/somepath", resp -> {
      });
    }
    assertWaitUntil(() -> requests.size() == 5);
    EndpointMetric endpoint = metrics.endpoint("localhost:8080");
    assertEquals(5, endpoint.connectionCount.get());
    ArrayList<Runnable> copy = new ArrayList<>(requests);
    requests.clear();
    copy.forEach(Runnable::run);
    assertWaitUntil(() -> metrics.endpoints().isEmpty());
    assertEquals(0, endpoint.connectionCount.get());
  }

  @Test
  public void testHttpClientConnectionCloseAfterRequestEnd() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    client = vertx.createHttpClient();
    AtomicReference<EndpointMetric> endpointMetrics = new AtomicReference<>();
    server = vertx.createHttpServer().requestHandler(req -> {
      endpointMetrics.set(((FakeHttpClientMetrics)FakeHttpClientMetrics.getMetrics(client)).endpoint("localhost:8080"));
      req.response().end();
    }).listen(8080, "localhost", ar -> {
      assertTrue(ar.succeeded());
      started.countDown();
    });
    awaitLatch(started);
    CountDownLatch closed = new CountDownLatch(1);
    HttpClientRequest req = client.get(8080, "localhost", "/somepath");
    req.handler(onSuccess(resp -> {
      resp.endHandler(v1 -> {
        HttpConnection conn = req.connection();
        conn.closeHandler(v2 -> {
          closed.countDown();
        });
        conn.close();
      });
    }));
    req.end();
    awaitLatch(closed);
    EndpointMetric val = endpointMetrics.get();
    assertWaitUntil(() -> val.connectionCount.get() == 0);
    assertEquals(0, val.queueSize.get());
    assertEquals(0, val.requests.get());
  }

  @Test
  public void testMulti() {
    HttpServer s1 = vertx.createHttpServer();
    HttpServer s2 = vertx.createHttpServer();
    try {
      s1.requestHandler(req -> {
      });
      s1.listen(8080, ar1 -> {
        assertTrue(ar1.succeeded());
        s2.requestHandler(req -> {
          req.response().end();
        });
        s2.listen(8080, ar2 -> {
          assertTrue(ar2.succeeded());
          FakeHttpServerMetrics metrics1 = FakeMetricsBase.getMetrics(ar1.result());
          assertNotNull(metrics1);
          FakeHttpServerMetrics metrics2 = FakeMetricsBase.getMetrics(ar2.result());
          assertNotNull(metrics2);
          testComplete();
        });
      });
      await();
    } finally {
      s1.close();
      s2.close();
    }
  }

  @Test
  public void testHttpConnect1() {
    testHttpConnect(TestUtils.loopbackAddress(), socketMetric -> assertEquals(TestUtils.loopbackAddress(), socketMetric.remoteName));
  }

  @Test
  public void testHttpConnect2() {
    testHttpConnect(TestUtils.loopbackAddress(), socketMetric -> assertEquals(socketMetric.remoteAddress.host(), socketMetric.remoteName));
  }

  private void testHttpConnect(String host, Consumer<SocketMetric> checker) {
    server = vertx.createHttpServer();
    AtomicReference<HttpClientMetric> clientMetric = new AtomicReference<>();
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      HttpServerMetric serverMetric = metrics.getMetric(req);
      assertNotNull(serverMetric);
      req.response().setStatusCode(200);
      req.response().setStatusMessage("Connection established");
      NetSocket so = req.netSocket();
      so.handler(so::write);
      so.closeHandler(v -> {
        assertNull(metrics.getMetric(req));
        assertFalse(serverMetric.socket.connected.get());
        assertEquals(5, serverMetric.socket.bytesRead.get());
        assertEquals(5, serverMetric.socket.bytesWritten.get());
        assertEquals(serverMetric.socket.remoteAddress.host(), serverMetric.socket.remoteName);
        assertFalse(clientMetric.get().socket.connected.get());
        assertEquals(5, clientMetric.get().socket.bytesRead.get());
        assertEquals(5, clientMetric.get().socket.bytesWritten.get());
        checker.accept(clientMetric.get().socket);
        testComplete();
      });
    }).listen(8080, ar1 -> {
      assertTrue(ar1.succeeded());
      client = vertx.createHttpClient();
      HttpClientRequest request = client.request(HttpMethod.CONNECT, 8080, host, "/");
      FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(client);
      request.handler(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        clientMetric.set(metrics.getMetric(request));
        assertNotNull(clientMetric.get());
        NetSocket socket = resp.netSocket();
        socket.write(Buffer.buffer("hello"));
        socket.handler(buf -> {
          assertEquals("hello", buf.toString());
          assertNull(metrics.getMetric(request));
          socket.close();
        });
      })).end();
    });
    await();
  }

  @Test
  public void testDatagram1() throws Exception {
    testDatagram("127.0.0.1", packet -> {
      assertEquals("127.0.0.1", packet.remoteAddress.host());
      assertEquals(1234, packet.remoteAddress.port());
      assertEquals(5, packet.numberOfBytes);
    });
  }

  @Test
  public void testDatagram2() throws Exception {
    testDatagram("localhost", packet -> {
      assertEquals("localhost", packet.remoteAddress.host());
      assertEquals(1234, packet.remoteAddress.port());
      assertEquals(5, packet.numberOfBytes);
    });
  }

  private void testDatagram(String host, Consumer<PacketMetric> checker) throws Exception {
    DatagramSocket peer1 = vertx.createDatagramSocket();
    DatagramSocket peer2 = vertx.createDatagramSocket();
    try {
      CountDownLatch latch = new CountDownLatch(1);
      peer1.handler(packet -> {
        FakeDatagramSocketMetrics peer1Metrics = FakeMetricsBase.getMetrics(peer1);
        FakeDatagramSocketMetrics peer2Metrics = FakeMetricsBase.getMetrics(peer2);
        assertEquals(host, peer1Metrics.getLocalName());
        assertEquals("127.0.0.1", peer1Metrics.getLocalAddress().host());
        assertNull(peer2Metrics.getLocalAddress());
        assertEquals(1, peer1Metrics.getReads().size());
        PacketMetric read = peer1Metrics.getReads().get(0);
        assertEquals(5, read.numberOfBytes);
        assertEquals(0, peer1Metrics.getWrites().size());
        assertEquals(0, peer2Metrics.getReads().size());
        assertEquals(1, peer2Metrics.getWrites().size());
        checker.accept(peer2Metrics.getWrites().get(0));
        testComplete();
      });
      peer1.listen(1234, host, ar -> {
        assertTrue(ar.succeeded());
        latch.countDown();
      });
      awaitLatch(latch);
      peer2.send("hello", 1234, host, ar -> {
        assertTrue(ar.succeeded());
      });
      await();
    } finally {
      peer1.close();
      peer2.close();
    }
  }

  @Test
  public void testThreadPoolMetricsWithExecuteBlocking() {
    Map<String, PoolMetrics> all = FakePoolMetrics.getPoolMetrics();

    FakePoolMetrics metrics = (FakePoolMetrics) all.get("vert.x-worker-thread");

    assertThat(metrics.getPoolSize(), is(getOptions().getInternalBlockingPoolSize()));
    assertThat(metrics.numberOfIdleThreads(), is(getOptions().getWorkerPoolSize()));

    Handler<Future<Void>> job = getSomeDumbTask();

    AtomicInteger counter = new AtomicInteger();
    AtomicBoolean hadWaitingQueue = new AtomicBoolean();
    AtomicBoolean hadIdle = new AtomicBoolean();
    AtomicBoolean hadRunning = new AtomicBoolean();
    for (int i = 0; i < 100; i++) {
      vertx.executeBlocking(
          job,
          ar -> {
            if (metrics.numberOfWaitingTasks() > 0) {
              hadWaitingQueue.set(true);
            }
            if (metrics.numberOfIdleThreads() > 0) {
              hadIdle.set(true);
            }
            if (metrics.numberOfRunningTasks() > 0) {
              hadRunning.set(true);
            }
            if (counter.incrementAndGet() == 100) {
              testComplete();
            }
          }
      );
    }

    await();

    assertEquals(metrics.numberOfSubmittedTask(), 100);
    assertEquals(metrics.numberOfCompletedTasks(), 100);
    assertTrue(hadIdle.get());
    assertTrue(hadWaitingQueue.get());
    assertTrue(hadRunning.get());

    assertEquals(metrics.numberOfIdleThreads(), getOptions().getWorkerPoolSize());
    assertEquals(metrics.numberOfRunningTasks(), 0);
    assertEquals(metrics.numberOfWaitingTasks(), 0);
  }

  @Test
  public void testThreadPoolMetricsWithInternalExecuteBlocking() {
    Map<String, PoolMetrics> all = FakePoolMetrics.getPoolMetrics();
    FakePoolMetrics metrics = (FakePoolMetrics) all.get("vert.x-internal-blocking");

    assertThat(metrics.getPoolSize(), is(getOptions().getInternalBlockingPoolSize()));
    assertThat(metrics.numberOfIdleThreads(), is(getOptions().getInternalBlockingPoolSize()));

    AtomicInteger counter = new AtomicInteger();
    AtomicBoolean hadWaitingQueue = new AtomicBoolean();
    AtomicBoolean hadIdle = new AtomicBoolean();
    AtomicBoolean hadRunning = new AtomicBoolean();

    VertxInternal v = (VertxInternal) vertx;
    Map<Integer, CountDownLatch> latches = new HashMap<>();
    int num = VertxOptions.DEFAULT_INTERNAL_BLOCKING_POOL_SIZE;
    int count = num * 5;
    for (int i = 0; i < count; i++) {
      CountDownLatch latch = latches.computeIfAbsent(i / num, k -> new CountDownLatch(num));
      v.executeBlockingInternal(fut -> {
        latch.countDown();
        try {
          awaitLatch(latch);
        } catch (InterruptedException e) {
          fail(e);
          Thread.currentThread().interrupt();
        }
        if (metrics.numberOfRunningTasks() > 0) {
          hadRunning.set(true);
        }
        if (metrics.numberOfWaitingTasks() > 0) {
          hadWaitingQueue.set(true);
        }
        fut.complete();
      }, ar -> {
        if (metrics.numberOfIdleThreads() > 0) {
          hadIdle.set(true);
        }
        if (counter.incrementAndGet() == count) {
          testComplete();
        }
      });
    }

    await();

    assertEquals(metrics.numberOfSubmittedTask(), 100);
    assertEquals(metrics.numberOfCompletedTasks(), 100);
    assertTrue(hadIdle.get());
    assertTrue(hadWaitingQueue.get());
    assertTrue(hadRunning.get());

    assertEquals(metrics.numberOfIdleThreads(), getOptions().getWorkerPoolSize());
    assertEquals(metrics.numberOfRunningTasks(), 0);
    assertEquals(metrics.numberOfWaitingTasks(), 0);
  }

  @Test
  public void testThreadPoolMetricsWithWorkerVerticle() throws Exception {
    AtomicInteger counter = new AtomicInteger();
    Map<String, PoolMetrics> all = FakePoolMetrics.getPoolMetrics();
    FakePoolMetrics metrics = (FakePoolMetrics) all.get("vert.x-worker-thread");

    assertThat(metrics.getPoolSize(), is(getOptions().getInternalBlockingPoolSize()));
    assertThat(metrics.numberOfIdleThreads(), is(getOptions().getWorkerPoolSize()));

    AtomicBoolean hadWaitingQueue = new AtomicBoolean();
    AtomicBoolean hadIdle = new AtomicBoolean();
    AtomicBoolean hadRunning = new AtomicBoolean();

    int count = 100;

    AtomicInteger msg = new AtomicInteger();

    CountDownLatch latch = new CountDownLatch(1);
    Verticle worker = new AbstractVerticle() {
      @Override
      public void start(Future<Void> done) throws Exception {
        vertx.eventBus().localConsumer("message", d -> {
            msg.incrementAndGet();
            try {
              Thread.sleep(10);

              if (metrics.numberOfWaitingTasks() > 0) {
                hadWaitingQueue.set(true);
              }
              if (metrics.numberOfIdleThreads() > 0) {
                hadIdle.set(true);
              }
              if (metrics.numberOfRunningTasks() > 0) {
                hadRunning.set(true);
              }

              if (counter.incrementAndGet() == count) {
                latch.countDown();
              }

            } catch (InterruptedException e) {
              Thread.currentThread().isInterrupted();
            }
          }
        );
        done.complete();
      }
    };


    vertx.deployVerticle(worker, new DeploymentOptions().setWorker(true), s -> {
      for (int i = 0; i < count; i++) {
        vertx.eventBus().send("message", i);
      }
    });

    awaitLatch(latch);

    assertWaitUntil(() -> count + 1 == metrics.numberOfCompletedTasks());

    // The verticle deployment is also executed on the worker thread pool
    assertEquals(count + 1, metrics.numberOfSubmittedTask());
    assertEquals(count + 1, metrics.numberOfCompletedTasks());
    assertTrue("Had no idle threads", hadIdle.get());
    assertTrue("Had no waiting tasks", hadWaitingQueue.get());
    assertTrue("Had running tasks", hadRunning.get());

    assertEquals(getOptions().getWorkerPoolSize(), metrics.numberOfIdleThreads());
    assertEquals(0, metrics.numberOfRunningTasks());
    assertEquals(0, metrics.numberOfWaitingTasks());
  }

  @Test
  public void testThreadPoolMetricsWithNamedExecuteBlocking() {
    vertx.close(); // Close the instance automatically created
    vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(new FakeMetricsFactory())));

    WorkerExecutor workerExec = vertx.createSharedWorkerExecutor("my-pool", 10);

    Map<String, PoolMetrics> all = FakePoolMetrics.getPoolMetrics();

    FakePoolMetrics metrics = (FakePoolMetrics) all.get("my-pool");

    assertThat(metrics.getPoolSize(), is(10));
    assertThat(metrics.numberOfIdleThreads(), is(10));

    Handler<Future<Void>> job = getSomeDumbTask();

    AtomicInteger counter = new AtomicInteger();
    AtomicBoolean hadWaitingQueue = new AtomicBoolean();
    AtomicBoolean hadIdle = new AtomicBoolean();
    AtomicBoolean hadRunning = new AtomicBoolean();
    for (int i = 0; i < 100; i++) {
      workerExec.executeBlocking(
          job,
          false,
          ar -> {
            if (metrics.numberOfWaitingTasks() > 0) {
              hadWaitingQueue.set(true);
            }
            if (metrics.numberOfIdleThreads() > 0) {
              hadIdle.set(true);
            }
            if (metrics.numberOfRunningTasks() > 0) {
              hadRunning.set(true);
            }
            if (counter.incrementAndGet() == 100) {
              testComplete();
            }
          });
    }

    await();

    assertEquals(metrics.numberOfSubmittedTask(), 100);
    assertEquals(metrics.numberOfCompletedTasks(), 100);
    assertTrue(hadIdle.get());
    assertTrue(hadWaitingQueue.get());
    assertTrue(hadRunning.get());

    assertEquals(metrics.numberOfIdleThreads(), 10);
    assertEquals(metrics.numberOfRunningTasks(), 0);
    assertEquals(metrics.numberOfWaitingTasks(), 0);
  }

  @Test
  public void testWorkerPoolClose() {
    WorkerExecutor ex1 = vertx.createSharedWorkerExecutor("ex1");
    WorkerExecutor ex1_ = vertx.createSharedWorkerExecutor("ex1");
    WorkerExecutor ex2 = vertx.createSharedWorkerExecutor("ex2");
    Map<String, PoolMetrics> all = FakePoolMetrics.getPoolMetrics();
    FakePoolMetrics metrics1 = (FakePoolMetrics) all.get("ex1");
    FakePoolMetrics metrics2 = (FakePoolMetrics) all.get("ex2");
    assertNotNull(metrics1);
    assertNotNull(metrics2);
    assertNotSame(metrics1, metrics2);
    assertFalse(metrics1.isClosed());
    assertFalse(metrics2.isClosed());
    ex1_.close();
    assertFalse(metrics1.isClosed());
    assertFalse(metrics2.isClosed());
    ex1.close();
    assertTrue(metrics1.isClosed());
    assertFalse(metrics2.isClosed());
    ex2.close();
    assertTrue(metrics1.isClosed());
    assertTrue(metrics2.isClosed());
  }

  private Handler<Future<Void>> getSomeDumbTask() {
    return (future) -> {
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().isInterrupted();
      }
      future.complete(null);
    };
  }

  @Test
  public void testInitialization() {
    assertSame(vertx, ((FakeVertxMetrics)FakeMetricsBase.getMetrics(vertx)).vertx());
    startNodes(1);
    assertSame(vertices[0], ((FakeVertxMetrics)FakeMetricsBase.getMetrics(vertices[0])).vertx());
    EventLoopGroup group = vertx.nettyEventLoopGroup();
    Set<EventLoop> loops = new HashSet<>();
    int count = 0;
    while (true) {
      EventLoop next = group.next();
      if (!loops.add(next)) {
        break;
      }
      count++;
      assertTrue(count <= VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE);
    }
    assertEquals(loops.size(), VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE);
  }
}
