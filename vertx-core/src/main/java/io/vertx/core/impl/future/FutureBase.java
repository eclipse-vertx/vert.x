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

import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.OrderedEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.impl.NoStackTraceTimeoutException;
import io.vertx.core.internal.FutureInternal;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Future base implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class FutureBase<T> implements FutureInternal<T> {

  protected final ContextInternal context;

  /**
   * Create a future that hasn't completed yet
   */
  protected FutureBase() {
    this(null);
  }

  /**
   * Create a future that hasn't completed yet
   */
  FutureBase(ContextInternal context) {
    this.context = context;
  }

  public final ContextInternal context() {
    return context;
  }

  protected final void emitResult(T result, Throwable cause, Completable<? super T> listener) {
    if (context != null && !context.isRunningOnContext()) {
      context.execute(() -> {
        ContextInternal prev = context.beginDispatch();
        try {
          listener.complete(result, cause);
        } finally {
          context.endDispatch(prev);
        }
      });
    } else {
      listener.complete(result, cause);
    }
  }

  @Override
  public <U> Future<U> compose(Function<? super T, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper) {
    Objects.requireNonNull(successMapper, "No null success mapper accepted");
    Objects.requireNonNull(failureMapper, "No null failure mapper accepted");
    Composition<T, U> operation = new Composition<>(context, successMapper, failureMapper);
    addListener(operation);
    return operation;
  }

  @Override
  public <U> Future<U> transform(Function<AsyncResult<T>, Future<U>> mapper) {
    Objects.requireNonNull(mapper, "No null mapper accepted");
    Transformation<T, U> operation = new Transformation<>(context, mapper);
    addListener(operation);
    return operation;
  }

  @Override
  public <U> Future<T> eventually(Supplier<Future<U>> supplier) {
    Objects.requireNonNull(supplier, "No null supplier accepted");
    Eventually<T, U> operation = new Eventually<>(context, supplier);
    addListener(operation);
    return operation;
  }

  @Override
  public <U> Future<U> map(Function<? super T, U> mapper) {
    Objects.requireNonNull(mapper, "No null mapper accepted");
    Mapping<T, U> operation = new Mapping<>(context, mapper);
    addListener(operation);
    return operation;
  }

  @Override
  public <V> Future<V> map(V value) {
    FixedMapping<T, V> transformation = new FixedMapping<>(context, value);
    addListener(transformation);
    return transformation;
  }

  @Override
  public Future<T> otherwise(Function<Throwable, T> mapper) {
    Objects.requireNonNull(mapper, "No null mapper accepted");
    Otherwise<T> transformation = new Otherwise<>(context, mapper);
    addListener(transformation);
    return transformation;
  }

  @Override
  public Future<T> otherwise(T value) {
    FixedOtherwise<T> operation = new FixedOtherwise<>(context, value);
    addListener(operation);
    return operation;
  }

  @Override
  public Future<T> expecting(Expectation<? super T> expectation) {
    Expect<T> expect = new Expect<>(context, expectation);
    addListener(expect);
    return expect;
  }

  @Override
  public Future<T> timeout(long delay, TimeUnit unit) {
    if (isComplete()) {
      return this;
    }
    OrderedEventExecutor instance;
    Promise<T> promise;
    if (context != null) {
      instance = context.nettyEventLoop();
      promise = context.promise();
    } else {
      instance = GlobalEventExecutor.INSTANCE;
      promise = Promise.promise();
    }
    ScheduledFuture<?> task = instance.schedule(() -> {
      String msg = "Timeout " + unit.toMillis(delay) + " (ms) fired";
      promise.fail(new NoStackTraceTimeoutException(msg));
    }, delay, unit);
    addListener((value, err) -> {
      if (task.cancel(false)) {
        promise.complete(value, err);
      }
    });
    return promise.future();
  }

  /**
   * Add a listener to the future result.
   *
   * @param listener the listener
   */
  public abstract void addListener(Completable<T> listener);

  /**
   * Remove a listener to the future result.
   *
   * @param listener the listener
   */
  public abstract void removeListener(Completable<T> listener);
}
