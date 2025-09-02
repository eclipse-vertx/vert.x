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
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.function.Consumer;

public abstract class HandlerRegistration<T> implements Closeable {

  protected final ContextInternal context;
  protected final EventBusImpl bus;
  protected final String address;
  protected final boolean src;
  private Consumer<Promise<Void>> registered;
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
      doReceive(msg);
    });
  }

  public String address() {
    return address;
  }

  protected abstract void doReceive(Message<T> msg);

  protected abstract void dispatchMessage(Message<T> msg, ContextInternal context, Handler<Message<T>> handler);

  synchronized void register(boolean broadcast, boolean localOnly, Completable<Void> promise) {
    if (registered != null) {
      throw new IllegalStateException();
    }
    registered = bus.addRegistration(address, this, broadcast, localOnly, promise);
    if (bus.metrics != null) {
      metric = bus.metrics.handlerRegistered(address);
    }
  }

  public synchronized boolean isRegistered() {
    return registered != null;
  }

  public Future<Void> unregister() {
    Promise<Void> promise = context.owner().promise();
    synchronized (this) {
      if (registered != null) {
        registered.accept(promise);
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

  void dispatchMessage(Handler<Message<T>> handler, MessageImpl<?, T> message, ContextInternal context) {
    Handler<DeliveryContext<?>>[] interceptors = message.bus.inboundInterceptors();
    if (interceptors.length > 0) {
      Runnable dispatch = () -> dispatch(context, message, handler);
      DeliveryContextImpl<T> deliveryCtx = new DeliveryContextImpl<>(message, interceptors, context, message.receivedBody, dispatch);
      deliveryCtx.next();
    } else {
      dispatch(context, message, handler);
    }
  }

  private void dispatch(ContextInternal ctx, MessageImpl<?, T> message, Handler<Message<T>> handler) {
    Object m = metric;
    VertxTracer tracer = ctx.tracer();
    if (bus.metrics != null) {
      bus.metrics.messageDelivered(m, message.isLocal());
    }
    if (tracer != null && !src) {
      message.trace = tracer.receiveRequest(ctx, SpanKind.RPC, TracingPolicy.PROPAGATE, message, message.isSend() ? "send" : "publish", message.headers(), MessageTagExtractor.INSTANCE);
      dispatchMessage(message, ctx, handler);
      Object trace = message.trace;
      if (message.replyAddress == null && trace != null) {
        tracer.sendResponse(ctx, null, trace, null, TagExtractor.empty());
      }
    } else {
      dispatchMessage(message, ctx, handler);
    }
  }

  void discardMessage(Message<T> msg) {
    if (bus.metrics != null) {
      bus.metrics.discardMessage(metric, ((MessageImpl)msg).isLocal(), msg);
    }

    String replyAddress = msg.replyAddress();
    if (replyAddress != null) {
      msg.reply(new ReplyException(ReplyFailure.TIMEOUT, "Discarded the request. address: " + replyAddress + ", repliedAddress: " + msg.address()));
    }
  }

  @Override
  public void close(Completable<Void> completion) {
    unregister().onComplete(completion);
  }
}
