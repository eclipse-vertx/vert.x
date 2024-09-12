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

import io.vertx.core.AsyncResult;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;

import java.util.function.Function;

/**
 * Function compose transformation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Transformation<T, U> extends Operation<U> implements Completable<T> {

  private final Function<AsyncResult<T>, Future<U>> mapper;

  Transformation(ContextInternal context, Function<AsyncResult<T>, Future<U>> mapper) {
    super(context);
    this.mapper = mapper;
  }

  @Override
  public void complete(T result, Throwable failure) {
    Future<T> f = failure == null ? Future.succeededFuture(result) : Future.failedFuture(failure);
    FutureBase<U> future;
    try {
      future = (FutureBase<U>) mapper.apply(f);
    } catch (Throwable e) {
      tryFail(e);
      return;
    }
    future.addListener(this::handleInternal);
  }
}
