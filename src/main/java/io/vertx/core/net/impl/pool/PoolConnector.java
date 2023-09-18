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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

/**
 * Defines the interactions with the actual back-end managing connections.
 *
 * @param <C>
 */
public interface PoolConnector<C> {

  /**
   * Connects to a back-end.
   *
   * @param context the context to use for IO
   * @param listener the listener
   * @param handler the callback handler with the result
   */
  void connect(ContextInternal context, Listener listener, Handler<AsyncResult<ConnectResult<C>>> handler);

  /**
   * Checks whether the connection is still valid.
   *
   * <p> The pool calls this method when it checks the validity of a connection. Any connection
   * will be evicted from the pool.
   *
   * @param connection the connection to check
   * @return whether the connection is valid or not
   */
  boolean isValid(C connection);

  /**
   * The contract by which the back-end can signal the pool of connection events.
   *
   * <p> A connection listener is used for each connection in the pool.
   */
  interface Listener {

    /**
     * Signal the connection needs to be remove from the pool.
     */
    void onRemove();

    /**
     * Signals the connection the concurrency changed to the {@code concurrency} value.
     *
     * @param concurrency the concurrency
     */
    void onConcurrencyChange(long concurrency);

  }
}
