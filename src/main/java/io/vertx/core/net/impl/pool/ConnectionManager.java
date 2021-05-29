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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.impl.EventLoopContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The connection manager associates remote hosts with pools, it also tracks all connections so they can be closed
 * when the manager is closed.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ConnectionManager<K, C> {

  private final EndpointProvider<K, C> endpointProvider;
  private final Map<K, Endpoint<C>> endpointMap = new ConcurrentHashMap<>();

  public ConnectionManager(EndpointProvider<K, C> endpointProvider) {
    this.endpointProvider = endpointProvider;
  }

  public void forEach(Consumer<Endpoint<C>> consumer) {
    endpointMap.values().forEach(consumer);
  }

  public void getConnection(EventLoopContext ctx,
                            K key,
                            Handler<AsyncResult<C>> handler) {
    getConnection(ctx, key, 0, handler);
  }

  public void getConnection(EventLoopContext ctx,
                            K key,
                            long timeout,
                            Handler<AsyncResult<C>> handler) {
    Runnable dispose = () -> endpointMap.remove(key);
    while (true) {
      Endpoint<C> endpoint = endpointMap.computeIfAbsent(key, k -> endpointProvider.create(key, ctx, dispose));
      if (endpoint.getConnection(ctx, timeout, handler)) {
        break;
      }
    }
  }

  public void close() {
    for (Endpoint<C> conn : endpointMap.values()) {
      conn.close();
    }
  }
}
