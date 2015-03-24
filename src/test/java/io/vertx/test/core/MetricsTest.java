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
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.test.fakemetrics.FakeEventBusMetrics;
import io.vertx.test.fakemetrics.FakeVertxMetrics;
import io.vertx.test.fakemetrics.HandlerRegistration;
import io.vertx.test.fakemetrics.ReceivedMessage;
import io.vertx.test.fakemetrics.SentMessage;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MetricsTest extends VertxTestBase {

  private static final String ADDRESS1 = "some-address1";

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
    FakeVertxMetrics metrics = FakeVertxMetrics.getMetrics(from);
    FakeEventBusMetrics eventBusMetrics = metrics.getEventBusMetrics();
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
    FakeVertxMetrics metrics = FakeVertxMetrics.getMetrics(to);
    FakeEventBusMetrics eventBusMetrics = metrics.getEventBusMetrics();
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
    FakeVertxMetrics metrics = FakeVertxMetrics.getMetrics(to);
    FakeEventBusMetrics eventBusMetrics = metrics.getEventBusMetrics();
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
    FakeEventBusMetrics fromMetrics = FakeVertxMetrics.getMetrics(from).getEventBusMetrics();
    FakeEventBusMetrics toMetrics = FakeVertxMetrics.getMetrics(to).getEventBusMetrics();
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
  public void testHandlerRegistration() {
    FakeEventBusMetrics metrics = FakeVertxMetrics.getMetrics(vertx).getEventBusMetrics();
    MessageConsumer<Object> consumer = vertx.eventBus().consumer(ADDRESS1, msg -> {});
    assertEquals(1, metrics.getRegistrations().size());
    assertEquals(ADDRESS1, metrics.getRegistrations().get(0).address);
    consumer.unregister();
    assertEquals(0, metrics.getRegistrations().size());
  }

  @Test
  public void testHandlerProcessMessage() {
    FakeEventBusMetrics metrics = FakeVertxMetrics.getMetrics(vertx).getEventBusMetrics();
    vertx.eventBus().consumer(ADDRESS1, msg -> {
      HandlerRegistration registration = metrics.getRegistrations().get(0);
      assertEquals(1, registration.beginCount.get());
      assertEquals(0, registration.endCount.get());
      assertEquals(0, registration.failureCount.get());
      msg.reply("pong");
    });
    vertx.eventBus().send(ADDRESS1, "ping", reply -> {
      HandlerRegistration registration = metrics.getRegistrations().get(0);
      assertEquals(1, registration.beginCount.get());
      assertEquals(1, registration.endCount.get());
      assertEquals(0, registration.failureCount.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testHandlerProcessMessageFailure() throws Exception {
    FakeEventBusMetrics metrics = FakeVertxMetrics.getMetrics(vertx).getEventBusMetrics();
    vertx.eventBus().consumer(ADDRESS1, msg -> {
      HandlerRegistration registration = metrics.getRegistrations().get(0);
      assertEquals(1, registration.beginCount.get());
      assertEquals(0, registration.endCount.get());
      assertEquals(0, registration.failureCount.get());
      throw new RuntimeException();
    });
    vertx.eventBus().send(ADDRESS1, "ping");
    HandlerRegistration registration = metrics.getRegistrations().get(0);
    long now = System.currentTimeMillis();
    while (registration.failureCount.get() < 1 && (System.currentTimeMillis() - now ) < 10 * 1000) {
      Thread.sleep(10);
    }
    assertEquals(1, registration.beginCount.get());
    assertEquals(1, registration.endCount.get());
    assertEquals(1, registration.failureCount.get());
  }
}
