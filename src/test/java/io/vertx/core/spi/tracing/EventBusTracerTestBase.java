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
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.impl.ContextInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

public abstract class EventBusTracerTestBase extends VertxTestBase {

  Tracer tracer;
  Vertx vertx1;
  Vertx vertx2;

  @Override
  protected Tracer getTracer() {
    return tracer = new Tracer() {
      @Override
      public Object receiveRequest(Map context, Object request, String operation, Iterable headers, Iterable tags) {
        return tracer.receiveRequest(context, request, operation, headers, tags);
      }
      @Override
      public void sendResponse(Map context, Object response, Object payload, Throwable failure, Iterable tags) {
        tracer.sendResponse(context, response, payload, failure, tags);
      }
      @Override
      public Object sendRequest(Map context, Object request, String operation, BiConsumer headers, Iterable tags) {
        return tracer.sendRequest(context, request, operation, headers, tags);
      }
      @Override
      public void receiveResponse(Map context, Object response, Object payload, Throwable failure, Iterable tags) {
        tracer.receiveResponse(context, response, payload, failure, tags);
      }
    };
  }

  class EventBusTracer implements Tracer<Object, Object> {

    final Object receiveKey = new Object();
    final Object receiveVal = new Object();
    final Object receiveTrace = new Object();
    final Object sendKey = new Object();
    final Object sendVal = new Object();
    final Object sendTrace = new Object();
    final List<String> sendEvents = Collections.synchronizedList(new ArrayList<>());
    final List<String> receiveEvents = Collections.synchronizedList(new ArrayList<>());

    private String bilto(Iterable<Map.Entry<String, String>> tags) {
      for (Map.Entry<String, String> tag : tags) {
        if (tag.getKey().equals("peer.service")) {
          String value = tag.getValue();
          if (value.startsWith("__vertx")) {
            value = "generated";
          }
          return value;
        }
      }
      return null;
    }

    @Override
    public Object receiveRequest(Map<Object, Object> context, Object request, String operation, Iterable<Map.Entry<String, String>> headers, Iterable<Map.Entry<String, String>> tags) {
      context.put(receiveKey, receiveVal);
      Object body = ((Message)request).body();
      receiveEvents.add("receiveRequest[" + bilto(tags) + "]");
      return receiveTrace;
    }

    @Override
    public void sendResponse(Map<Object, Object> context, Object response, Object payload, Throwable failure, Iterable<Map.Entry<String, String>> tags) {
      assertSame(receiveTrace, payload);
      assertSame(receiveVal, context.remove(receiveKey));
      receiveEvents.add("sendResponse[]");
    }

    @Override
    public Object sendRequest(Map<Object, Object> context, Object request, String operation, BiConsumer<String, String> headers, Iterable<Map.Entry<String, String>> tags) {
      assertSame(sendVal, context.get(sendKey));
      sendEvents.add("sendRequest[" + bilto(tags) + "]");
      assertTrue(request instanceof Message<?>);
      return sendTrace;
    }

    @Override
    public void receiveResponse(Map<Object, Object> context, Object response, Object payload, Throwable failure, Iterable<Map.Entry<String, String>> tags) {
      assertSame(sendTrace, payload);
      assertSame(sendVal, context.remove(sendKey));
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
    EventBusTracer ebTracer = new EventBusTracer() {
      @Override
      public void sendResponse(Map<Object, Object> context, Object response, Object payload, Throwable failure, Iterable<Map.Entry<String, String>> tags) {
        super.sendResponse(context, response, payload, failure, tags);
        assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[]"), sendEvents);
        assertEquals(Arrays.asList("receiveRequest[the_address]", "sendResponse[]"), receiveEvents);
        testComplete();
      }
    };
    tracer = ebTracer;
    Context receiveCtx = vertx2.getOrCreateContext();
    CountDownLatch latch = new CountDownLatch(1);
    receiveCtx.runOnContext(v -> {
      vertx2.eventBus().consumer("the_address", msg -> {
        assertNotSame(Vertx.currentContext(), receiveCtx);
        assertSameEventLoop(receiveCtx, Vertx.currentContext());
        assertEquals("msg", msg.body());
      });
      latch.countDown();
    });
    awaitLatch(latch);
    Context sendCtx = vertx1.getOrCreateContext();
    sendCtx.runOnContext(v -> {
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) sendCtx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      vertx1.eventBus().send("the_address", "msg");
    });
    await();
  }

  @Test
  public void testEventBusSendNoConsumer() {
    EventBusTracer ebTracer = new EventBusTracer() {
      @Override
      public void receiveResponse(Map<Object, Object> context, Object response, Object payload, Throwable failure, Iterable<Map.Entry<String, String>> tags) {
        super.receiveResponse(context, response, payload, failure, tags);
        assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[NO_HANDLERS]"), sendEvents);
        assertEquals(Collections.emptyList(), receiveEvents);
        testComplete();
      }
    };
    tracer = ebTracer;
    Context ctx = vertx1.getOrCreateContext();
    ctx.runOnContext(v -> {
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      vertx1.eventBus().send("the_address", "msg");
    });
    await();
  }

  @Test
  public void testEventBusRequestReply() {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    vertx2.eventBus().consumer("the_address", msg -> {
      assertEquals("msg_1", msg.body());
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) vertx.getOrCreateContext()).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      msg.reply("msg_2");
    });
    Context ctx = vertx1.getOrCreateContext();
    ctx.runOnContext(v -> {
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      vertx1.eventBus().send("the_address", "msg_1", onSuccess(reply -> {
        ctx.runOnContext(v2 -> {
          assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[]"), ebTracer.sendEvents);
          assertEquals(Arrays.asList("receiveRequest[the_address]", "sendResponse[]"), ebTracer.receiveEvents);
          testComplete();
        });
      }));
    });
    await();
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
      vertx2.eventBus().send("the_address", "msg", onFailure(failure -> {
        ctx.runOnContext(v2 -> {
          assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[RECIPIENT_FAILURE]"), ebTracer.sendEvents);
          assertEquals(Arrays.asList("receiveRequest[the_address]", "sendResponse[]"), ebTracer.receiveEvents);
          testComplete();
        });
      }));
    });
    await();
  }

  @Test
  public void testEventBusRequestNoConsumer() {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    Context ctx = vertx2.getOrCreateContext();
    ctx.runOnContext(v -> {
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      vertx2.eventBus().send("the_address", "msg", onFailure(failure -> {
        assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[NO_HANDLERS]"), ebTracer.sendEvents);
        assertEquals(Collections.emptyList(), ebTracer.receiveEvents);
        testComplete();
      }));
    });
    await();
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
      vertx2.eventBus().send("the_address", "msg", new DeliveryOptions().setSendTimeout(1), onFailure(failure -> {
        vertx.runOnContext(v2 -> {
          assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[TIMEOUT]"), ebTracer.sendEvents);
          assertEquals(Arrays.asList("receiveRequest[the_address]", "sendResponse[]"), ebTracer.receiveEvents);
          testComplete();
        });
      }));
    });
    await();
  }

  @Test
  public void testEventBusRequestReplyReply() {
    EventBusTracer ebTracer = new EventBusTracer();
    tracer = ebTracer;
    vertx2.eventBus().consumer("the_address", msg -> {
      Context ctx = vertx2.getOrCreateContext();
      assertEquals("msg_1", msg.body());
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) vertx.getOrCreateContext()).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      msg.reply("msg_2", reply -> {
        assertNotSame(ctx, vertx2.getOrCreateContext());
        assertSameEventLoop(ctx, vertx2.getOrCreateContext());
        ctx.runOnContext(v -> {
          assertEquals(Arrays.asList("sendRequest[the_address]", "receiveResponse[]", "sendRequest[generated]", "receiveResponse[]"), ebTracer.sendEvents);
          assertEquals(Arrays.asList("receiveRequest[the_address]", "sendResponse[]", "receiveRequest[generated]", "sendResponse[]"), ebTracer.receiveEvents);
          testComplete();
        });
      });
    });
    Context ctx = vertx1.getOrCreateContext();
    ctx.runOnContext(v -> {
      ConcurrentMap<Object, Object> tracerMap = ((ContextInternal) ctx).localContextData();
      tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
      vertx1.eventBus().send("the_address", "msg_1", onSuccess(reply -> {
        assertSame(Vertx.currentContext(), ctx);
        tracerMap.put(ebTracer.sendKey, ebTracer.sendVal);
        reply.reply("msg_3");
      }));
    });
    await();
  }
}
