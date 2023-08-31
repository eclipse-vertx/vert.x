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
package io.vertx.core.impl.future;

import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Eventually operation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Eventually<T, U> extends Operation<T> implements Listener<T> {

  private final Supplier<Future<U>> supplier;

  Eventually(ContextInternal context, Supplier<Future<U>> supplier) {
    super(context);
    this.supplier = supplier;
  }

  @Override
  public void onSuccess(T value) {
    FutureInternal<U> future;
    try {
      future = (FutureInternal<U>) supplier.get();
    } catch (Throwable e) {
      tryFail(e);
      return;
    }
    future.addListener(new Listener<U>() {
      @Override
      public void onSuccess(U ignore) {
        tryComplete(value);
      }
      @Override
      public void onFailure(Throwable ignore) {
        tryComplete(value);
      }
    });
  }

  @Override
  public void onFailure(Throwable failure) {
    FutureInternal<U> future;
    try {
      future = (FutureInternal<U>) supplier.get();
    } catch (Throwable e) {
      tryFail(e);
      return;
    }
    future.addListener(new Listener<U>() {
      @Override
      public void onSuccess(U ignore) {
        tryFail(failure);
      }
      @Override
      public void onFailure(Throwable ignore) {
        tryFail(failure);
      }
    });
  }
}
