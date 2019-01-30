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
package io.vertx.core.spi.tracing;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.faketracer.FakeTracer;
import io.vertx.test.faketracer.Span;
import org.junit.Test;

import java.util.List;

public abstract class EventBusTracingTestBase extends VertxTestBase {

  Vertx vertx1;
  Vertx vertx2;

  FakeTracer tracer;

  @Override
  protected Tracer getTracer() {
    return tracer = new FakeTracer();
  }

  @Test
  public void testEventBusSend() {
    vertx2.eventBus().consumer("the-address", msg -> {
    });
    Span rootSpan = tracer.newTrace();
    Context ctx = vertx1.getOrCreateContext();
    ctx.runOnContext(v -> {
      tracer.activate(rootSpan);
      vertx1.eventBus().send("the-address", "ping");
    });
    waitUntil(() -> tracer.getFinishedSpans().size() == 2);
    List<Span> finishedSpans = tracer.getFinishedSpans();
    assertSingleTrace(finishedSpans);
  }

  @Test
  public void testEventBusRequestReply() {
    vertx2.eventBus().consumer("the-address", msg -> {
      msg.reply("pong");
    });
    Span rootSpan = tracer.newTrace();
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      tracer.activate(rootSpan);
      vertx1.eventBus().send("the-address", "ping", onSuccess(reply -> {

      }));
    });
    waitUntil(() -> tracer.getFinishedSpans().size() == 2);
    List<Span> finishedSpans = tracer.getFinishedSpans();
    assertSingleTrace(finishedSpans);
  }

  @Test
  public void testEventBusRequestReplyFailure() {
    vertx2.eventBus().consumer("the-address", msg -> {
      msg.fail(10, "it failed");
    });
    Span rootSpan = tracer.newTrace();
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      tracer.activate(rootSpan);
      vertx1.eventBus().send("the-address", "ping", onFailure(err -> {

      }));
    });
    waitUntil(() -> tracer.getFinishedSpans().size() == 2);
    List<Span> finishedSpans = tracer.getFinishedSpans();
    assertSingleTrace(finishedSpans);
  }

  private void assertSingleTrace(List<Span> spans) {
    for (int i = 1; i < spans.size(); i++) {
      assertEquals(spans.get(i - 1).traceId, spans.get(i).traceId);
    }
  }
}
