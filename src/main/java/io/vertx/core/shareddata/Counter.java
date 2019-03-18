/*
 * Copyright (c) 2014 Red Hat, Inc. and others
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
import io.vertx.core.Promise;

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
  void get(Handler<AsyncResult<Long>> resultHandler);

  /**
   * Same as {@link #get(Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Long> get() {
    Promise<Long> promise = Promise.promise();
    get(promise);
    return promise.future();
  }

  /**
   * Increment the counter atomically and return the new count
   *
   * @param resultHandler handler which will be passed the value
   */
  void incrementAndGet(Handler<AsyncResult<Long>> resultHandler);

  /**
   * Same as {@link #incrementAndGet(Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Long> incrementAndGet() {
    Promise<Long> promise = Promise.promise();
    incrementAndGet(promise);
    return promise.future();
  }

  /**
   * Increment the counter atomically and return the value before the increment.
   *
   * @param resultHandler handler which will be passed the value
   */
  void getAndIncrement(Handler<AsyncResult<Long>> resultHandler);

  /**
   * Same as {@link #getAndIncrement(Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Long> getAndIncrement() {
    Promise<Long> promise = Promise.promise();
    getAndIncrement(promise);
    return promise.future();
  }

  /**
   * Decrement the counter atomically and return the new count
   *
   * @param resultHandler handler which will be passed the value
   */
  void decrementAndGet(Handler<AsyncResult<Long>> resultHandler);

  /**
   * Same as {@link #decrementAndGet(Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Long> decrementAndGet() {
    Promise<Long> promise = Promise.promise();
    decrementAndGet(promise);
    return promise.future();
  }

  /**
   * Add the value to the counter atomically and return the new count
   *
   * @param value  the value to add
   * @param resultHandler handler which will be passed the value
   */
  void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler);

  /**
   * Same as {@link #addAndGet(long, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Long> addAndGet(long value) {
    Promise<Long> promise = Promise.promise();
    addAndGet(value, promise);
    return promise.future();
  }

  /**
   * Add the value to the counter atomically and return the value before the add
   *
   * @param value  the value to add
   * @param resultHandler handler which will be passed the value
   */
  void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler);

  /**
   * Same as {@link #getAndAdd(long, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Long> getAndAdd(long value) {
    Promise<Long> promise = Promise.promise();
    getAndAdd(value, promise);
    return promise.future();
  }

  /**
   * Set the counter to the specified value only if the current value is the expectec value. This happens
   * atomically.
   *
   * @param expected  the expected value
   * @param value  the new value
   * @param resultHandler  the handler will be passed true on success
   */
  void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler);

  /**
   * Same as {@link #compareAndSet(long, long, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Boolean> compareAndSet(long expected, long value) {
    Promise<Boolean> promise = Promise.promise();
    compareAndSet(expected, value, promise);
    return promise.future();
  }
}
