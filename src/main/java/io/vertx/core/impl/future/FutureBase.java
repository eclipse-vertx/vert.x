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

import io.netty.channel.EventLoop;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.OrderedEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.NoStackTraceTimeoutException;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Future base implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class FutureBase<T> implements FutureInternal<T> {

  protected final ContextInternal context;

  /**
   * Create a future that hasn't completed yet
   */
  FutureBase() {
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

  protected final void emitSuccess(T value, Listener<T> listener) {
    if (context != null && !context.isRunningOnContext()) {
      context.execute(() -> {
        ContextInternal prev = context.beginDispatch();
        try {
          listener.onSuccess(value);
        } finally {
          context.endDispatch(prev);
        }
      });
    } else {
      listener.onSuccess(value);
    }
  }

  protected final void emitFailure(Throwable cause, Listener<T> listener) {
    if (context != null && !context.isRunningOnContext()) {
      context.execute(() -> {
        ContextInternal prev = context.beginDispatch();
        try {
          listener.onFailure(cause);
        } finally {
          context.endDispatch(prev);
        }
      });
    } else {
      listener.onFailure(cause);
    }
  }

  @Override
  public <U> Future<U> compose(Function<T, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper) {
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
  public <U> Future<T> eventually(Function<Void, Future<U>> mapper) {
    Objects.requireNonNull(mapper, "No null mapper accepted");
    Eventually<T, U> operation = new Eventually<>(context, mapper);
    addListener(operation);
    return operation;
  }

  @Override
  public <U> Future<U> map(Function<T, U> mapper) {
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
    addListener(new Listener<T>() {
      @Override
      public void onSuccess(T value) {
        if (task.cancel(false)) {
          promise.complete(value);
        }
      }
      @Override
      public void onFailure(Throwable failure) {
        if (task.cancel(false)) {
          promise.fail(failure);
        }
      }
    });
    return promise.future();
  }
}
