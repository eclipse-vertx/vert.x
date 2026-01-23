/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.logic;

import java.util.function.Supplier;

import io.vertx.core.Future;


/**
 * Interface for exclusive execution mechanism using locks.
 * <p>
 * This interface provides methods for executing asynchronous logic exclusively
 * using Vert.x's lock mechanism.
 *
 * @author Sinri Edogawa
 */
interface AsyncLogicLock extends AsyncLogicCore {
  /**
   * Executes an asynchronous logic exclusively under Vert.x's lock mechanism.
   * <p>
   * Attempts to acquire the lock within the specified time limit, runs the
   * asynchronous logic, and releases the lock after execution completes.
   *
   * @param <T>               the return type of the asynchronous logic
   * @param lockName          the lock name
   * @param waitTimeForLock   the maximum lock wait time in milliseconds
   * @param exclusiveSupplier the asynchronous logic that needs to run exclusively
   * @return the result of the asynchronous logic; if lock acquisition fails,
   *         returns a failed future asynchronously
   */
  default <T> Future<T> asyncCallExclusively(String lockName, long waitTimeForLock,
                                             Supplier<Future<T>> exclusiveSupplier) {
    return vertx().sharedData()
    .getLockWithTimeout(lockName, waitTimeForLock)
                       .compose(lock -> Future.succeededFuture()
                                              .compose(v -> exclusiveSupplier.get())
                                              .andThen(ar -> lock.release()));
  }

  /**
   * Executes an asynchronous logic exclusively under Vert.x's lock mechanism.
   * <p>
   * This method has the same logic as
   * {@link AsyncLogicLock#asyncCallExclusively(String, long, Supplier)},
   * with a default lock wait time of 1 second.
   *
   * @param <T>               the return type of the asynchronous logic
   * @param lockName          the lock name
   * @param exclusiveSupplier the asynchronous logic that needs to run exclusively
   * @return the result of the asynchronous logic; if lock acquisition fails,
   *         returns a failed future asynchronously
   */
  default <T> Future<T> asyncCallExclusively(String lockName, Supplier<Future<T>> exclusiveSupplier) {
    return asyncCallExclusively(lockName, 1_000L, exclusiveSupplier);
  }
}
