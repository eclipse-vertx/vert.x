/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.tracing;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumerOptions;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.faketracer.FakeTracer;
import io.vertx.test.faketracer.Span;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class EventBusTracingTestBase extends VertxTestBase {

  Vertx vertx1;
  Vertx vertx2;

  FakeTracer tracer;

  @Override
  public void setUp() throws Exception {
    tracer = new FakeTracer();
    super.setUp();
  }

  @Override
  protected VertxTracer getTracer() {
    return tracer;
  }

  @Test
  public void testEventBusSendProducerPropagate() throws Exception {
    testEventBusySendProducerPolicy(TracingPolicy.PROPAGATE, true, 2);
  }

  @Test
  public void testEventBusSendProducerIgnore() throws Exception {
    testEventBusySendProducerPolicy(TracingPolicy.IGNORE, true, 0);
  }

  @Test
  public void testEventBusSendProducerAlways() throws Exception {
    testEventBusySendProducerPolicy(TracingPolicy.ALWAYS, false, 2);
  }

  private void testEventBusySendProducerPolicy(TracingPolicy policy, boolean create, int expected) throws Exception {
    testEventBusySend(policy, TracingPolicy.PROPAGATE, create, expected);
  }

  @Test
  public void testEventBusSendConsumerPropagate() throws Exception {
    testEventBusySendConsumerPolicy(TracingPolicy.PROPAGATE, true, 2);
  }

  @Test
  public void testEventBusSendConsumerIgnore() throws Exception {
    testEventBusySendConsumerPolicy(TracingPolicy.IGNORE, true, 1);
  }

  @Test
  public void testEventBusSendConsumerAlways() throws Exception {
    testEventBusySendConsumerPolicy(TracingPolicy.ALWAYS, false, 1);
  }

  private void testEventBusySendConsumerPolicy(TracingPolicy policy, boolean create, int expected) throws Exception {
    testEventBusySend(TracingPolicy.PROPAGATE, policy, create, expected);
  }

  private void testEventBusySend(TracingPolicy producerPolicy, TracingPolicy consumerPolicy, boolean create, int expected) throws Exception {
    AtomicInteger received = new AtomicInteger();
    MessageConsumerOptions consumerOptions = new MessageConsumerOptions()
      .setAddress("the-address")
      .setTracingPolicy(consumerPolicy);
    vertx2.eventBus().consumer(consumerOptions, msg -> {
      received.incrementAndGet();
    });
    Context ctx = vertx1.getOrCreateContext();
    ctx.runOnContext(v -> {
      if (create) {
        tracer.activate(tracer.newTrace());
      }
      vertx1.eventBus().send("the-address", "ping", new DeliveryOptions().setTracingPolicy(producerPolicy));
    });
    assertWaitUntil(() -> received.get() == 1);
    assertWaitUntil(() -> tracer.getFinishedSpans().size() == expected);
    List<Span> finishedSpans = tracer.getFinishedSpans();
    assertSingleTrace(finishedSpans);
    finishedSpans.forEach(span -> {
      assertEquals("send", span.operation);
    });
  }

  @Test
  public void testEventBusPublishProducerProgagate() throws Exception {
    testEventBusPublishProducerPolicy(TracingPolicy.PROPAGATE, true, 3, true);
  }

  @Test
  public void testEventBusPublishProducerIgnore() throws Exception {
    testEventBusPublishProducerPolicy(TracingPolicy.IGNORE, true, 0, false);
  }

  @Test
  public void testEventBusPublishProducerAlways() throws Exception {
    testEventBusPublishProducerPolicy(TracingPolicy.ALWAYS, false, 3, true);
  }

  private void testEventBusPublishProducerPolicy(TracingPolicy producerPolicy, boolean create, int expected, boolean singleTrace) throws Exception {
    testEventBusPublish(producerPolicy, TracingPolicy.PROPAGATE, create, expected, singleTrace);
  }

  @Test
  public void testEventBusPublishConsumerProgagate() throws Exception {
    testEventBusPublishConsumerPolicy(TracingPolicy.PROPAGATE, true, 3, true);
  }

  @Test
  public void testEventBusPublishConsumerIgnore() throws Exception {
    testEventBusPublishConsumerPolicy(TracingPolicy.IGNORE, true, 1, false);
  }

  @Test
  public void testEventBusPublishConsumerAlways() throws Exception {
    testEventBusPublishConsumerPolicy(TracingPolicy.ALWAYS, false, 2, false);
  }

  private void testEventBusPublishConsumerPolicy(TracingPolicy policy, boolean create, int expected, boolean singleTrace) throws Exception {
    testEventBusPublish(TracingPolicy.PROPAGATE, policy, create, expected, singleTrace);
  }

  private void testEventBusPublish(TracingPolicy producerPolicy, TracingPolicy consumerPolicy, boolean create, int expected, boolean singleTrace) throws Exception {
    MessageConsumerOptions consumerOptions = new MessageConsumerOptions()
      .setAddress("the-address")
      .setTracingPolicy(consumerPolicy);
    AtomicInteger received = new AtomicInteger();
    vertx2.eventBus().consumer(consumerOptions, msg -> {
      received.incrementAndGet();
    });
    vertx2.eventBus().consumer(consumerOptions, msg -> {
      received.incrementAndGet();
    });
    Context ctx = vertx1.getOrCreateContext();
    ctx.runOnContext(v -> {
      if (create) {
        tracer.activate(tracer.newTrace());
      }
      vertx1.eventBus().publish("the-address", "ping", new DeliveryOptions().setTracingPolicy(producerPolicy));
    });
    assertWaitUntil(() -> received.get() == 2);
    assertWaitUntil(() -> tracer.getFinishedSpans().size() == expected);
    List<Span> finishedSpans = tracer.getFinishedSpans();
    if (singleTrace) {
      assertSingleTrace(finishedSpans);
    }
    finishedSpans.forEach(span -> {
      assertEquals("publish", span.operation);
    });
  }

  @Test
  public void testEventBusRequestReplyProducerPropagate() throws Exception {
    testEventRequestReplyProducerPolicy(TracingPolicy.PROPAGATE, true, false, 2);
  }

  @Test
  public void testEventBusRequestReplyProducerIgnore() throws Exception {
    testEventRequestReplyProducerPolicy(TracingPolicy.IGNORE, true, false, 0);
  }

  @Test
  public void testEventBusRequestReplyProducerAlways() throws Exception {
    testEventRequestReplyProducerPolicy(TracingPolicy.ALWAYS, false, false, 2);
  }

  @Test
  public void testEventBusRequestReplyFailureProducerPropagate() throws Exception {
    testEventRequestReplyProducerPolicy(TracingPolicy.PROPAGATE, true, true, 2);
  }

  @Test
  public void testEventBusRequestReplyFailureProducerIgnore() throws Exception {
    testEventRequestReplyProducerPolicy(TracingPolicy.IGNORE, true, true, 0);
  }

  @Test
  public void testEventBusRequestReplyFailureProducerAlways() throws Exception {
    testEventRequestReplyProducerPolicy(TracingPolicy.ALWAYS, false, true, 2);
  }

  private void testEventRequestReplyProducerPolicy(TracingPolicy policy, boolean create, boolean fail, int expected) throws Exception {
    testEventRequestReply(policy, TracingPolicy.PROPAGATE, create, false, expected);
  }

  @Test
  public void testEventBusRequestReplyConsumerPropagate() throws Exception {
    testEventRequestReplyConsumerPolicy(TracingPolicy.PROPAGATE, true, false, 2);
  }

  @Test
  public void testEventBusRequestReplyConsumerIgnore() throws Exception {
    testEventRequestReplyConsumerPolicy(TracingPolicy.IGNORE, true, false, 1);
  }

  @Test
  public void testEventBusRequestReplyConsumerAlways() throws Exception {
    testEventRequestReplyConsumerPolicy(TracingPolicy.ALWAYS, false, false, 1);
  }

  @Test
  public void testEventBusRequestReplyFailureConsumerPropagate() throws Exception {
    testEventRequestReplyConsumerPolicy(TracingPolicy.PROPAGATE, true, true, 2);
  }

  @Test
  public void testEventBusRequestReplyFailureConsumerIgnore() throws Exception {
    testEventRequestReplyConsumerPolicy(TracingPolicy.IGNORE, true, true, 1);
  }

  @Test
  public void testEventBusRequestReplyFailureConsumerAlways() throws Exception {
    testEventRequestReplyConsumerPolicy(TracingPolicy.ALWAYS, false, true, 1);
  }

  private void testEventRequestReplyConsumerPolicy(TracingPolicy policy, boolean create, boolean fail, int expected) throws Exception {
    testEventRequestReply(TracingPolicy.PROPAGATE, policy, create, false, expected);
  }

  private void testEventRequestReply(TracingPolicy producerPolicy, TracingPolicy consumerPolicy, boolean create, boolean fail, int expected) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MessageConsumerOptions consumerOptions = new MessageConsumerOptions()
      .setAddress("the-address")
      .setTracingPolicy(consumerPolicy);
    vertx2.eventBus().consumer(consumerOptions, msg -> {
      if (fail) {
        msg.fail(10, "it failed");
      } else {
        msg.reply("pong");
      }
    });
    Context ctx = vertx1.getOrCreateContext();
    ctx.runOnContext(v -> {
      if (create) {
        tracer.activate(tracer.newTrace());
      }
      vertx1.eventBus().request("the-address", "ping", new DeliveryOptions().setTracingPolicy(producerPolicy)).onComplete(ar -> {
        assertEquals(fail, ar.failed());
        vertx1.runOnContext(v2 -> latch.countDown()); // make sure span is finished
      });
    });
    awaitLatch(latch);
    List<Span> finishedSpans = tracer.getFinishedSpans();
    assertWaitUntil(() -> finishedSpans.size() == expected);
    assertSingleTrace(finishedSpans);
    finishedSpans.forEach(span -> {
      assertEquals("send", span.operation);
      assertEquals("vertx-eventbus", span.getTags().get("messaging.system"));
      assertEquals("send", span.getTags().get("messaging.operation.name"));
    });
  }

  private void assertSingleTrace(List<Span> spans) {
    for (int i = 1; i < spans.size(); i++) {
      assertEquals(spans.get(i - 1).traceId, spans.get(i).traceId);
    }
  }
}
