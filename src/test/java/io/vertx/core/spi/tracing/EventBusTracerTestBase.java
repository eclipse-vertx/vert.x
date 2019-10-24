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
package io.vertx.core.spi.tracing;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.impl.ContextInternal;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

public abstract class EventBusTracerTestBase extends VertxTestBase {

  VertxTracer tracer;
  Vertx vertx1;
  Vertx vertx2;

  @Override
  protected VertxTracer getTracer() {
    return tracer = new VertxTracer() {
      @Override
      public Object receiveRequest(Context context, Object request, String operation, Iterable headers, TagExtractor tagExtractor) {
        return tracer.receiveRequest(context, request, operation, headers, tagExtractor);
      }
      @Override
      public void sendResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        tracer.sendResponse(context, response, payload, failure, tagExtractor);
      }
      @Override
      public Object sendRequest(Context context, Object request, String operation, BiConsumer headers, TagExtractor tagExtractor) {
        return tracer.sendRequest(context, request, operation, headers, tagExtractor);
      }
      @Override
      public void receiveResponse(Context context, Object response, Object payload, Throwable failure, TagExtractor tagExtractor) {
        tracer.receiveResponse(context, response, payload, failure, tagExtractor);
      }
    };
  }

  class EventBusTracer implements VertxTracer<Object, Object> {

    final String receiveKey = TestUtils.randomAlphaString(10);
    final Object receiveVal = new Object();
    final Object receiveTrace = new Object();
    final String sendKey = TestUtils.randomAlphaString(10);
    final Object sendVal = new Object();
    final Object sendTrace = new Object();
    final List<String> sendEvents = new CopyOnWriteArrayList<>();
    final List<String> receiveEvents = new CopyOnWriteArrayList<>();

    private <T> String addressOf(T obj, TagExtractor<T> extractor) {
      int len = extractor.len(obj);
      for (int idx = 0;idx < len;idx++) {
        if (extractor.name(obj, idx).equals("peer.service")) {
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
    public <R> Object receiveRequest(Context context, R request, String operation, Iterable<Map.Entry<String, String>> headers, TagExtractor<R> tagExtractor) {
      context.putLocal(receiveKey, receiveVal);
      Object body = ((Message)request).body();
      receiveEvents.add("receiveRequest[" + addressOf(request, tagExtractor) + "]");
      return receiveTrace;
    }

    @Override
    public <R> void sendResponse(Context context, R response, Object payload, Throwable failure, TagExtractor<R> tagExtractor) {
      assertSame(receiveTrace, payload);
      assertSame(receiveVal, context.getLocal(receiveKey));
      assertTrue(context.removeLocal(receiveKey));
      receiveEvents.add("sendResponse[]");
    }

    @Override
    public <R> Object sendRequest(Context context, R request, String operation, BiConsumer<String, String> headers, TagExtractor<R> tagExtractor) {
      assertSame(sendVal, context.getLocal(sendKey));
      sendEvents.add("sendRequest[" + addressOf(request, tagExtractor) + "]");
      assertTrue(request instanceof Message<?>);
      return sendTrace;
    }

    @Override
    public <R> void receiveResponse(Context context, R response, Object payload, Throwable failure, TagExtractor<R> tagExtractor) {
      assertSame(sendTrace, payload);
      assertSame(sendVal, context.getLocal(sendKey));
      assertTrue(context.removeLocal(sendKey));
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
    vertx2.runOnContext(v -> {
      Context ctx = vertx2.getOrCreateContext();
      vertx2.eventBus().consumer("the_address", msg -> {
        assertNotSame(Vertx.currentContext(), ctx);
        assertSameEventLoop(ctx, Vertx.currentContext());
        assertEquals("msg", msg.body());
      });
      latch.countDown();
    });
    awaitLatch(latch);
    vertx1.runOnContext(v -> {
      Context ctx = vertx1.getOrCreateContext();
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
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
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
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
    vertx2.runOnContext(v -> {
      Context ctx = vertx2.getOrCreateContext();
      vertx2.eventBus().consumer("the_address", msg -> {
        assertNotSame(ctx, vertx2.getOrCreateContext());
        assertSameEventLoop(ctx, vertx2.getOrCreateContext());
        assertEquals("msg_1", msg.body());
        ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) vertx.getOrCreateContext()).localContextData();
        tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
        msg.reply("msg_2");
      });
      latch.countDown();
    });
    awaitLatch(latch);
    vertx1.runOnContext(v -> {
      Context ctx = vertx1.getOrCreateContext();
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      vertx1.eventBus().request("the_address", "msg_1", onSuccess(reply -> {
        assertSame(ctx, vertx1.getOrCreateContext());
        assertSameEventLoop(ctx, vertx1.getOrCreateContext());
      }));
    });
    waitUntil(() -> ebTracer.sendEvents.size() + ebTracer.receiveEvents.size() == 4);
    assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[]"), ebTracer.sendEvents);
    assertEquals(Arrays.asList("receiveRequest[the_address]", "sendResponse[]"), ebTracer.receiveEvents);
  }

  @Test
  public void testEventBusRequestReplyFailure() {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    vertx1.eventBus().consumer("the_address", msg -> {
      assertEquals("msg", msg.body());
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) vertx.getOrCreateContext()).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      msg.fail(10, "it failed");
    });
    Context ctx = vertx2.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      vertx2.eventBus().request("the_address", "msg", onFailure(failure -> {
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
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      vertx2.eventBus().request("the_address", "msg", onFailure(failure -> { }));
    });
    waitUntil(() -> ebTracer.sendEvents.size() + ebTracer.receiveEvents.size() == 2);
    assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[NO_HANDLERS]"), ebTracer.sendEvents);
    assertEquals(Collections.emptyList(), ebTracer.receiveEvents);
  }

  @Test
  public void testEventBusRequestTimeout() {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    vertx1.eventBus().consumer("the_address", msg -> {
      // Let timeout
    });
    Context ctx = vertx2.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      vertx2.eventBus().request("the_address", "msg", new DeliveryOptions().setSendTimeout(100), onFailure(failure -> {
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
    vertx2.runOnContext(v -> {
      Context ctx = vertx2.getOrCreateContext();
      vertx2.eventBus().consumer("the_address", msg -> {
        Context consumerCtx = vertx2.getOrCreateContext();
        assertNotSame(ctx, consumerCtx);
        assertSameEventLoop(ctx, consumerCtx);
        assertEquals("msg_1", msg.body());
        ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) vertx.getOrCreateContext()).localContextData();
        tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
        msg.replyAndRequest("msg_2", reply -> {
          assertSame(consumerCtx, vertx2.getOrCreateContext());
          assertSameEventLoop(consumerCtx, vertx2.getOrCreateContext());
        });
      });
      latch.countDown();
    });
    awaitLatch(latch);
    vertx1.runOnContext(v -> {
      Context ctx = vertx1.getOrCreateContext();
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      vertx1.eventBus().request("the_address", "msg_1", onSuccess(reply -> {
        assertSame(Vertx.currentContext(), ctx);
        tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
        reply.reply("msg_3");
      }));
    });
    waitUntil(() -> ebTracer.sendEvents.size() + ebTracer.receiveEvents.size() == 8);
    assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[]", "sendRequest[generated]", "receiveResponse[]"), ebTracer.sendEvents);
    assertEquals(Arrays.asList("receiveRequest[the_address]", "sendResponse[]", "receiveRequest[generated]", "sendResponse[]"), ebTracer.receiveEvents);
  }
}
