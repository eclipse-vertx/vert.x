/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl.pool;

/**
 * The listener definest the contract used by the {@link ConnectionProvider} to interact with the
 * connection pool. Its purpose is also to use a connection implementation without a pool.
 */
public interface ConnectionListener<C> {

  /**
   * Signals the connection the concurrency changed to the {@code concurrency} value.
   *
   * @param concurrency the concurrency
   */
  void onConcurrencyChange(long concurrency);

  /**
   * Recycles the connection.
   *
   * @param expirationTimestamp the expiration timestamp or {@code 0L} to expire immediately
   */
  void onRecycle(long expirationTimestamp);

  /**
   * Discard the connection from the pool, it will now be fully managed by the borrower.
   */
  void onDiscard();

}
