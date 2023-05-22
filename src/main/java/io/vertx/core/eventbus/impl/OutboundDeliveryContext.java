/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
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
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.impl.clustered.ClusteredMessage;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.function.BiConsumer;

public class OutboundDeliveryContext<T> extends DeliveryContextBase<T> implements Promise<Void> {

  public final ContextInternal ctx;
  public final DeliveryOptions options;
  public final ReplyHandler<T> replyHandler;
  public final Promise<Void> writePromise;
  private boolean src;

  EventBusImpl bus;
  EventBusMetrics metrics;

  OutboundDeliveryContext(ContextInternal ctx, MessageImpl message, DeliveryOptions options, ReplyHandler<T> replyHandler) {
    super(message, message.bus.outboundInterceptors(), ctx);
    this.ctx = ctx;
    this.options = options;
    this.replyHandler = replyHandler;
    this.writePromise = ctx.promise();
  }

  @Override
  public boolean tryComplete(Void result) {
    written(null);
    return true;
  }

  @Override
  public boolean tryFail(Throwable cause) {
    written(cause);
    return false;
  }

  @Override
  public Future<Void> future() {
    throw new UnsupportedOperationException();
  }

  private void written(Throwable failure) {

    // Metrics
    if (metrics != null) {
      boolean remote = (message instanceof ClusteredMessage) && ((ClusteredMessage<?, ?>)message).isToWire();
      metrics.messageSent(message.address(), !message.send, !remote, remote);
    }

    // Tracing
    VertxTracer tracer = ctx.tracer();
    if (tracer != null) {
      Object trace = message.trace;
      if (trace != null) {
        if (src) {
          if (replyHandler != null) {
            replyHandler.trace = message.trace;
          } else {
            tracer.receiveResponse(ctx, null, trace, failure, TagExtractor.empty());
          }
        }
      }
    }

    // Fail fast reply handler
    if (failure instanceof ReplyException) {
      if (replyHandler != null) {
        replyHandler.fail((ReplyException) failure);
      }
    }

    // Notify promise finally
    if (writePromise != null) {
      if (failure == null) {
        writePromise.complete();
      } else {
        writePromise.fail(failure);
      }
    }
  }

  @Override
  protected void execute() {
    VertxTracer tracer = ctx.tracer();
    if (tracer != null) {
      if (message.trace == null) {
        src = true;
        BiConsumer<String, String> biConsumer = (String key, String val) -> message.headers().set(key, val);
        TracingPolicy tracingPolicy = options.getTracingPolicy();
        if (tracingPolicy == null) {
          tracingPolicy = TracingPolicy.PROPAGATE;
        }
        message.trace = tracer.sendRequest(ctx, SpanKind.RPC, tracingPolicy, message, message.send ? "send" : "publish", biConsumer, MessageTagExtractor.INSTANCE);
      } else {
        // Handle failure here
        tracer.sendResponse(ctx, null, message.trace, null, TagExtractor.empty());
      }
    }
    bus.sendOrPub(this);
  }

  @Override
  public boolean send() {
    return message.isSend();
  }

  @Override
  public Object body() {
    return message.sentBody;
  }
}
