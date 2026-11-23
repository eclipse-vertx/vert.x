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
package io.vertx.test.core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.future.FutureImpl;
import io.vertx.core.internal.FutureInternal;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

final class FutureAssertImpl<T> extends FutureImpl<T> implements FutureAssert<T> {

  FutureAssertImpl(Future<T> future) {
    super(future instanceof FutureInternal ? ((FutureInternal<T>) future).context() : null);
    future.onComplete((value, err) -> {
      if (err == null) {
        tryComplete(value);
      } else {
        tryFail(err);
      }
    });
  }

  @Override
  public <U> FutureAssert<U> compose(Function<? super T, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper) {
    return new FutureAssertImpl<>(super.compose(successMapper, failureMapper));
  }

  @Override
  public <U> FutureAssert<U> transform(Function<AsyncResult<T>, Future<U>> mapper) {
    return new FutureAssertImpl<>(super.transform(mapper));
  }

  @Override
  public <U> FutureAssert<T> eventually(Supplier<Future<U>> mapper) {
    return new FutureAssertImpl<>(super.eventually(mapper));
  }

  @Override
  public <U> FutureAssert<U> map(Function<? super T, U> mapper) {
    return new FutureAssertImpl<>(super.map(mapper));
  }

  @Override
  public <V> FutureAssert<V> map(V value) {
    return new FutureAssertImpl<>(super.map(value));
  }

  @Override
  public FutureAssert<T> otherwise(Function<Throwable, T> mapper) {
    return new FutureAssertImpl<>(super.otherwise(mapper));
  }

  @Override
  public FutureAssert<T> otherwise(T value) {
    return new FutureAssertImpl<>(super.otherwise(value));
  }

  @Override
  public FutureAssert<T> expecting(Expectation<? super T> expectation) {
    return new FutureAssertImpl<>(super.expecting(expectation));
  }

  @Override
  public FutureAssert<T> peek(Handler<? super T> handler) {
    return new FutureAssertImpl<>(super.peek(handler));
  }

  @Override
  public FutureAssert<T> timeout(long delay, TimeUnit unit) {
    return new FutureAssertImpl<>(super.timeout(delay, unit));
  }
}
