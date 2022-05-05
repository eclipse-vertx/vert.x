/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.eventbus.impl;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

public abstract class HandlerRegistration<T> implements Closeable {

  public final ContextInternal context;
  public final EventBusImpl bus;
  public final String address;
  public final boolean src;
  private HandlerHolder<T> registered;
  private Object metric;

  HandlerRegistration(ContextInternal context,
                      EventBusImpl bus,
                      String address,
                      boolean src) {
    this.context = context;
    this.bus = bus;
    this.src = src;
    this.address = address;
  }

  void receive(MessageImpl msg) {
    if (bus.metrics != null) {
      bus.metrics.scheduleMessage(metric, msg.isLocal());
    }
    context.executor().execute(() -> {
      // Need to check handler is still there - the handler might have been removed after the message were sent but
      // before it was received
      if (!doReceive(msg)) {
        discard(msg);
      }
    });
  }

  protected abstract boolean doReceive(Message<T> msg);

  protected abstract void dispatch(Message<T> msg, ContextInternal context, Handler<Message<T>> handler);

  synchronized void register(String repliedAddress, boolean localOnly, Promise<Void> promise) {
    if (registered != null) {
      throw new IllegalStateException();
    }
    registered = bus.addRegistration(address, this, repliedAddress != null, localOnly, promise);
    if (bus.metrics != null) {
      metric = bus.metrics.handlerRegistered(address, repliedAddress);
    }
  }

  public synchronized boolean isRegistered() {
    return registered != null;
  }

  public Future<Void> unregister() {
    Promise<Void> promise = context.promise();
    synchronized (this) {
      if (registered != null) {
        bus.removeRegistration(registered, promise);
        registered = null;
        if (bus.metrics != null) {
          bus.metrics.handlerUnregistered(metric);
        }
      } else {
        promise.complete();
      }
    }
    return promise.future();
  }

  public void unregister(Handler<AsyncResult<Void>> completionHandler) {
    Future<Void> fut = unregister();
    if (completionHandler != null) {
      fut.onComplete(completionHandler);
    }
  }

  void dispatch(Handler<Message<T>> theHandler, Message<T> message, ContextInternal context) {
    InboundDeliveryContext deliveryCtx = new InboundDeliveryContext((MessageImpl<?, T>) message, theHandler, context);
    deliveryCtx.dispatch();
  }

  void discard(Message<T> msg) {
    if (bus.metrics != null) {
      bus.metrics.discardMessage(metric, ((MessageImpl)msg).isLocal(), msg);
    }

    String replyAddress = msg.replyAddress();
    if (replyAddress != null) {
      msg.reply(new ReplyException(ReplyFailure.TIMEOUT, "Discarded the request. address: " + replyAddress + ", repliedAddress: " + msg.address()));
    }
  }

  private class InboundDeliveryContext extends DeliveryContextBase<T> {

    private final Handler<Message<T>> handler;

    private InboundDeliveryContext(MessageImpl<?, T> message, Handler<Message<T>> handler, ContextInternal context) {
      super(message, message.bus.inboundInterceptors(), context);

      this.handler = handler;
    }

    protected void execute() {
      ContextInternal ctx = InboundDeliveryContext.super.context;
      Object m = metric;
      VertxTracer tracer = ctx.tracer();
      if (bus.metrics != null) {
        bus.metrics.messageDelivered(m, message.isLocal());
      }
      if (tracer != null && !src) {
        message.trace = tracer.receiveRequest(ctx, SpanKind.RPC, TracingPolicy.PROPAGATE, message, message.isSend() ? "send" : "publish", message.headers(), MessageTagExtractor.INSTANCE);
        HandlerRegistration.this.dispatch(message, ctx, handler);
        Object trace = message.trace;
        if (message.replyAddress == null && trace != null) {
          tracer.sendResponse(this.context, null, trace, null, TagExtractor.empty());
        }
      } else {
        HandlerRegistration.this.dispatch(message, ctx, handler);
      }
    }

    @Override
    public boolean send() {
      return message.isSend();
    }

    @Override
    public Object body() {
      return message.receivedBody;
    }
  }

  @Override
  public void close(Promise<Void> completion) {
    unregister(completion);
  }
}
