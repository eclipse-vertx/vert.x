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
   * @return a future notified with the value
   */
  Future<Long> get();

  /**
   * Increment the counter atomically and return the new count
   *
   * @return a future notified with the value
   */
  Future<Long> incrementAndGet();

  /**
   * Increment the counter atomically and return the value before the increment.
   *
   * @return a future notified with the value
   */
  Future<Long> getAndIncrement();

  /**
   * Decrement the counter atomically and return the new count
   *
   * @return a future notified with the value
   */
  Future<Long> decrementAndGet();

  /**
   * Add the value to the counter atomically and return the new count
   *
   * @param value  the value to add
   * @return a future notified with the value
   */
  Future<Long> addAndGet(long value);

  /**
   * Add the value to the counter atomically and return the value before the add
   *
   * @param value  the value to add
   * @return a future notified with the value
   */
  Future<Long> getAndAdd(long value);

  /**
   * Set the counter to the specified value only if the current value is the expectec value. This happens
   * atomically.
   *
   * @param expected  the expected value
   * @param value  the new value
   * @return a future notified with {@code true} on success
   */
  Future<Boolean> compareAndSet(long expected, long value);
}
