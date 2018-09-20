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
package io.vertx.core.eventbus;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
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
  }

  private void threadA(AtomicInteger seq) {
    EventBus eventBus = vertx.eventBus();
    int count = 0;
    while (count < NUM_MSG) {
      while (count > seq.get()) {
        Thread.yield();
      }
      count++;
      MessageConsumer<Object> consumer = eventBus.consumer(TEST_ADDR, msg -> { });
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
