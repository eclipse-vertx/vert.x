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
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.Iterator;

public abstract class HandlerRegistration<T> implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(HandlerRegistration.class);

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
    context.nettyEventLoop().execute(() -> {
      // Need to check handler is still there - the handler might have been removed after the message were sent but
      // before it was received
      if (!doReceive(msg) && bus.metrics != null) {
        bus.metrics.discardMessage(metric, msg.isLocal(), msg);
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
          metric = null;
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
    context.dispatch(deliveryCtx::dispatch);
  }

  void discard(Message<T> msg) {
    if (bus.metrics != null) {
      bus.metrics.discardMessage(metric, ((MessageImpl)msg).isLocal(), msg);
    }
  }

  private class InboundDeliveryContext implements DeliveryContext<T> {

    private final MessageImpl<?, T> message;
    private final Iterator<Handler<DeliveryContext>> iter;
    private final Handler<Message<T>> handler;
    private final ContextInternal context;

    private InboundDeliveryContext(MessageImpl<?, T> message, Handler<Message<T>> handler, ContextInternal context) {
      this.message = message;
      this.handler = handler;
      this.iter = message.bus.receiveInterceptors();
      this.context = context;
    }

    void dispatch() {
      next();
    }

    @Override
    public Message<T> message() {
      return message;
    }

    @Override
    public void next() {
      if (iter.hasNext()) {
        try {
          Handler<DeliveryContext> handler = iter.next();
          if (handler != null) {
            handler.handle(this);
          } else {
            next();
          }
        } catch (Throwable t) {
          log.error("Failure in interceptor", t);
        }
      } else {
        Object m = metric;
        VertxTracer tracer = context.tracer();
        if (bus.metrics != null) {
          bus.metrics.messageDelivered(m, message.isLocal());
        }
        if (tracer != null && !src) {
          message.trace = tracer.receiveRequest(context, SpanKind.RPC, TracingPolicy.PROPAGATE, message, message.isSend() ? "send" : "publish", message.headers(), MessageTagExtractor.INSTANCE);
          HandlerRegistration.this.dispatch(message, context, handler);
          Object trace = message.trace;
          if (message.replyAddress == null && trace != null) {
            tracer.sendResponse(context, null, trace, null, TagExtractor.empty());
          }
        } else {
          HandlerRegistration.this.dispatch(message, context, handler);
        }
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
