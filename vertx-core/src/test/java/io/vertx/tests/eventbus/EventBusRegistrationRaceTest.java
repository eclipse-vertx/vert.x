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
package io.vertx.tests.eventbus;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.impl.MessageConsumerImpl;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This test checks race conditions in EventBus registration and unregistration.
 *
 * We use two separate threads to register and unregister consumers on the same address.
 *
 * Thread are coordinated by an volatile sequence and we check at each iteration that Thread-A
 * registration / un-registration of a consumer does not prevent the Thread-B registration
 * to receive the message it sends to its consumer, since both threads will access the same
 * handlers list concurrently.
 */
public class EventBusRegistrationRaceTest extends VertxTestBase {

  private static final int NUM_MSG = 300_000;
  private static String TEST_ADDR = "the-addr";

  private static final Handler<Message<Object>> IGNORE_MSG = msg -> {};

  private final AtomicInteger count = new AtomicInteger();

  @Override
  protected VertxMetricsFactory getMetrics() {
    return o -> new VertxMetrics() {
      @Override
      public EventBusMetrics<Void> createEventBusMetrics() {
        return new EventBusMetrics<>() {
          @Override
          public void scheduleMessage(Void handler, boolean local) {
            count.incrementAndGet();
          }
          @Override
          public void messageDelivered(Void handler, boolean local) {
            count.decrementAndGet();
          }
          @Override
          public void discardMessage(Void handler, boolean local, Message<?> msg) {
            count.decrementAndGet();
          }
        };
      }
    };
  }

  @Test
  public void theTest() throws Exception {
    AtomicInteger seq = new AtomicInteger();
    Thread threadA = new Thread(() -> threadA(seq));
    threadA.setName("Thread-A");
    Thread threadB = new Thread(() -> threadB(seq));
    threadB.setName("Thread-B");
    threadA.start();
    threadB.start();
    threadA.join(20 * 1000);
    threadB.join(20 * 1000);
    assertWaitUntil(() -> count.get() == 0);
  }

  private void threadA(AtomicInteger seq) {
    EventBus eventBus = vertx.eventBus();
    int count = 0;
    while (count < NUM_MSG) {
      while (count > seq.get()) {
        Thread.yield();
      }
      count++;
      MessageConsumerImpl<Object> consumer = (MessageConsumerImpl<Object>) eventBus.consumer(TEST_ADDR, IGNORE_MSG);
      consumer.discardHandler(IGNORE_MSG);
      consumer.unregister();
    }
  }

  private void threadB(AtomicInteger seq) {
    EventBus eventBus = vertx.eventBus();
    MessageConsumer<Object> consumer = null;
    int count = 0;
    while (count < NUM_MSG) {
      while (count > seq.get()) {
        Thread.yield();
      }
      count++;
      if (consumer != null) {
        consumer.unregister();
      }
      consumer = eventBus.consumer(TEST_ADDR);
      consumer.handler(event -> {
        // Missing a message prevents the progression of the test
        seq.incrementAndGet();
      });
      // We use publish because send might deliver the message to a Thread-A's consumer
      // so we are sure we always get a message
      eventBus.publish(TEST_ADDR, count);
    }
  }
}
