/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.pool;

/**
 * An executor of tasks acting on a given state in a serial fashion.
 */
public interface Executor<S> {

  @FunctionalInterface
  interface Continuation {

    /**
     * Resume running {@link Action} accumulated to the belonging {@link Executor}.
     *
     * @return {@code not null} requires user to keep on calling it to drain the remaining actions backlog,
     * {@code null} means no additional actions are needed
     */
    Continuation resume();
  }

  /**
   * The action.
   */
  interface Action<S> {

    /**
     * Execute the action, the action should be side effect free and only update the {@code state}.
     *
     * Side effect actions should be executed in the returned post action.
     *
     * @param state the state to update
     * @return the post action to execute or {@code null} if nothing should happen
     */
    Task execute(S state);
  }

  /**
   * Submit an action.
   *
   * @param action the action
   */
  default void submit(Action<S> action) {
    Continuation continuation = submitAndContinue(action);
    while (continuation != null) {
      continuation = continuation.resume();
    }
  }

  /**
   * Submit an action, suspending it if the implementors allow it.
   *
   * @param action the action
   * @return if {@code not null}, {@link Continuation#resume} should be called to let the executor be able
   * to keep on consuming already submitted actions. Otherwise, no further action is required.
   */
  Continuation submitAndContinue(Action<S> action);
}
