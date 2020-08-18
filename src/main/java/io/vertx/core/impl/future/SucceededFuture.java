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
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

import java.util.Objects;
import java.util.function.Function;

/**
 * Succeeded future implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SucceededFuture<T> implements FutureInternal<T> {

  /**
   * Stateless instance of empty results that can be shared safely.
   */
  public static final SucceededFuture EMPTY = new SucceededFuture(null, null);

  private final ContextInternal context;
  private final T result;

  /**
   * Create a future that has already succeeded
   * @param result the result
   */
  public SucceededFuture(T result) {
    this(null, result);
  }

  /**
   * Create a future that has already succeeded
   * @param context the context
   * @param result the result
   */
  public SucceededFuture(ContextInternal context, T result) {
    this.context = context;
    this.result = result;
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public boolean isComplete() {
    return true;
  }

  @Override
  public Future<T> onSuccess(Handler<T> handler) {
    if (context != null) {
      context.emit(result, handler);
    } else {
      handler.handle(result);
    }
    return this;
  }

  @Override
  public Future<T> onFailure(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> handler) {
    if (handler instanceof Listener<?>) {
      Listener<T> listener = (Listener<T>) handler;
      listener.onSuccess(result);
    } else if (context != null) {
      context.emit(this, handler);
    } else {
      handler.handle(this);
    }
    return this;
  }

  @Override
  public void addListener(Listener<T> listener) {
    listener.onSuccess(result);
  }

  @Override
  public T result() {
    return result;
  }

  @Override
  public Throwable cause() {
    return null;
  }

  @Override
  public boolean succeeded() {
    return true;
  }

  @Override
  public boolean failed() {
    return false;
  }

  @Override
  public <U> Future<U> compose(Function<T, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper) {
    Objects.requireNonNull(successMapper, "No null success mapper accepted");
    Objects.requireNonNull(failureMapper, "No null failure mapper accepted");
    Future<U> fut;
    try {
      fut = successMapper.apply(result);
    } catch (Throwable e) {
      return new FailedFuture<U>(context, e);
    }
    return fut;
  }

  @Override
  public <U> Future<U> map(Function<T, U> mapper) {
    Objects.requireNonNull(mapper, "No null mapper accepted");
    U value;
    try {
      value = mapper.apply(result);
    } catch (Throwable e) {
      return new FailedFuture<U>(context, e);
    }
    return new SucceededFuture<>(context, value);
  }

  @Override
  public <V> Future<V> map(V value) {
    return new SucceededFuture<>(context, value);
  }

  @Override
  public Future<T> otherwise(Function<Throwable, T> mapper) {
    Objects.requireNonNull(mapper, "No null mapper accepted");
    return this;
  }

  @Override
  public Future<T> otherwise(T value) {
    return new SucceededFuture<>(context, value);
  }

  @Override
  public String toString() {
    return "Future{result=" + result + "}";
  }
}
