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

import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The connection manager associates remote hosts with pools, it also tracks all connections so they can be closed
 * when the manager is closed.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ConnectionManager<K, C> {

  private final EndpointProvider<K, C> provider;
  private final Map<K, Endpoint<C>> endpointMap = new ConcurrentHashMap<>();

  public ConnectionManager(EndpointProvider<K, C> provider) {
    this.provider = provider;
  }

  public void forEach(Consumer<Endpoint<C>> consumer) {
    endpointMap.values().forEach(consumer);
  }

  public <T> T withEndpoint(K key, Function<Endpoint<C>, Optional<T>> f) {
    Runnable dispose = () -> endpointMap.remove(key);
    while (true) {
      Endpoint<C> endpoint = endpointMap.computeIfAbsent(key, k -> provider.create(key, dispose));
      Optional<T> opt = f.apply(endpoint);
      if (opt.isPresent()) {
        return opt.get();
      }
    }
  }

  public Future<C> getConnection(ContextInternal ctx, K key) {
    return getConnection(ctx, key, 0);
  }

  public Future<C> getConnection(ContextInternal ctx, K key, long timeout) {
    return withEndpoint(key, endpoint -> {
      Future<C> fut = endpoint.getConnection(ctx, timeout);
      return Optional.ofNullable(fut);
    });
  }

  public void close() {
    for (Endpoint<C> endpoint : endpointMap.values()) {
      endpoint.close();
    }
  }
}
