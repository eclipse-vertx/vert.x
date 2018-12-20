/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.pool;

public class ConnectResult<C> {

  private final C conn;
  private final long concurrency;
  private final long weight;

  public ConnectResult(C connection, long concurrency, long weight) {
    this.conn = connection;
    this.concurrency = concurrency;
    this.weight = weight;
  }

  public C connection() {
    return conn;
  }

  public long concurrency() {
    return concurrency;
  }

  public long weight() {
    return weight;
  }
}
