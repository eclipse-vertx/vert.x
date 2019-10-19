/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.eventbus.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.impl.clustered.ClusteredMessage;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.Iterator;

abstract class HandlerRegistration<T> {

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

  final void receive(MessageImpl<?, T> msg) {
    if (bus.metrics != null) {
      bus.metrics.scheduleMessage(metric, msg.isLocal());
    }
    doReceive(msg);
  }

  protected abstract void doReceive(Message<T> msg);

  protected abstract void doUnregister();

  synchronized Future<Void> register(String repliedAddress, boolean localOnly) {
    if (registered != null) {
      throw new IllegalStateException();
    }
    Promise<Void> p = Promise.promise();
    Future<Void> fut = p.future();
    registered = bus.addRegistration(address, this, repliedAddress != null, localOnly, p);
    if (bus.metrics != null) {
      metric = bus.metrics.handlerRegistered(address, repliedAddress);
    }
    return fut;
  }

  public synchronized boolean isRegistered() {
    return registered != null;
  }

  public void unregister(Handler<AsyncResult<Void>> completionHandler) {
    doUnregister();
    synchronized (this) {
      if (registered != null) {
        bus.removeRegistration(registered, completionHandler);
        registered = null;
        if (bus.metrics != null) {
          bus.metrics.handlerUnregistered(metric);
          metric = null;
        }
      } else {
        if (completionHandler != null) {
          context.owner().runOnContext(v -> completionHandler.handle(Future.succeededFuture()));
        }
      }
    }
  }

  void dispatch(Handler<Message<T>> theHandler, Message<T> message, ContextInternal context) {
    InboundDeliveryContext deliveryCtx = new InboundDeliveryContext((MessageImpl<?, T>) message, theHandler, context);
    deliveryCtx.dispatch();
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
      context.dispatch(v -> {
        next();
      });
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
        boolean local = true;
        if (message instanceof ClusteredMessage) {
          // A bit hacky
          ClusteredMessage cmsg = (ClusteredMessage)message;
          if (cmsg.isFromWire()) {
            local = false;
          }
        }
        Object m = metric;
        try {
          if (bus.metrics != null) {
            bus.metrics.beginHandleMessage(m, local);
          }
          VertxTracer tracer = context.tracer();
          if (tracer != null && !src) {
            message.trace = tracer.receiveRequest(context, message, message.isSend() ? "send" : "publish", message.headers, MessageTagExtractor.INSTANCE);
            handler.handle(message);
            if (message.replyAddress == null) {
              tracer.sendResponse(context, null, message.trace, null, TagExtractor.empty());
            }
          } else {
            handler.handle(message);
          }
          if (bus.metrics != null) {
            bus.metrics.endHandleMessage(m, null);
          }
        } catch (Exception e) {
          log.error("Failed to handleMessage. address: " + message.address(), e);
          if (bus.metrics != null) {
            bus.metrics.endHandleMessage(m, e);
          }
          context.reportException(e);
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
}
