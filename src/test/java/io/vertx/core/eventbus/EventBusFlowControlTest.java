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

package io.vertx.core.eventbus;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.streams.ReadStream;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBusFlowControlTest extends VertxTestBase {

  protected EventBus eb;

  @Test
  public void testFlowControl() {

    MessageProducer<String> prod = eb.sender("some-address");
    int numBatches = 1000;
    int wqms = 2000;
    prod.setWriteQueueMaxSize(wqms);

    MessageConsumer<String> consumer = eb.consumer("some-address");
    AtomicInteger cnt = new AtomicInteger();
    consumer.handler(msg -> {
      int c = cnt.incrementAndGet();
      if (c == numBatches * wqms) {
        testComplete();
      }
    });
    vertx.runOnContext(v -> {
      sendBatch(prod, wqms, numBatches, 0);
    });
    await();
  }

  @Test
  public void testFlowControlWithOptions() {
    MessageProducer<String> prod = eb.sender("some-address");
    prod.deliveryOptions(new DeliveryOptions().addHeader("foo", "bar"));
    int numBatches = 1000;
    int wqms = 2000;
    prod.setWriteQueueMaxSize(wqms);

    MessageConsumer<String> consumer = eb.consumer("some-address");
    AtomicInteger cnt = new AtomicInteger();
    consumer.handler(msg -> {
      int c = cnt.incrementAndGet();
      if (c == numBatches * wqms) {
        testComplete();
      }
    });

    vertx.runOnContext(v -> {
      sendBatch(prod, wqms, numBatches, 0);
    });
    await();
  }

  private void sendBatch(MessageProducer<String> prod, int batchSize, int numBatches, int batchNumber) {
    while (batchNumber < numBatches) {
      for (int i = 0; i < batchSize; i++) {
        prod.write("message-" + i);
      }
      if (prod.writeQueueFull()) {
        int nextBatch = batchNumber + 1;
        prod.drainHandler(v -> {
          sendBatch(prod, batchSize, numBatches, nextBatch);
        });
        break;
      } else {
        batchNumber++;
      }
    }
  }

  @Test
  public void testDrainHandlerCalledWhenQueueAlreadyDrained() throws Exception {
    MessageConsumer<String> consumer = eb.consumer("some-address");
    consumer.handler(msg -> {
    });
    MessageProducer<String> prod = eb.sender("some-address");
    prod.setWriteQueueMaxSize(1);
    prod.write("msg");
    assertTrue(prod.writeQueueFull());
    waitUntil(() -> !prod.writeQueueFull());
    prod.drainHandler(v -> {
      testComplete();
    });
    await();
  }

  @Test
  public void testFlowControlPauseConsumer() {

    MessageProducer<String> prod = eb.sender("some-address");
    int numBatches = 10;
    int wqms = 100;
    prod.setWriteQueueMaxSize(wqms);

    MessageConsumer<String> consumer = eb.consumer("some-address");
    AtomicInteger cnt = new AtomicInteger();
    AtomicBoolean paused = new AtomicBoolean();
    consumer.handler(msg -> {
      assertFalse(paused.get());
      int c = cnt.incrementAndGet();
      if (c == numBatches * wqms) {
        testComplete();
      }
      if (c % 100 == 0) {
        consumer.pause();
        paused.set(true);
        vertx.setTimer(100, tid -> {
          paused.set(false);
          consumer.resume();
        });
      }
    });

    sendBatch(prod, wqms, numBatches, 0);
    await();
  }

  @Test
  public void testFlowControlNoConsumer() {

    MessageProducer<String> prod = eb.sender("some-address");
    int wqms = 2000;
    prod.setWriteQueueMaxSize(wqms);

    boolean drainHandlerSet = false;
    for (int i = 0; i < wqms * 2; i++) {
      prod.write("message-" + i);
      if (prod.writeQueueFull() && !drainHandlerSet) {
        prod.drainHandler(v -> {
          fail("Should not be called");
        });
        drainHandlerSet = true;
      }
    }
    assertTrue(drainHandlerSet);
    vertx.setTimer(500, tid -> testComplete());
    await();
  }

  @Test
  public void testResumePausedProducer() {
    BlockingQueue<Integer> sequence = new LinkedBlockingQueue<>();
    AtomicReference<Context> handlerContext = new AtomicReference<>();
    MessageConsumer<Integer> consumer = eb.consumer("some-address", msg -> {
      if (sequence.isEmpty()) {
        handlerContext.set(Vertx.currentContext());
      } else {
        assertOnIOContext(handlerContext.get());
      }
      sequence.add(msg.body());
    });
    consumer.pause();
    MessageProducer<Integer> prod = eb.sender("some-address");
    LinkedList<Integer> expected = new LinkedList<>();
    int count = 0;
    while (!prod.writeQueueFull()) {
      int val = count++;
      expected.add(val);
      prod.write(val);
    }
    consumer.resume();
    assertWaitUntil(() -> !prod.writeQueueFull());
    int theCount = count;
    assertWaitUntil(() -> sequence.size() == theCount);
    while (expected.size() > 0) {
      assertEquals(expected.removeFirst(), sequence.poll());
    }
    assertNotNull(handlerContext.get());
  }

  @Test
  public void testMessageConsumerUnregisterThenRegisterAgain() {
    String address = "some-address";
    MessageConsumer<String> consumer = eb.consumer(address);
    ReadStream<String> bodyStream = consumer.bodyStream();
    bodyStream.handler(m1 -> {
      assertEquals("m1", m1);
      consumer.unregister();
      assertFalse("Consumer is not registered", consumer.isRegistered());
      bodyStream.handler(m2 -> {
        assertEquals("m2", m2);
        consumer.unregister();
        assertFalse("Consumer is not registered", consumer.isRegistered());
        testComplete();
      });
      assertTrue("Consumer is registered", consumer.isRegistered());
      eb.send(address, "m2");
    });
    assertTrue("Consumer is registered", consumer.isRegistered());
    eb.send(address, "m1");
    await();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    eb = vertx.eventBus();
  }
}
