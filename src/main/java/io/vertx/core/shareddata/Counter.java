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

package io.vertx.core.shareddata;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.util.Objects;

/**
 * An asynchronous counter that can be used to across the cluster to maintain a consistent count.
 * <p>
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Counter {

  /**
   * Get the current value of the counter
   *
   * @param resultHandler handler which will be passed the value
   */
  default void get(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    get().onComplete(resultHandler);
  }

  /**
   * Same as {@link #get(Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Long> get();

  /**
   * Increment the counter atomically and return the new count
   *
   * @param resultHandler handler which will be passed the value
   */
  default void incrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    incrementAndGet().onComplete(resultHandler);
  }

  /**
   * Same as {@link #incrementAndGet(Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Long> incrementAndGet();

  /**
   * Increment the counter atomically and return the value before the increment.
   *
   * @param resultHandler handler which will be passed the value
   */
  default void getAndIncrement(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    getAndIncrement().onComplete(resultHandler);
  }

  /**
   * Same as {@link #getAndIncrement(Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Long> getAndIncrement();

  /**
   * Decrement the counter atomically and return the new count
   *
   * @param resultHandler handler which will be passed the value
   */
  default void decrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    decrementAndGet().onComplete(resultHandler);
  }

  /**
   * Same as {@link #decrementAndGet(Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Long> decrementAndGet();

  /**
   * Add the value to the counter atomically and return the new count
   *
   * @param value  the value to add
   * @param resultHandler handler which will be passed the value
   */
  default void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    addAndGet(value).onComplete(resultHandler);
  }

  /**
   * Same as {@link #addAndGet(long, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Long> addAndGet(long value);

  /**
   * Add the value to the counter atomically and return the value before the add
   *
   * @param value  the value to add
   * @param resultHandler handler which will be passed the value
   */
  default void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    getAndAdd(value).onComplete(resultHandler);
  }

  /**
   * Same as {@link #getAndAdd(long, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Long> getAndAdd(long value);

  /**
   * Set the counter to the specified value only if the current value is the expectec value. This happens
   * atomically.
   *
   * @param expected  the expected value
   * @param value  the new value
   * @param resultHandler  the handler will be passed true on success
   */
  default void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler) {
    Objects.requireNonNull(resultHandler, "resultHandler");
    compareAndSet(expected, value).onComplete(resultHandler);
  }

  /**
   * Same as {@link #compareAndSet(long, long, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Boolean> compareAndSet(long expected, long value);
}
