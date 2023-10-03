/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.pool;

import io.vertx.core.spi.net.AddressResolver;

/**
 * A connection lookup to the endpoint resolver, provides additional information.
 */
public class ConnectionLookup<C, E, M> {

  private final AddressResolver<?, ?, M, E> resolver;
  private final C connection;
  private final E endpoint;
  private final M metric;

  public ConnectionLookup(C connection, AddressResolver<?, ?, M, E> resolver, E endpoint, M metric) {
    this.connection = connection;
    this.endpoint = endpoint;
    this.resolver = resolver;
    this.metric = metric;
  }

  public C connection() {
    return connection;
  }

  public void reportRequestBegin() {
    resolver.requestBegin(metric);
  }

  public void reportRequestEnd() {
    resolver.requestEnd(metric);
  }

  public void reportResponseBegin() {
    resolver.responseBegin(metric);
  }

  public void reportResponseEnd() {
    resolver.responseEnd(metric);
  }

  public void reportFailure(Throwable error) {
    resolver.requestFailed(metric, error);
  }
}
