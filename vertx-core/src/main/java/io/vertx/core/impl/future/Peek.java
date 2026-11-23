/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl.future;

import io.vertx.core.Completable;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;

/**
 * Implements peek.
 */
class Peek<T> extends Operation<T> implements Completable<T> {

  private final Handler<? super T> handler;

  Peek(ContextInternal context, Handler<? super T> handler) {
    super(context);
    this.handler = handler;
  }

  @Override
  public void complete(T result, Throwable failure) {
    if (failure == null) {
      try {
        handler.handle(result);
      } catch (Throwable e) {
        failure = e;
      }
    }
    completeInternal(result, failure);
  }
}
