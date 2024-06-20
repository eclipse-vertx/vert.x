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
package io.vertx.core.internal.pool;

import io.vertx.core.internal.ContextInternal;

/**
 * A wrapper for the actual connection.
 *
 * @param <C>
 */
public interface PoolConnection<C> {

  /**
   * @return the connection context
   */
  ContextInternal context();

  /**
   * @return the connection
   */
  C get();

  /**
   * @return the number of times this connection has been acquired
   */
  int usage();

  /**
   * @return the number of times this connection can be acquired from the pool, the returned value can be negative
   */
  long available();

  /**
   * @return the total number of times this connection can be acquired
   */
  long concurrency();

}
