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

import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.ContextInternal;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

class DeliveryContextImpl<T> implements DeliveryContext<T> {

  private static final AtomicIntegerFieldUpdater<DeliveryContextImpl> UPDATER = AtomicIntegerFieldUpdater.newUpdater(DeliveryContextImpl.class, "interceptorIdx");

  private final MessageImpl<?, T> message;
  private final ContextInternal context;
  private final Object body;
  private final Runnable dispatch;
  private final Handler<DeliveryContext<?>>[] interceptors;
  private volatile int interceptorIdx;

  protected DeliveryContextImpl(MessageImpl<?, T> message, Handler<DeliveryContext<?>>[] interceptors,
                                ContextInternal context, Object body, Runnable dispatch) {
    this.message = message;
    this.interceptors = interceptors;
    this.context = context;
    this.interceptorIdx = 0;
    this.body = body;
    this.dispatch = dispatch;
  }

  @Override
  public Message<T> message() {
    return message;
  }

  @Override
  public boolean send() {
    return message.isSend();
  }

  @Override
  public Object body() {
    return body;
  }

  @Override
  public void next() {
    int idx = UPDATER.getAndIncrement(this);
    if (idx < interceptors.length) {
      Handler<DeliveryContext<?>> interceptor = interceptors[idx];
      if (context.inThread()) {
        context.dispatch(this, interceptor);
      } else {
        try {
          interceptor.handle(this);
        } catch (Throwable t) {
          context.reportException(t);
        }
      }
    } else if (idx == interceptors.length) {
      dispatch.run();
    } else {
      throw new IllegalStateException();
    }
  }
}
