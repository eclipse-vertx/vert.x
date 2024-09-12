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

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;

import java.util.function.Function;

/**
 * Function compose transformation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Composition<T, U> extends Operation<U> implements Completable<T> {

  private final Function<? super T, Future<U>> successMapper;
  private final Function<Throwable, Future<U>> failureMapper;

  Composition(ContextInternal context, Function<? super T, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper) {
    super(context);
    this.successMapper = successMapper;
    this.failureMapper = failureMapper;
  }

  @Override
  public void complete(T result, Throwable failure) {
    FutureBase<U> future;
    try {
      if (failure == null) {
        future = (FutureBase<U>) successMapper.apply(result);
      } else {
        future = (FutureBase<U>) failureMapper.apply(failure);
      }
    } catch (Throwable e) {
      handleInternal(null, e);
      return;
    }
    future.addListener(this::handleInternal);
  }
}
