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
package io.vertx.core.net.impl.pool;

/**
 * The result of a connect operation.
 */
public class ConnectResult<C> {

  private final C conn;
  private final long concurrency;
  private final long capacity;

  public ConnectResult(C connection, long concurrency, long capacity) {
    this.conn = connection;
    this.concurrency = concurrency;
    this.capacity = capacity;
  }

  /**
   * @return the connection
   */
  public C connection() {
    return conn;
  }

  /**
   * @return the connection concurrency, i.e how many times a connection can be borrowed from a pool
   */
  public long concurrency() {
    return concurrency;
  }

  /**
   * @return the connection weight, i.e how much the connection accounts when it is managed by a pool
   */
  public long weight() {
    return capacity;
  }

  /**
   * @return the connection capacity, i.e how much the connection accounts when it is managed by a pool
   */
  public long capacity() {
    return capacity;
  }
}
