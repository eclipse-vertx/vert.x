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
package io.vertx.tests.tracing;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.spi.context.storage.ContextLocal;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.test.core.ContextLocalHelper;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

public abstract class EventBusTracerTestBase extends VertxTestBase {

  ContextLocal<Object> receiveKey;
  ContextLocal<Object> sendKey;
  VertxTracer tracer;
  Vertx vertx1;
  Vertx vertx2;

  @Override
  public void setUp() throws Exception {
    receiveKey = ContextLocal.registerLocal(Object.class);
    sendKey = ContextLocal.registerLocal(Object.class);
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    ContextLocalHelper.reset();
    super.tearDown();
  }

  @Override
  protected VertxTracer getTracer() {
    return tracer = new VertxTracer() {
      @Override
      public Object receiveRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, Iterable headers, TagExtractor tagExtractor) {
        return tracer.receiveRequest(context, kind, policy, request, operation, headers, tagExtractor);
      }
      @Override
      public void sendResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        tracer.sendResponse(context, response, payload, failure, tagExtractor);
      }
      @Override
      public Object sendRequest(Context context, SpanKind kind, TracingPolicy policy, Object request, String operation, BiConsumer headers, TagExtractor tagExtractor) {
        return tracer.sendRequest(context, kind, policy, request, operation, headers, tagExtractor);
      }
      @Override
      public void receiveResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        tracer.receiveResponse(context, response, payload, failure, tagExtractor);
      }
    };
  }

  class EventBusTracer implements VertxTracer<Object, Object> {
    final Object receiveVal = new Object();
    final Object receiveTrace = new Object();
    final Object sendVal = new Object();
    final Object sendTrace = new Object();
    final List<String> sendEvents = new CopyOnWriteArrayList<>();
    final List<String> receiveEvents = new CopyOnWriteArrayList<>();

    private <T> String addressOf(T obj, TagExtractor<T> extractor) {
      int len = extractor.len(obj);
      for (int idx = 0;idx < len;idx++) {
        if (extractor.name(obj, idx).equals("messaging.destination.name")) {
          String value = extractor.value(obj, idx);
          if (value.startsWith("__vertx")) {
            value = "generated";
          }
          return value;
        }
      }
      return null;
    }

    @Override
    public <R> Object receiveRequest(Context context, SpanKind kind, TracingPolicy policy, R request, String operation, Iterable<Map.Entry<String, String>> headers, TagExtractor<R> tagExtractor) {
      receiveKey.put(context, receiveVal);
      Object body = ((Message)request).body();
      receiveEvents.add("receiveRequest[" + addressOf(request, tagExtractor) + "]");
      return receiveTrace;
    }

    @Override
    public <R> void sendResponse(Context context, R response, Object payload, Throwable failure, TagExtractor<R> tagExtractor) {
      assertSame(receiveTrace, payload);
      assertSame(receiveVal, receiveKey.get(context));
      receiveEvents.add("sendResponse[]");
    }

    @Override
    public <R> Object sendRequest(Context context, SpanKind kind, TracingPolicy policy, R request, String operation, BiConsumer<String, String> headers, TagExtractor<R> tagExtractor) {
      assertSame(sendVal, sendKey.get(context));
      sendEvents.add("sendRequest[" + addressOf(request, tagExtractor) + "]");
      assertTrue(request instanceof Message<?>);
      return sendTrace;
    }

    @Override
    public <R> void receiveResponse(Context context, R response, Object payload, Throwable failure, TagExtractor<R> tagExtractor) {
      assertSame(sendTrace, payload);
      assertSame(sendVal, sendKey.get(context));
      if (failure != null) {
        assertTrue(failure instanceof ReplyException);
        ReplyException replyException = (ReplyException) failure;
        sendEvents.add("receiveResponse[" + replyException.failureType() + "]");
      } else {
        Object body = response != null ? ((Message)response).body() : null;
        sendEvents.add("receiveResponse[]");
      }
    }
  }

  @Test
  public void testEventBusSend() throws Exception {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    CountDownLatch latch = new CountDownLatch(1);
    vertx2.runOnContext(v1 -> {
      Context ctx = vertx2.getOrCreateContext();
      vertx2.eventBus().consumer("the_address", msg -> {
        assertNotSame(Vertx.currentContext(), ctx);
        assertSameEventLoop(ctx, Vertx.currentContext());
        assertEquals("msg", msg.body());
      }).completion().onComplete(onSuccess(v2 -> {
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    vertx1.runOnContext(v -> {
      Context ctx = vertx1.getOrCreateContext();
      sendKey.put(ctx, ebTracer.sendVal);
      vertx1.eventBus().send("the_address", "msg");
    });
    waitUntil(() -> ebTracer.sendEvents.size() + ebTracer.receiveEvents.size() == 4);
    assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[]"), ebTracer.sendEvents);
    assertEquals(Arrays.asList("receiveRequest[the_address]", "sendResponse[]"), ebTracer.receiveEvents);
  }

  @Test
  public void testEventBusSendNoConsumer() {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    Context ctx = vertx1.getOrCreateContext();
    ctx.runOnContext(v -> {
      sendKey.put(ctx, ebTracer.sendVal);
      vertx1.eventBus().send("the_address", "msg");
    });
    waitUntil(() -> ebTracer.sendEvents.size() + ebTracer.receiveEvents.size() == 2);
    assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[NO_HANDLERS]"), ebTracer.sendEvents);
    assertEquals(Collections.emptyList(), ebTracer.receiveEvents);
  }

  @Test
  public void testEventBusRequestReply() throws Exception {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    CountDownLatch latch = new CountDownLatch(1);
    vertx2.runOnContext(v1 -> {
      Context ctx = vertx2.getOrCreateContext();
      vertx2.eventBus().consumer("the_address", msg -> {
        assertNotSame(ctx, vertx2.getOrCreateContext());
        assertSameEventLoop(ctx, vertx2.getOrCreateContext());
        assertEquals("msg_1", msg.body());
        sendKey.put(vertx.getOrCreateContext(), ebTracer.sendVal);
        msg.reply("msg_2");
      }).completion().onComplete(onSuccess(v2 -> {
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    vertx1.runOnContext(v -> {
      Context ctx = vertx1.getOrCreateContext();
      sendKey.put(ctx, ebTracer.sendVal);
      vertx1.eventBus().request("the_address", "msg_1").onComplete(onSuccess(reply -> {
        assertSame(ctx, vertx1.getOrCreateContext());
        assertSameEventLoop(ctx, vertx1.getOrCreateContext());
      }));
    });
    waitUntil(() -> ebTracer.sendEvents.size() + ebTracer.receiveEvents.size() == 4);
    assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[]"), ebTracer.sendEvents);
    assertEquals(Arrays.asList("receiveRequest[the_address]", "sendResponse[]"), ebTracer.receiveEvents);
  }

  @Test
  public void testEventBusRequestReplyFailure() throws Exception {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    CountDownLatch latch = new CountDownLatch(1);
    vertx1.eventBus().consumer("the_address", msg -> {
      assertEquals("msg", msg.body());
      sendKey.put(vertx.getOrCreateContext(), ebTracer.sendVal);
      msg.fail(10, "it failed");
    }).completion().onComplete(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    Context ctx = vertx2.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      sendKey.put(ctx, ebTracer.sendVal);
      vertx2.eventBus().request("the_address", "msg").onComplete(onFailure(failure -> {
      }));
    });
    waitUntil(() -> ebTracer.sendEvents.size() + ebTracer.receiveEvents.size() == 4);
    assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[RECIPIENT_FAILURE]"), ebTracer.sendEvents);
    assertEquals(Arrays.asList("receiveRequest[the_address]", "sendResponse[]"), ebTracer.receiveEvents);
  }

  @Test
  public void testEventBusRequestNoConsumer() {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    Context ctx = vertx2.getOrCreateContext();
    ctx.runOnContext(v -> {
      sendKey.put(ctx, ebTracer.sendVal);
      vertx2.eventBus().request("the_address", "msg").onComplete(onFailure(failure -> { }));
    });
    waitUntil(() -> ebTracer.sendEvents.size() + ebTracer.receiveEvents.size() == 2);
    assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[NO_HANDLERS]"), ebTracer.sendEvents);
    assertEquals(Collections.emptyList(), ebTracer.receiveEvents);
  }

  @Test
  public void testEventBusRequestTimeout() throws Exception {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    CountDownLatch latch = new CountDownLatch(1);
    vertx1.eventBus().consumer("the_address", msg -> {
      // Let timeout
    }).completion().onComplete(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    Context ctx = vertx2.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      sendKey.put(ctx, ebTracer.sendVal);
      vertx2.eventBus().request("the_address", "msg", new DeliveryOptions().setSendTimeout(100)).onComplete(onFailure(failure -> {
      }));
    });
    waitUntil(() -> ebTracer.sendEvents.size() + ebTracer.receiveEvents.size() == 3);
    assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[TIMEOUT]"), ebTracer.sendEvents);
    assertEquals(Arrays.asList("receiveRequest[the_address]"), ebTracer.receiveEvents);
  }

  @Test
  public void testEventBusRequestReplyReply() throws Exception {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    CountDownLatch latch = new CountDownLatch(1);
    vertx2.runOnContext(v1 -> {
      Context ctx = vertx2.getOrCreateContext();
      vertx2.eventBus().consumer("the_address", msg -> {
        Context consumerCtx = vertx2.getOrCreateContext();
        assertNotSame(ctx, consumerCtx);
        assertSameEventLoop(ctx, consumerCtx);
        assertEquals("msg_1", msg.body());
        sendKey.put(vertx.getOrCreateContext(), ebTracer.sendVal);
        msg.replyAndRequest("msg_2").onComplete(reply -> {
          assertSame(consumerCtx, vertx2.getOrCreateContext());
          assertSameEventLoop(consumerCtx, vertx2.getOrCreateContext());
        });
      }).completion().onComplete(onSuccess(v2 -> {
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    vertx1.runOnContext(v -> {
      Context ctx = vertx1.getOrCreateContext();
      sendKey.put(ctx, ebTracer.sendVal);
      vertx1.eventBus().request("the_address", "msg_1").onComplete(onSuccess(reply -> {
        assertSame(Vertx.currentContext(), ctx);
        sendKey.put(ctx, ebTracer.sendVal);
        reply.reply("msg_3");
      }));
    });
    waitUntil(() -> ebTracer.sendEvents.size() + ebTracer.receiveEvents.size() == 8);
    assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[]", "sendRequest[generated]", "receiveResponse[]"), ebTracer.sendEvents);
    assertEquals(Arrays.asList("receiveRequest[the_address]", "sendResponse[]", "receiveRequest[generated]", "sendResponse[]"), ebTracer.receiveEvents);
  }
}
