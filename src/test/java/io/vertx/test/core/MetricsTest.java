/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.test.fakemetrics.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MetricsTest extends VertxTestBase {

  private static final String ADDRESS1 = "some-address1";

  @BeforeClass
  public static void setFactory() {
    ConfigurableMetricsFactory.delegate = new FakeMetricsFactory();
  }

  @AfterClass
  public static void unsetFactory() {
    ConfigurableMetricsFactory.delegate = null;
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.setMetricsOptions(new MetricsOptions().setEnabled(true));
    return options;
  }

  @Test
  public void testSendMessage() {
    testBroadcastMessage(vertx, new Vertx[]{vertx}, false, true, false);
  }

  @Test
  public void testSendMessageInCluster() {
    startNodes(2);
    testBroadcastMessage(vertices[0], new Vertx[]{vertices[1]}, false, false, true);
  }

  @Test
  public void testPublishMessageToSelf() {
    testBroadcastMessage(vertx, new Vertx[]{vertx}, true, true, false);
  }

  @Test
  public void testPublishMessageToRemote() {
    startNodes(2);
    testBroadcastMessage(vertices[0], new Vertx[]{vertices[1]}, true, false, true);
  }

  @Test
  public void testPublishMessageToCluster() {
    startNodes(2);
    testBroadcastMessage(vertices[0], vertices, true, true, true);
  }

  private void testBroadcastMessage(Vertx from, Vertx[] to, boolean publish, boolean expectedLocal, boolean expectedRemote) {
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
          assertEquals(Arrays.asList(new SentMessage(ADDRESS1, publish, expectedLocal, expectedRemote)), eventBusMetrics.getSentMessages());
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
    for (int i = 0;i < expectedHandlers;i++) {
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
    assertEquals(false, registration.replyHandler);
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

  private void testHandlerProcessMessage(Vertx from, Vertx to, int expectedLocalCoult) {
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(to.eventBus());
    to.eventBus().consumer(ADDRESS1, msg -> {
      HandlerMetric registration = assertRegistration(metrics);
      assertEquals(ADDRESS1, registration.address);
      assertEquals(false, registration.replyHandler);
      assertEquals(1, registration.beginCount.get());
      assertEquals(0, registration.endCount.get());
      assertEquals(0, registration.failureCount.get());
      assertEquals(expectedLocalCoult, registration.localCount.get());
      msg.reply("pong");
    }).completionHandler(onSuccess(v -> {
      from.eventBus().send(ADDRESS1, "ping", reply -> {
        HandlerMetric registration = assertRegistration(metrics);
        assertEquals(ADDRESS1, registration.address);
        assertEquals(false, registration.replyHandler);
        assertEquals(1, registration.beginCount.get());
        assertEquals(1, registration.endCount.get());
        assertEquals(0, registration.failureCount.get());
        assertEquals(expectedLocalCoult, registration.localCount.get());
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testHandlerProcessMessageFailure() throws Exception {
    FakeEventBusMetrics metrics = FakeMetricsBase.getMetrics(vertx.eventBus());
    MessageConsumer<Object> consumer = vertx.eventBus().consumer(ADDRESS1, msg -> {
      assertEquals(1, metrics.getReceivedMessages().size());
      HandlerMetric registration = metrics.getRegistrations().get(0);
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
    while (registration.failureCount.get() < 1 && (System.currentTimeMillis() - now ) < 10 * 1000) {
      Thread.sleep(10);
    }
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
      waitUntil(() -> metrics.getRegistrations().size() == 2);
      HandlerMetric registration = metrics.getRegistrations().get(1);
      assertTrue(registration.replyHandler);
      assertEquals(0, registration.beginCount.get());
      assertEquals(0, registration.endCount.get());
      assertEquals(0, registration.localCount.get());
      msg.reply("pong");
    }).completionHandler(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
    vertx.eventBus().send(ADDRESS1, "ping", reply -> {
      assertEquals(ADDRESS1, metrics.getRegistrations().get(0).address);
      HandlerMetric registration = metrics.getRegistrations().get(1);
      assertTrue(registration.replyHandler);
      assertEquals(1, registration.beginCount.get());
      assertEquals(0, registration.endCount.get());
      assertEquals(1, registration.localCount.get());
      vertx.runOnContext(v -> {
        assertEquals(ADDRESS1, metrics.getRegistrations().get(0).address);
        assertTrue(registration.replyHandler);
        assertEquals(1, registration.beginCount.get());
        assertEquals(1, registration.endCount.get());
        assertEquals(1, registration.localCount.get());
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
  public void testServerWebSocket() throws Exception {
    HttpServer server = vertx.createHttpServer();
    server.websocketHandler(ws -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      WebSocketMetric metric = metrics.getMetric(ws);
      assertNotNull(metric);
      assertNotNull(metric.soMetric);
      ws.handler(buffer -> {
        ws.close();
      });
      ws.closeHandler(closed -> {
        assertNull(metrics.getMetric(ws));
        testComplete();
      });
    });
    server.listen(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, ar -> {
      assertTrue(ar.succeeded());
      HttpClient client = vertx.createHttpClient();
      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        ws.write(Buffer.buffer("wibble"));
      });
    });
    await();
  }

  @Test
  public void testWebSocket() throws Exception {
    HttpServer server = vertx.createHttpServer();
    server.websocketHandler(ws -> {
      ws.write(Buffer.buffer("wibble"));
    });
    server.listen(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, ar -> {
      assertTrue(ar.succeeded());
      HttpClient client = vertx.createHttpClient();
      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(client);
        WebSocketMetric metric = metrics.getMetric(ws);
        assertNotNull(metric);
        assertNotNull(metric.soMetric);
        ws.closeHandler(closed -> {
          assertNull(metrics.getMetric(ws));
          testComplete();
        });
        ws.handler(buffer -> {
          ws.close();
        });
      });
    });
    await();
  }

  @Test
  public void testMulti() {
    HttpServer s1 = vertx.createHttpServer();
    s1.requestHandler(req -> {
    });
    s1.listen(8080, ar1 -> {
      assertTrue(ar1.succeeded());
      HttpServer s2 = vertx.createHttpServer();
      s2.requestHandler(req -> {});
      s2.listen(8080, ar2 -> {
        assertTrue(ar2.succeeded());
        FakeHttpServerMetrics metrics1 = FakeMetricsBase.getMetrics(ar1.result());
        assertSame(ar1.result(), metrics1.server);
        FakeHttpServerMetrics metrics2 = FakeMetricsBase.getMetrics(ar2.result());
        assertSame(ar2.result(), metrics2.server);
        testComplete();
      });
    });
    await();
  }
}
