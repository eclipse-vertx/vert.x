/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.http.impl.pool;

import io.vertx.core.impl.ContextImpl;

/**
 * The logic for the connection pool.
 */
public interface ConnectionPool<C> {

  int maxSize();

  boolean canBorrow(int connCount);

  C pollConnection();

  /**
   * Determine when a new connection should be created
   *
   * @param connCount the actual connection count including the one being created
   * @return true whether or not a new connection can be created
   */
  boolean canCreateConnection(int connCount);

  void closeAllConnections();

  void initConnection(C conn);

  void recycleConnection(C conn);

  void evictConnection(C conn);

  boolean isValid(C conn);

  ContextImpl getContext(C conn);

}
