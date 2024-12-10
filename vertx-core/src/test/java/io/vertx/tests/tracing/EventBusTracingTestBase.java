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
  public void testEventBusSendPropagate() throws Exception {
    testSend(TracingPolicy.PROPAGATE, true, 2);
  }

  @Test
  public void testEventBusSendIgnore() throws Exception {
    testSend(TracingPolicy.IGNORE, true, 0);
  }

  @Test
  public void testEventBusSendAlways() throws Exception {
    testSend(TracingPolicy.ALWAYS, false, 2);
  }

  private void testSend(TracingPolicy policy, boolean create, int expected) throws Exception {
    AtomicInteger received = new AtomicInteger();
    vertx2.eventBus().consumer("the-address", msg -> {
      received.incrementAndGet();
    });
    Context ctx = vertx1.getOrCreateContext();
    ctx.runOnContext(v -> {
      if (create) {
        tracer.activate(tracer.newTrace());
      }
      vertx1.eventBus().send("the-address", "ping", new DeliveryOptions().setTracingPolicy(policy));
    });
    assertWaitUntil(() -> tracer.getFinishedSpans().size() == expected);
    assertWaitUntil(() -> received.get() == 1);
    List<Span> finishedSpans = tracer.getFinishedSpans();
    assertSingleTrace(finishedSpans);
    finishedSpans.forEach(span -> {
      assertEquals("send", span.operation);
    });
  }

  @Test
  public void testEventBusPublishProgagate() throws Exception {
    testPublish(TracingPolicy.PROPAGATE, true, 3, true);
  }

  @Test
  public void testEventBusPublishIgnore() throws Exception {
    testPublish(TracingPolicy.IGNORE, true, 0, false);
  }

  @Test
  public void testEventBusPublishAlways() throws Exception {
    testPublish(TracingPolicy.ALWAYS, false, 3, true);
  }

  private void testPublish(TracingPolicy policy, boolean create, int expected, boolean singleTrace) throws Exception {
    vertx2.eventBus().consumer("the-address", msg -> {
    });
    vertx2.eventBus().consumer("the-address", msg -> {
    });
    Context ctx = vertx1.getOrCreateContext();
    ctx.runOnContext(v -> {
      if (create) {
        tracer.activate(tracer.newTrace());
      }
      vertx1.eventBus().publish("the-address", "ping", new DeliveryOptions().setTracingPolicy(policy));
    });
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
  public void testEventBusRequestReplyPropagate() throws Exception {
    testRequestReply(TracingPolicy.PROPAGATE, true, false, 2);
  }

  @Test
  public void testEventBusRequestReplyIgnore() throws Exception {
    testRequestReply(TracingPolicy.IGNORE, true, false, 0);
  }

  @Test
  public void testEventBusRequestReplyAlways() throws Exception {
    testRequestReply(TracingPolicy.ALWAYS, false, false, 2);
  }

  @Test
  public void testEventBusRequestReplyFailurePropagate() throws Exception {
    testRequestReply(TracingPolicy.PROPAGATE, true, true, 2);
  }

  @Test
  public void testEventBusRequestReplyFailureIgnore() throws Exception {
    testRequestReply(TracingPolicy.IGNORE, true, true, 0);
  }

  @Test
  public void testEventBusRequestReplyFailureAlways() throws Exception {
    testRequestReply(TracingPolicy.ALWAYS, false, true, 2);
  }

  private void testRequestReply(TracingPolicy policy, boolean create, boolean fail, int expected) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    vertx2.eventBus().consumer("the-address", msg -> {
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
      vertx1.eventBus().request("the-address", "ping", new DeliveryOptions().setTracingPolicy(policy)).onComplete(ar -> {
        assertEquals(fail, ar.failed());
        vertx1.runOnContext(v2 -> latch.countDown()); // make sure span is finished
      });
    });
    awaitLatch(latch);
    List<Span> finishedSpans = tracer.getFinishedSpans();
    assertEquals(expected, finishedSpans.size());
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
