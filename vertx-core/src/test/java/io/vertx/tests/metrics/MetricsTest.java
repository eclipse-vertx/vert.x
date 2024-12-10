/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.metrics;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakemetrics.*;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.tls.Trust;
import io.vertx.tests.http.HttpOptionsFactory;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.hamcrest.core.Is.is;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MetricsTest extends VertxTestBase {

  private static final String ADDRESS1 = "some-address1";

  private HttpServer server;
  private HttpClientAgent client;
  private WebSocketClient wsClient;
  private VertxMetricsFactory metricsFactory;

  protected void tearDown() throws Exception {
    if (client != null) {
      try {
        client.close();
      } catch (IllegalStateException ignore) {
        // Client was already closed by the test
      }
    }
    if (wsClient != null) {
      try {
        wsClient.close();
      } catch (IllegalStateException ignore) {
        // Client was already closed by the test
      }
    }
    if (server != null) {
      CountDownLatch latch = new CountDownLatch(1);
      server.close().onComplete(onSuccess(v -> {
        latch.countDown();
      }));
      awaitLatch(latch);
    }
    super.tearDown();
    FakeMetricsBase.sanityCheck();
  }

  @Override
  public void setUp() throws Exception {
    metricsFactory = new FakeMetricsFactory();
    super.setUp();
  }

  @Override
  protected VertxMetricsFactory getMetrics() {
    return metricsFactory;
  }

  @Override
  protected Vertx vertx() {
    return super.vertx();
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
      consumer.completion().onComplete(onSuccess(v -> {
        if (broadcastCount.incrementAndGet() == to.length) {
          String msg = TestUtils.randomAlphaString(10);
          if (publish) {
            from.eventBus().publish(ADDRESS1, msg);
          } else {
            from.eventBus().send(ADDRESS1, msg);
          }
        }
      }));
      consumer.handler(msg -> {
        if (receiveCount.incrementAndGet() == to.length) {
          testComplete();
        }
      });
    }
    waitUntil(() -> eventBusMetrics.getSentMessages().size() == expected.length);
    assertEquals(new HashSet<>(Arrays.asList(expected)), new HashSet<>(eventBusMetrics.getSentMessages()));
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
    consumer.completion().onComplete(done -> {
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
      consumer.completion().onComplete(done -> {
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
  public void testReplyMessageFromSelf() throws Exception {
    testReply(vertx, vertx, true, false);
  }

  @Test
  public void testReplyMessageFromRemote() throws Exception {
    startNodes(2);
    testReply(vertices[0], vertices[1], false, true);
  }

  private void testReply(Vertx from, Vertx to, boolean expectedLocal, boolean expectedRemote) throws Exception {
    FakeEventBusMetrics fromMetrics = FakeMetricsBase.getMetrics(from.eventBus());
    FakeEventBusMetrics toMetrics = FakeMetricsBase.getMetrics(to.eventBus());
    MessageConsumer<Object> consumer = to.eventBus().consumer(ADDRESS1);
    CountDownLatch latch = new CountDownLatch(1);
    consumer.completion().onComplete(onSuccess(v -> {
      String msg = TestUtils.randomAlphaString(10);
      from.eventBus().request(ADDRESS1, msg).onComplete(onSuccess(reply -> {
        latch.countDown();
      }));
    }));
    consumer.handler(msg -> {
      toMetrics.getReceivedMessages().clear();
      toMetrics.getSentMessages().clear();
      msg.reply(TestUtils.randomAlphaString(10));
    });
    awaitLatch(latch);
    assertWaitUntil(() -> fromMetrics.getReceivedMessages().size() > 0);
    ReceivedMessage receivedMessage = fromMetrics.getReceivedMessages().get(0);
    assertEquals(false, receivedMessage.publish);
    assertEquals(expectedLocal, receivedMessage.local);
    assertEquals(1, receivedMessage.handlers);
    assertWaitUntil(() -> toMetrics.getSentMessages().size() > 0);
    SentMessage sentMessage = toMetrics.getSentMessages().get(0);
    assertEquals(false, sentMessage.publish);
    assertEquals(expectedLocal, sentMessage.local);
    assertEquals(expectedRemote, sentMessage.remote);
    assertEquals(sentMessage.address, receivedMessage.address);
  }

  @Test
  public void testDiscardOnOverflow1() throws Exception {
    startNodes(2);
    Vertx from = vertices[0], to = vertices[1];
    FakeEventBusMetrics toMetrics = FakeMetricsBase.getMetrics(to.eventBus());
    MessageConsumer<Object> consumer = to.eventBus().consumer(ADDRESS1);
    int num = 10;
    consumer.setMaxBufferedMessages(num);
    consumer.pause();
    consumer.completion().onComplete(onSuccess(v -> {
      for (int i = 0;i < num;i++) {
        from.eventBus().send(ADDRESS1, "" + i);
      }
      from.eventBus().send(ADDRESS1, "last");
    }));
    consumer.handler(msg -> fail());
    waitUntil(() -> toMetrics.getRegistrations().size() == 1);
    HandlerMetric metric = toMetrics.getRegistrations().get(0);
    waitUntil(() -> metric.scheduleCount.get() == num + 1);
    waitUntil(() -> metric.discardCount.get() == 1);
  }

  @Test
  public void testDiscardOnOverflow2() {
    startNodes(2);
    Vertx from = vertices[0], to = vertices[1];
    FakeEventBusMetrics toMetrics = FakeMetricsBase.getMetrics(to.eventBus());
    MessageConsumer<Object> consumer = to.eventBus().consumer(ADDRESS1);
    int num = 10;
    consumer.setMaxBufferedMessages(num);
    consumer.pause();
    consumer.completion().onComplete(onSuccess(v -> {
      for (int i = 0;i < num;i++) {
        from.eventBus().send(ADDRESS1, "" + i);
      }
    }));
    consumer.handler(msg -> fail());
    waitUntil(() -> toMetrics.getRegistrations().size() == 1);
    HandlerMetric metric = toMetrics.getRegistrations().get(0);
    waitUntil(() -> metric.scheduleCount.get() == num);
    consumer.setMaxBufferedMessages(num - 1);
    waitUntil(() -> metric.discardCount.get() == 1);
  }

  @Test
  public void testDiscardMessageOnUnregistration() {
    startNodes(2);
    Vertx from = vertices[0], to = vertices[1];
    FakeEventBusMetrics toMetrics = FakeMetricsBase.getMetrics(to.eventBus());
    MessageConsumer<Object> consumer = to.eventBus().consumer(ADDRESS1);
    consumer.pause();
    consumer.completion().onComplete(onSuccess(v -> {
      from.eventBus().send(ADDRESS1, "last");
    }));
    consumer.handler(msg -> fail());
    waitUntil(() -> toMetrics.getRegistrations().size() == 1);
    HandlerMetric metric = toMetrics.getRegistrations().get(0);
    waitUntil(() -> metric.scheduleCount.get() == 1);
    consumer.unregister();
    waitUntil(() -> metric.discardCount.get() == 1);
  }

  @Test
  public void testSignalMetricEventAfterUnregistration() {
    FakeEventBusMetrics toMetrics = FakeMetricsBase.getMetrics(vertx.eventBus());
    int nums = 1000;
    List<HandlerMetric> metrics = new ArrayList<>();
    for (int i = 0;i < nums;i++) {
      String addr = ADDRESS1 + "-" + i;
      MessageConsumer<Object> consumer = vertx.eventBus().consumer(addr);
      consumer.handler(msg -> {
      });
      HandlerMetric metric = toMetrics.getRegistrations().stream().filter(m -> m.address.equals(addr)).findFirst().get();
      metrics.add(metric);
      vertx.eventBus().send(addr, "the-msg");
      consumer.unregister();
    }
    assertWaitUntil(() -> metrics.stream().noneMatch(metric -> metric.discardCount.get() == 0 && metric.localDeliveredCount.get() == 0));
  }

  @Test
  public void testHandlerRegistration() throws Exception {
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(vertx.eventBus());
    MessageConsumer<Object> consumer = vertx.eventBus().consumer(ADDRESS1, msg -> {
    });
    CountDownLatch latch = new CountDownLatch(1);
    consumer.completion().onComplete(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    assertEquals(1, metrics.getRegistrations().size());
    HandlerMetric registration = metrics.getRegistrations().get(0);
    assertEquals(ADDRESS1, registration.address);
    consumer.unregister().onComplete(onSuccess(v1 -> {
      assertEquals(0, metrics.getRegistrations().size());
      consumer.unregister().onComplete(onSuccess(v2 -> {
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testClusterUnregistration() {
    startNodes(1);
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(vertices[0].eventBus());
    Context ctx = vertices[0].getOrCreateContext();
    ctx.runOnContext(v1 -> {
      MessageConsumer<Object> consumer = vertices[0].eventBus().consumer(ADDRESS1, ar -> {
        fail("Should not receive message");
      });
      consumer.completion().onComplete(onSuccess(v2 -> {
        consumer.unregister().onComplete(onSuccess(v3 -> {
          assertSame(Vertx.currentContext(), ctx);
          List<HandlerMetric> registrations = metrics.getRegistrations();
          assertEquals(Collections.emptyList(), registrations);
          testComplete();
        }));
      }));
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
    to.runOnContext(v -> {
      to.eventBus().consumer(ADDRESS1, msg -> {
        HandlerMetric registration = assertRegistration(metrics);
        assertEquals(ADDRESS1, registration.address);
        assertEquals(1, registration.scheduleCount.get());
        assertEquals(expectedLocalCount, registration.localScheduleCount.get());
        assertEquals(1, registration.deliveredCount.get());
        msg.reply("pong");
      }).completion().onComplete(onSuccess(v2 -> {
        to.runOnContext(v3 -> {
          latch1.countDown();
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
    from.eventBus().request(ADDRESS1, "ping").onComplete(onSuccess(reply -> {
      assertEquals(1, registration.scheduleCount.get());
      // This might take a little time
      assertWaitUntil(() -> 1 == registration.deliveredCount.get());
      assertEquals(expectedLocalCount, registration.localDeliveredCount.get());
      testComplete();
    }));
    assertWaitUntil(() -> registration.scheduleCount.get() == 1);
    await();
    assertEquals(expectedLocalCount, registration.localDeliveredCount.get());
  }

  @Test
  public void testHandlerMetricReply() throws Exception {
    AtomicReference<HandlerMetric> replyRegistration = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(vertx.eventBus());
    vertx.eventBus().consumer(ADDRESS1, msg -> {
      assertEquals(ADDRESS1, metrics.getRegistrations().get(0).address);
      assertWaitUntil(() -> metrics.getRegistrations().size() == 2);
      HandlerMetric registration = metrics.getRegistrations().get(1);
      assertEquals(0, registration.scheduleCount.get());
      assertEquals(0, registration.deliveredCount.get());
      assertEquals(0, registration.localDeliveredCount.get());
      replyRegistration.set(registration);
      msg.reply("pong");
    }).completion().onComplete(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    vertx.eventBus().request(ADDRESS1, "ping").onComplete(onSuccess(reply -> {
      assertEquals(ADDRESS1, metrics.getRegistrations().get(0).address);
      HandlerMetric registration = replyRegistration.get();
      assertEquals(1, registration.scheduleCount.get());
      assertEquals(1, registration.deliveredCount.get());
      assertEquals(1, registration.localDeliveredCount.get());
      vertx.runOnContext(v -> {
        assertEquals(ADDRESS1, metrics.getRegistrations().get(0).address);
        assertEquals(1, registration.scheduleCount.get());
        assertEquals(1, registration.deliveredCount.get());
        assertEquals(1, registration.localDeliveredCount.get());
      });
      testComplete();
    }));
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
    }).completion().onComplete(ar -> {
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
    eb.request(ADDRESS1, "bar").onComplete(onFailure(ar -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(eb);
    assertEquals(Collections.singletonList("some-address1"), metrics.getReplyFailureAddresses());
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
    eb.request(ADDRESS1, "bar", new DeliveryOptions().setSendTimeout(10))
      .onComplete(onFailure(err -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    waitUntil(() -> metrics.getReplyFailureAddresses().size() == 1, 11_000);
    assertEquals(Collections.singletonList(ReplyFailure.TIMEOUT), metrics.getReplyFailures());
  }

  @Test
  public void testReplyFailureTimeout2() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    EventBus eb = vertx.eventBus();
    eb.consumer(ADDRESS1, msg -> {
      msg.replyAndRequest("juu", new DeliveryOptions().setSendTimeout(10))
        .onComplete(onFailure(err -> {
          latch.countDown();
        }));
    });
    eb.request(ADDRESS1, "bar");
    awaitLatch(latch);
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(eb);
    waitUntil(() -> metrics.getReplyFailureAddresses().size() == 1);
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
    }).completion().onComplete(onSuccess(v -> {
      regLatch.countDown();
    }));
    awaitLatch(regLatch);
    eb.request("foo", "bar", new DeliveryOptions()).onComplete(onFailure(err -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    assertWaitUntil(() -> metrics.getReplyFailureAddresses().equals(Collections.singletonList("foo")));
    assertEquals(Collections.singletonList(ReplyFailure.RECIPIENT_FAILURE), metrics.getReplyFailures());
  }

  @Test
  public void testServerWebSocket() throws InterruptedException {
    server = vertx.createHttpServer();
    AtomicReference<ServerWebSocket> wsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    server.webSocketHandler(ws -> {
      wsRef.set(ws);
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      WebSocketMetric metric = metrics.getWebSocketMetric(ws);
      assertNotNull(metric);
      ws.handler(ws::write);
      ws.closeHandler(closed -> {
        latch.countDown();
      });
    });
    awaitFuture(server.listen(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST));
    wsClient = vertx.createWebSocketClient();
    wsClient.connect(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(onSuccess(ws -> {
      ws.write(Buffer.buffer("wibble"));
      ws.handler(buff -> ws.close());
    }));
    awaitLatch(latch);
    FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
    assertWaitUntil(() -> metrics.getWebSocketMetric(wsRef.get()) == null);
  }

  @Test
  public void testServerWebSocketUpgrade() throws InterruptedException {
    server = vertx.createHttpServer();
    AtomicReference<ServerWebSocket> ref = new AtomicReference<>();
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      assertNotNull(metrics.getRequestMetric(req));
      req.toWebSocket().onComplete(onSuccess(ws -> {
        assertNull(metrics.getRequestMetric(req));
        WebSocketMetric metric = metrics.getWebSocketMetric(ws);
        assertNotNull(metric);
        ws.handler(ws::write);
        ws.closeHandler(closed -> {
          ref.set(ws);
        });
      }));
    });
    awaitFuture(server.listen(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST));
    wsClient = vertx.createWebSocketClient();
    wsClient.connect(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/" + TestUtils.randomAlphaString(16))
      .onComplete(onSuccess(ws -> {
        ws.write(Buffer.buffer("wibble"));
        ws.handler(buff -> {
          ws.close();
        });
      }));
    assertWaitUntil(() -> ref.get() != null);
    FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
    assertWaitUntil(() -> metrics.getWebSocketMetric(ref.get()) == null);
  }

  @Test
  public void testWebSocket() throws InterruptedException {
    server = vertx.createHttpServer();
    server.webSocketHandler(ws -> {
      ws.write(Buffer.buffer("wibble"));
      ws.handler(buff -> ws.close());
    });
    awaitFuture(server.listen(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST));
    wsClient = vertx.createWebSocketClient();
    FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(wsClient);
    CountDownLatch closeLatch = new CountDownLatch(1);
    Future<WebSocket> fut = wsClient.connect(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/");
    fut.onComplete(onSuccess(ws -> {
      WebSocketMetric metric = metrics.getMetric(ws);
      assertNotNull(metric);
      ws.closeHandler(closed -> {
        closeLatch.countDown();
      });
      ws.handler(ws::write);
    }));
    WebSocket ws = awaitFuture(fut);
    assertWaitUntil(() -> metrics.getMetric(ws) == null);
  }

  @Test
  public void testHttpClientName() throws Exception {
    HttpClientAgent client1 = vertx.createHttpClient();
    try {
      FakeHttpClientMetrics metrics1 = FakeMetricsBase.getMetrics(client1);
      assertEquals("", metrics1.getName());
      String name = TestUtils.randomAlphaString(10);
      HttpClientAgent client2 = vertx.createHttpClient(new HttpClientOptions().setMetricsName(name));
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
    awaitFuture(server.listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost"));
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAliveTimeout(1));
    FakeHttpClientMetrics clientMetrics = FakeHttpClientMetrics.getMetrics(client);
    CountDownLatch responsesLatch = new CountDownLatch(5);
    for (int i = 0;i < 5;i++) {
      client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/somepath")
        .compose(HttpClientRequest::send)
        .onComplete(resp -> {
        responsesLatch.countDown();
      });
    }
    FakePoolMetrics queueMetrics = FakePoolMetrics.getMetrics("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT);
    assertWaitUntil(() -> requests.size() == 5);
    assertEquals(Collections.singleton("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT), clientMetrics.endpoints());
    assertEquals(0, queueMetrics.pending());
    assertEquals(5, (int)clientMetrics.connectionCount("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT));
    for (int i = 0;i < 8;i++) {
      client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/somepath")
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
      }));
    }
    assertEquals(Collections.singleton("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT), clientMetrics.endpoints());
    assertEquals(8, queueMetrics.pending());
    assertEquals(5, (int)clientMetrics.connectionCount("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT));
    ArrayList<Runnable> copy = new ArrayList<>(requests);
    requests.clear();
    copy.forEach(Runnable::run);
    awaitLatch(responsesLatch);
    assertWaitUntil(() -> requests.size() == 5);
    assertEquals(Collections.singleton("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT), clientMetrics.endpoints());
    assertEquals(3, queueMetrics.pending());
    assertEquals(5, (int)clientMetrics.connectionCount("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT));
    copy = new ArrayList<>(requests);
    requests.clear();
    copy.forEach(Runnable::run);
    assertWaitUntil(() -> requests.size() == 3);
    assertEquals(Collections.singleton("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT), clientMetrics.endpoints());
    assertEquals(0, queueMetrics.pending());
    assertWaitUntil(() -> clientMetrics.connectionCount("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT) == 3);
    copy = new ArrayList<>(requests);
    requests.clear();
    copy.forEach(Runnable::run);
    assertWaitUntil(() -> clientMetrics.connectionCount("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT) == null);
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
    awaitFuture(server.listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost"));
    client = vertx.createHttpClient();
    FakeHttpClientMetrics metrics = FakeHttpClientMetrics.getMetrics(client);
    for (int i = 0;i < 5;i++) {
      client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/somepath")
        .compose(HttpClientRequest::end)
        .onComplete(onSuccess(v -> {
      }));
    }
    assertWaitUntil(() -> requests.size() == 5);
    EndpointMetric endpoint = metrics.endpoint("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT);
    assertEquals(5, endpoint.connectionCount.get());
    ArrayList<Runnable> copy = new ArrayList<>(requests);
    requests.clear();
    copy.forEach(Runnable::run);
    assertWaitUntil(() -> metrics.endpoints().isEmpty());
    assertEquals(0, endpoint.connectionCount.get());
  }

  @Test
  public void testHttpClientConnectionCloseAfterRequestEnd() throws Exception {
    client = vertx.createHttpClient();
    AtomicReference<EndpointMetric> endpointMetrics = new AtomicReference<>();
    AtomicReference<FakePoolMetrics> queueMetrics = new AtomicReference<>();
    server = vertx.createHttpServer().requestHandler(req -> {
      endpointMetrics.set(((FakeHttpClientMetrics)FakeHttpClientMetrics.getMetrics(client)).endpoint("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT));
      queueMetrics.set(FakePoolMetrics.getMetrics("localhost:" + HttpTestBase.DEFAULT_HTTP_PORT));
      req.response().end();
    });
    awaitFuture(server.listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost"));
    client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/somepath")
      .compose(req -> req.send()
        .compose(HttpClientResponse::end)
        .compose(v ->  req.connection().close()))
      .await(20, TimeUnit.SECONDS);
    assertWaitUntil(() -> endpointMetrics.get().connectionCount.get() == 0);
    assertEquals(0, endpointMetrics.get().requestCount.get());
    assertEquals(0, queueMetrics.get().pending());
  }

  @Test
  public void testMulti() {
    int size = 2;
    waitFor(size);
    client = vertx.createHttpClient();
    List<HttpServer> servers = new ArrayList<>();
    List<HttpServerRequest> requests = Collections.synchronizedList(new ArrayList<>());
    BiConsumer<HttpServer, HttpServerRequest> check = (server, request) -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      HttpServerMetric metric = metrics.getRequestMetric(request);
      assertNotNull(metric);
      requests.add(request);
      if (requests.size() == size) {
        requests.forEach(req -> req.response().end());
      }
    };
    for (int i = 0;i < size;i++) {
      HttpServer server = vertx.createHttpServer();
      server.requestHandler(req -> check.accept(server, req));
      servers.add(server);
    }
    try {
      List<Future<?>> collect = servers.stream().map(server -> server.listen(HttpTestBase.DEFAULT_HTTP_PORT)).collect(Collectors.toList());
      Future
        .all(collect)
        .onSuccess(v -> {
          assertEquals("Was expecting a single metric", 1, servers.stream().map(FakeMetricsBase::getMetrics).distinct().count());
          for (int i = 0;i < 2;i++) {
            client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", TestUtils.randomAlphaString(16))
              .compose(HttpClientRequest::send)
              .onComplete(onSuccess(resp -> {
                complete();
              }));
          }
      });
      await();
    } finally {
      servers.forEach(HttpServer::close);
    }
  }

  @Test
  public void testHttpConnect1() throws InterruptedException {
    testHttpConnect(TestUtils.loopbackAddress(), socketMetric -> assertEquals(TestUtils.loopbackAddress(), socketMetric.remoteName));
  }

  @Test
  public void testHttpConnect2() throws InterruptedException {
    testHttpConnect(TestUtils.loopbackAddress(), socketMetric -> assertEquals(socketMetric.remoteAddress.host(), socketMetric.remoteName));
  }

  private void testHttpConnect(String host, Consumer<SocketMetric> checker) throws InterruptedException {
    waitFor(2);
    server = vertx.createHttpServer();
    AtomicReference<HttpClientMetric> clientMetric = new AtomicReference<>();
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      HttpServerMetric serverMetric = metrics.getRequestMetric(req);
      assertNotNull(serverMetric);
      req.response().setStatusCode(200);
      req.response().setStatusMessage("Connection established");
      req.toNetSocket().onComplete(onSuccess(so -> {
        so.handler(so::write);
        so.closeHandler(v -> {
          assertNull(metrics.getRequestMetric(req));
          assertFalse(serverMetric.socket.connected.get());
          assertEquals(5, serverMetric.socket.bytesRead.get());
          assertEquals(5, serverMetric.socket.bytesWritten.get());
          assertEquals(serverMetric.socket.remoteAddress.host(), serverMetric.socket.remoteName);
          assertFalse(serverMetric.socket.connected.get());
          assertEquals(5, serverMetric.socket.bytesRead.get());
          assertEquals(5, serverMetric.socket.bytesWritten.get());
          checker.accept(serverMetric.socket);
          complete();
        });
      }));
    });
    awaitFuture(server.listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost"));
    client = vertx.createHttpClient();
    client.request(new RequestOptions()
      .setMethod(HttpMethod.CONNECT)
      .setPort(HttpTestBase.DEFAULT_HTTP_PORT)
      .setHost(host)
      .setURI(TestUtils.randomAlphaString(16))).onComplete(onSuccess(req -> {
      FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(client);
      req.connect().onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        clientMetric.set(metrics.getMetric(req));
        assertNotNull(clientMetric.get());
        NetSocket socket = resp.netSocket();
        socket.write(Buffer.buffer("hello"));
        socket.handler(buf -> {
          assertEquals("hello", buf.toString());
          assertNotNull(metrics.getMetric(req));
          socket.closeHandler(v -> {
            assertNull(metrics.getMetric(req));
          });
          socket.close();
          complete();
        });
      }));
    }));
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
    waitFor(2);
    DatagramSocket peer1 = vertx.createDatagramSocket();
    DatagramSocket peer2 = vertx.createDatagramSocket();
    FakeDatagramSocketMetrics peer1Metrics = FakeMetricsBase.getMetrics(peer1);
    FakeDatagramSocketMetrics peer2Metrics = FakeMetricsBase.getMetrics(peer2);
    try {
      CountDownLatch latch = new CountDownLatch(1);
      peer1.handler(packet -> complete());
      peer1.listen(1234, host).onComplete(onSuccess(v -> latch.countDown()));
      awaitLatch(latch);
      peer2.send("hello", 1234, host).onComplete(onSuccess(v -> complete()));
      await();
    } finally {
      peer1.close();
      peer2.close();
    }
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
  }

  @Test
  public void testThreadPoolMetricsWithExecuteBlocking() {
    Map<String, FakePoolMetrics> all = FakePoolMetrics.getMetrics();

    FakePoolMetrics metrics = all.get("vert.x-worker-thread");

    assertThat(metrics.maxSize(), is(getOptions().getInternalBlockingPoolSize()));
    assertThat(metrics.available(), is(getOptions().getWorkerPoolSize()));

    Callable<Void> job = getSomeDumbTask();

    AtomicBoolean hadWaitingQueue = new AtomicBoolean();
    AtomicBoolean hadIdle = new AtomicBoolean();
    AtomicBoolean hadRunning = new AtomicBoolean();
    for (int i = 0; i < 100; i++) {
      vertx.executeBlocking(job).onComplete(
          ar -> {
            if (metrics.pending() > 0) {
              hadWaitingQueue.set(true);
            }
            if (metrics.available() > 0) {
              hadIdle.set(true);
            }
            if (metrics.inUse() > 0) {
              hadRunning.set(true);
            }
          }
      );
    }

    assertWaitUntil(() -> metrics.numberOfEnqueues() == 100);
    assertWaitUntil(() -> metrics.numberOfReleases() == 100);
    assertTrue(hadIdle.get());
    assertTrue(hadWaitingQueue.get());
    assertTrue(hadRunning.get());

    assertEquals(metrics.available(), getOptions().getWorkerPoolSize());
    assertEquals(metrics.inUse(), 0);
    assertEquals(metrics.pending(), 0);
  }

  @Test
  public void testThreadPoolMetricsWithInternalExecuteBlocking() {
    Map<String, FakePoolMetrics> all = FakePoolMetrics.getMetrics();
    FakePoolMetrics metrics = (FakePoolMetrics) all.get("vert.x-internal-blocking");

    assertThat(metrics.maxSize(), is(getOptions().getInternalBlockingPoolSize()));
    assertThat(metrics.available(), is(getOptions().getInternalBlockingPoolSize()));

    int num = VertxOptions.DEFAULT_INTERNAL_BLOCKING_POOL_SIZE;
    int count = num * 5;

    AtomicBoolean hadWaitingQueue = new AtomicBoolean();
    AtomicBoolean hadIdle = new AtomicBoolean();
    AtomicBoolean hadRunning = new AtomicBoolean();

    VertxInternal v = (VertxInternal) vertx;
    Map<Integer, CountDownLatch> latches = new HashMap<>();
    for (int i = 0; i < count; i++) {
      CountDownLatch latch = latches.computeIfAbsent(i / num, k -> new CountDownLatch(num));
      v.executeBlockingInternal(() -> {
        latch.countDown();
        try {
          awaitLatch(latch);
        } catch (InterruptedException e) {
          fail(e);
          Thread.currentThread().interrupt();
        }
        if (metrics.inUse() > 0) {
          hadRunning.set(true);
        }
        if (metrics.pending() > 0) {
          hadWaitingQueue.set(true);
        }
        return null;
      }).onComplete(ar -> {
        if (metrics.available() > 0) {
          hadIdle.set(true);
        }
      });
    }

    assertWaitUntil(() -> metrics.numberOfEnqueues() == 100);
    assertWaitUntil(() -> metrics.numberOfReleases() == 100);
    assertWaitUntil(() -> hadIdle.get());
    assertTrue(hadWaitingQueue.get());
    assertTrue(hadRunning.get());

    assertEquals(metrics.available(), getOptions().getWorkerPoolSize());
    assertEquals(metrics.inUse(), 0);
    assertEquals(metrics.pending(), 0);
  }

  @Test
  public void testThreadPoolMetricsWithWorkerVerticle() throws Exception {
    AtomicInteger counter = new AtomicInteger();
    Map<String, FakePoolMetrics> all = FakePoolMetrics.getMetrics();
    FakePoolMetrics metrics = all.get("vert.x-worker-thread");

    assertThat(metrics.maxSize(), is(getOptions().getInternalBlockingPoolSize()));
    assertThat(metrics.available(), is(getOptions().getWorkerPoolSize()));

    AtomicBoolean hadWaitingQueue = new AtomicBoolean();
    AtomicBoolean hadIdle = new AtomicBoolean();
    AtomicBoolean hadRunning = new AtomicBoolean();

    int count = 100;

    AtomicInteger msg = new AtomicInteger();

    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    ContextInternal ctx = ((VertxInternal) vertx).createWorkerContext();
    ctx.runOnContext(v -> {
      vertx.eventBus().localConsumer("message", d -> {
          msg.incrementAndGet();
          try {
            Thread.sleep(10);

            if (metrics.pending() > 0) {
              hadWaitingQueue.set(true);
            }
            if (metrics.available() > 0) {
              hadIdle.set(true);
            }
            if (metrics.inUse() > 0) {
              hadRunning.set(true);
            }

            if (counter.incrementAndGet() == count) {
              latch2.countDown();
            }

          } catch (InterruptedException e) {
            Thread.currentThread().isInterrupted();
          }
        }
      );
      latch1.countDown();
    });

    awaitLatch(latch1);

    for (int i = 0; i < count; i++) {
      vertx.eventBus().send("message", i);
    }

    awaitLatch(latch2);

    // The verticle deployment is also executed on the worker thread pool
    assertWaitUntil(() -> count + 1 == metrics.numberOfReleases());
    assertEquals(count + 1, metrics.numberOfEnqueues());
    assertEquals(count + 1, metrics.numberOfReleases());
    assertTrue("Had no idle threads", hadIdle.get());
    assertTrue("Had no waiting tasks", hadWaitingQueue.get());
    assertTrue("Had running tasks", hadRunning.get());

    assertEquals(getOptions().getWorkerPoolSize(), metrics.available());
    assertEquals(0, metrics.inUse());
    assertEquals(0, metrics.pending());
  }

  @Test
  public void testThreadPoolMetricsWithNamedExecuteBlocking() throws InterruptedException {
    vertx.close(); // Close the instance automatically created
    vertx = Vertx
      .builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(new FakeMetricsFactory())
      .build();

    WorkerExecutor workerExec = vertx.createSharedWorkerExecutor("my-pool", 10);

    Map<String, FakePoolMetrics> all = FakePoolMetrics.getMetrics();

    FakePoolMetrics metrics = (FakePoolMetrics) all.get("my-pool");

    assertThat(metrics.maxSize(), is(10));
    assertThat(metrics.available(), is(10));

    Callable<Void> job = getSomeDumbTask();

    AtomicBoolean hadWaitingQueue = new AtomicBoolean();
    AtomicBoolean hadIdle = new AtomicBoolean();
    AtomicBoolean hadRunning = new AtomicBoolean();
    for (int i = 0; i < 100; i++) {
      workerExec.executeBlocking(
          job,
          false).onComplete(ar -> {
            if (metrics.pending() > 0) {
              hadWaitingQueue.set(true);
            }
            if (metrics.available() > 0) {
              hadIdle.set(true);
            }
            if (metrics.inUse() > 0) {
              hadRunning.set(true);
            }
          });
    }

    waitUntil(() -> metrics.numberOfEnqueues() == 100 && metrics.numberOfReleases() == 100);
    assertTrue(hadIdle.get());
    assertTrue(hadWaitingQueue.get());
    assertTrue(hadRunning.get());

    assertEquals(metrics.available(), 10);
    assertEquals(metrics.inUse(), 0);
    assertEquals(metrics.pending(), 0);
  }

  @Test
  public void testWorkerPoolClose() {
    WorkerExecutor ex1 = vertx.createSharedWorkerExecutor("ex1");
    WorkerExecutor ex1_ = vertx.createSharedWorkerExecutor("ex1");
    WorkerExecutor ex2 = vertx.createSharedWorkerExecutor("ex2");
    Map<String, FakePoolMetrics> all = FakePoolMetrics.getMetrics();
    FakePoolMetrics metrics1 = all.get("ex1");
    FakePoolMetrics metrics2 = all.get("ex2");
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

  private Callable<Void> getSomeDumbTask() {
    return () -> {
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().isInterrupted();
      }
      return null;
    };
  }

  @Test
  public void testInitialization() {
    assertSame(vertx, ((FakeVertxMetrics)FakeMetricsBase.getMetrics(vertx)).vertx());
    startNodes(1);
    assertSame(vertices[0], ((FakeVertxMetrics)FakeMetricsBase.getMetrics(vertices[0])).vertx());
    EventLoopGroup group = ((VertxInternal)vertx).nettyEventLoopGroup();
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

  @Test
  public void testHTTP2ConnectionCloseBeforePrefaceIsReceived() throws Exception {
    // Let the server close the connection with an idle timeout
    HttpServerOptions options = HttpOptionsFactory
      .createHttp2ServerOptions(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST)
      .setIdleTimeout(1);
    HttpServer server = vertx.createHttpServer(options);
    server.requestHandler(req -> {

    }).listen()
      .await(20, TimeUnit.SECONDS);
    FakeHttpServerMetrics metrics = FakeVertxMetrics.getMetrics(server);
    NetClient client = vertx.createNetClient(new NetClientOptions()
      .setSslEngineOptions(new JdkSSLEngineOptions())
      .setUseAlpn(true)
      .setSsl(true)
      .setHostnameVerificationAlgorithm("HTTPS")
      .setTrustOptions(Trust.SERVER_JKS.get())
      .setApplicationLayerProtocols(Collections.singletonList("h2")));
    CountDownLatch latch = new CountDownLatch(1);
    client.connect(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST).onComplete(onSuccess(so -> {
      assertEquals("h2", so.applicationLayerProtocol());
      so.closeHandler(v -> latch.countDown());
    }));
    awaitLatch(latch);
    assertEquals(0, metrics.connectionCount());
  }

  @Test
  public void testServerLifecycle() {
    AtomicInteger lifecycle = new AtomicInteger();
    vertx = Vertx
      .builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(options -> new VertxMetrics() {
        @Override
        public HttpServerMetrics<?, ?, ?> createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
          lifecycle.compareAndSet(0, 1);
          return new HttpServerMetrics<>() {
            @Override
            public void close() {
              lifecycle.compareAndSet(1, 2);
              HttpServerMetrics.super.close();
            }
          };
        }
      })
      .build();
    vertx.createHttpServer().requestHandler(req -> {}).listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost");
    vertx.close().onComplete(onSuccess(v -> {
      assertEquals(2, lifecycle.get());
      testComplete();
    }));
    await();
  }
}
