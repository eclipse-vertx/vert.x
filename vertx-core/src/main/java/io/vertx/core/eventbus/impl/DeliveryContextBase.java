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

abstract class DeliveryContextBase<T> implements DeliveryContext<T> {

  public final MessageImpl<?, T> message;
  public final ContextInternal context;

  private final Handler<DeliveryContext>[] interceptors;

  private int interceptorIdx;
  private boolean invoking;
  private boolean invokeNext;

  protected DeliveryContextBase(MessageImpl<?, T> message, Handler<DeliveryContext>[] interceptors, ContextInternal context) {
    this.message = message;
    this.interceptors = interceptors;
    this.context = context;
    this.interceptorIdx = 0;
  }

  void dispatch() {
    this.interceptorIdx = 0;
    if (invoking) {
      this.invokeNext = true;
    } else {
      next();
    }
  }

  @Override
  public Message<T> message() {
    return message;
  }

  protected abstract void execute();


  @Override
  public void next() {
    if (invoking) {
      invokeNext = true;
    } else {
      while (interceptorIdx < interceptors.length) {
        Handler<DeliveryContext> interceptor = interceptors[interceptorIdx];
        invoking = true;
        interceptorIdx++;
        if (context.inThread()) {
          context.dispatch(this, interceptor);
        } else {
          try {
            interceptor.handle(this);
          } catch (Throwable t) {
            context.reportException(t);
          }
        }
        invoking = false;
        if (!invokeNext) {
          return;
        }
        invokeNext = false;
      }
      interceptorIdx = 0;
      execute();
    }
  }
}
