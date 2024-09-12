/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core;

import io.vertx.core.impl.NoStackTraceThrowable;

/**
 * A view of something that can be completed with a success or failure.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@FunctionalInterface
public interface Completable<T> {

  /**
   * Set the result. The instance will be marked as succeeded and completed.
   * <p/>
   *
   * @param result  the result
   * @throws IllegalStateException when this instance is already completed or failed
   */
  default void succeed(T result) {
    complete(result, null);
  }

  /**
   * Shortcut for {@code succeed(null)}
   *
   * @throws IllegalStateException when this instance is already completed or failed
   */
  default void succeed() {
    complete(null, null);
  }

  /**
   * Set the failure. This instance will be marked as failed and completed.
   *
   * @param failure  the failure
   * @throws IllegalStateException when this instance is already completed or failed
   */
  default void fail(Throwable failure) {
    complete(null, failure);
  }

  /**
   * Calls {@link #fail(Throwable)} with the {@code message}.
   *
   * @param message  the failure message
   * @throws IllegalStateException when this instance is already completed or failed
   */
  default void fail(String message) {
    complete(null, new NoStackTraceThrowable(message));
  }

  /**
   *  Complete this instance
   *
   *  <ul>
   *      <li>when {@code failure} is {@code null}, a success is signaled</li>
   *      <li>otherwise a failure is signaled</li>
   *  </ul>
   *
   * @param result the result
   * @param failure the failure
   * @throws IllegalStateException when this instance is already completed
   */
  void complete(T result, Throwable failure);

}
