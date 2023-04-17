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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

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

  private final Map<K, Endpoint<C>> endpointMap = new ConcurrentHashMap<>();

  public void forEach(Consumer<Endpoint<C>> consumer) {
    endpointMap.values().forEach(consumer);
  }

  public Future<C> getConnection(ContextInternal ctx,
                            K key,
                            EndpointProvider<C> provider) {
    return getConnection(ctx, key, provider, 0);
  }

  public Future<C> getConnection(ContextInternal ctx,
                                 K key,
                                 EndpointProvider<C> provider,
                                 long timeout) {
    Runnable dispose = () -> endpointMap.remove(key);
    while (true) {
      Endpoint<C> endpoint = endpointMap.computeIfAbsent(key, k -> provider.create(ctx, dispose));
      Future<C> fut = endpoint.getConnection(ctx, timeout);
      if (fut != null) {
        return fut;
      }
    }
  }

  public void close() {
    for (Endpoint<C> endpoint : endpointMap.values()) {
      endpoint.close();
    }
  }
}
