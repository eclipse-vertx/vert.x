/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.future;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;

/**
 * Promise implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class PromiseImpl<T> extends FutureImpl<T> implements PromiseInternal<T>, Listener<T> {

  /**
   * Create a promise that hasn't completed yet
   */
  public PromiseImpl() {
    super();
  }

  /**
   * Create a promise that hasn't completed yet
   */
  public PromiseImpl(ContextInternal context) {
    super(context);
  }

  public void handle(AsyncResult<T> ar) {
    if (ar.succeeded()) {
      onSuccess(ar.result());
    } else {
      onFailure(ar.cause());
    }
  }

  @Override
  public void onSuccess(T value) {
    tryComplete(value);
  }

  @Override
  public void onFailure(Throwable failure) {
    tryFail(failure);
  }

  @Override
  public Future<T> future() {
    return this;
  }

  @Override
  public void operationComplete(io.netty.util.concurrent.Future<T> future) {
    if (future.isSuccess()) {
      complete(future.getNow());
    } else {
      fail(future.cause());
    }
  }
}
