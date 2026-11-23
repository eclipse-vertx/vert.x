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
import io.vertx.core.Completable;
import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <p>A {@link Future} describing the expected outcome of another future.</p>
 *
 * <p>Assertions are asynchronous: each one returns a new future that succeeds when the assertion holds, and fails with
 * an {@link AssertionError} otherwise. Nothing is checked until the future completes, the outcome is observed with
 * {@link #verify(Checkpoint)} or {@code await()}.</p>
 *
 * <pre>
 * assertThat(client.connect(options))
 *   .isFailure()
 *   .asInstanceOf(ConnectTimeoutException.class)
 *   .verify(checkpoint);
 * </pre>
 *
 * <p>Every {@link Future} operator returns a {@code FutureAssert}, so assertions can appear anywhere in a chain started
 * with {@link #assertThat(Future)}. A {@code FutureAssert} can be returned from the lambda of any future operator.</p>
 *
 * <p>The returned futures have the context of the asserted future, failures of their handlers are reported on that
 * context.</p>
 */
public interface FutureAssert<T> extends Future<T> {

  /**
   * @param future the future to assert
   * @return a {@code FutureAssert} with the same outcome as {@code future}
   */
  static <T> FutureAssert<T> assertThat(Future<T> future) {
    return new FutureAssertImpl<>(future);
  }

  /**
   * Expect this future to fail.
   *
   * @return a future succeeded with the failure cause, or failed with an {@link AssertionError} when this future succeeds
   */
  default FutureAssert<Throwable> isFailure() {
    return transform(ar -> {
      if (ar.succeeded()) {
        return Future.failedFuture(new AssertionError("Unexpected success: " + ar.result()));
      } else {
        return Future.succeededFuture(ar.cause());
      }
    });
  }

  /**
   * Expect the value to be an instance of {@code type}, and narrow the value to it.
   *
   * @param type the expected type
   * @return a future succeeded with the cast value, or failed with an {@link AssertionError} when the value is not an
   *         instance of {@code type}; a failure of this future is propagated
   */
  default <U> FutureAssert<U> asInstanceOf(Class<U> type) {
    return compose(value -> {
      if (type.isInstance(value)) {
        return Future.succeededFuture(type.cast(value));
      } else {
        return Future.failedFuture(new AssertionError("Expecting " + value + " to be an instance of " + type.getName()
          + (value == null ? "" : " but was instance of " + value.getClass().getName())));
      }
    });
  }

  /**
   * Report the outcome of this future to {@code checkpoint}: a success succeeds it, a failure fails it.
   *
   * @param checkpoint the checkpoint
   */
  default void verify(Checkpoint checkpoint) {
    onComplete(checkpoint);
  }

  @Override
  default FutureAssert<T> onComplete(Handler<AsyncResult<T>> handler) {
    return (FutureAssert<T>) Future.super.onComplete(handler);
  }

  @Override
  default FutureAssert<T> onComplete(Handler<? super T> successHandler, Handler<? super Throwable> failureHandler) {
    return (FutureAssert<T>) Future.super.onComplete(successHandler, failureHandler);
  }

  @Override
  default FutureAssert<T> onSuccess(Handler<? super T> handler) {
    return onComplete(handler, null);
  }

  @Override
  default FutureAssert<T> onFailure(Handler<? super Throwable> handler) {
    return onComplete(null, handler);
  }

  @Override
  default <U> FutureAssert<U> flatMap(Function<? super T, Future<U>> mapper) {
    return compose(mapper);
  }

  @Override
  default <U> FutureAssert<U> compose(Function<? super T, Future<U>> mapper) {
    return compose(mapper, Future::failedFuture);
  }

  @Override
  default FutureAssert<T> recover(Function<Throwable, Future<T>> mapper) {
    return compose(Future::succeededFuture, mapper);
  }

  @Override
  <U> FutureAssert<U> compose(Function<? super T, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper);

  @Override
  <U> FutureAssert<U> transform(Function<AsyncResult<T>, Future<U>> mapper);

  @Override
  default <U> FutureAssert<U> transform(BiFunction<? super T, ? super Throwable, Future<U>> mapper) {
    return (FutureAssert<U>) Future.super.transform(mapper);
  }

  @Override
  <U> FutureAssert<T> eventually(Supplier<Future<U>> mapper);

  @Override
  <U> FutureAssert<U> map(Function<? super T, U> mapper);

  @Override
  <V> FutureAssert<V> map(V value);

  @Override
  default <V> FutureAssert<V> mapEmpty() {
    return (FutureAssert<V>) Future.super.mapEmpty();
  }

  @Override
  FutureAssert<T> otherwise(Function<Throwable, T> mapper);

  @Override
  FutureAssert<T> otherwise(T value);

  @Override
  default FutureAssert<T> otherwiseEmpty() {
    return (FutureAssert<T>) Future.super.otherwiseEmpty();
  }

  @Override
  default FutureAssert<T> andThen(Handler<AsyncResult<T>> handler) {
    return (FutureAssert<T>) Future.super.andThen(handler);
  }

  @Override
  default FutureAssert<T> andThen(Completable<? super T> handler) {
    return (FutureAssert<T>) Future.super.andThen(handler);
  }

  @Override
  FutureAssert<T> expecting(Expectation<? super T> expectation);

  @Override
  FutureAssert<T> peek(Handler<? super T> handler);

  @Override
  FutureAssert<T> timeout(long delay, TimeUnit unit);

  @Override
  default FutureAssert<T> timeout(Duration delay) {
    return timeout(delay.toMillis(), TimeUnit.MILLISECONDS);
  }
}
