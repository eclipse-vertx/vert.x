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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The connection manager associates remote hosts with pools, it also tracks all connections so they can be closed
 * when the manager is closed.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ConnectionManager<K, C> {

  private static final Consumer<Endpoint<?>> EXPIRED_CHECKER = Endpoint::checkExpired;

  private final Map<K, Endpoint<C>> endpointMap = new ConcurrentHashMap<>();

  public ConnectionManager() {
  }

  /**
   * Apply the expiration check on all known endpoints.
   */
  public void checkExpired() {
    forEach((Consumer) EXPIRED_CHECKER);
  }

  /**
   * Visit all known endpoints and apply the {@code consumer}.
   *
   * @param consumer the consumer to apply
   */
  public void forEach(Consumer<Endpoint<C>> consumer) {
    endpointMap.values().forEach(consumer);
  }

  private final AtomicInteger status = new AtomicInteger();

  /**
   * Resolve the couple {@code key} as an endpoint, the {@code function} is then applied on this endpoint and the value returned.
   *
   * @param key the endpoint key
   * @param function the function to apply on the endpoint
   * @return the value returned by the function when applied on the resolved endpoint.
   */
  public <T> T withEndpoint(K key, EndpointProvider<K, C> provider, BiFunction<Endpoint<C>, Boolean, Optional<T>> function) {
    Endpoint<C>[] ref = new Endpoint[1];
    while (true) {
      ref[0] = null;
      Endpoint<C> endpoint = endpointMap.computeIfAbsent(key, k -> {
        Endpoint<C> ep = provider.create(key, () -> endpointMap.remove(key, ref[0]));
        ref[0] = ep;
        return ep;
      });
      Optional<T> opt = function.apply(endpoint, endpoint == ref[0]);
      if (opt.isPresent()) {
        return opt.get();
      }
    }
  }

  /**
   * Get a connection to an endpoint resolved by {@code key}
   *
   * @param ctx the connection context
   * @param key the endpoint key
   * @return the future resolved with the connection
   */
  public Future<C> getConnection(ContextInternal ctx, EndpointProvider<K, C> provider, K key) {
    return getConnection(ctx, key, provider, 0);
  }

  /**
   * Like {@link #getConnection(ContextInternal, Object)} but with an acquisition timeout.
   */
  public Future<C> getConnection(ContextInternal ctx, K key, EndpointProvider<K, C> provider, long timeout) {
    int st = status.get();
    if (st == 1) {
      return ctx.failedFuture("Pool shutdown");
    } else if (st == 2) {
      return ctx.failedFuture("Pool closed");
    }
    return withEndpoint(key, provider, (endpoint, created) -> {
      Future<C> fut = endpoint.getConnection(ctx, timeout);
      return Optional.ofNullable(fut);
    });
  }

  /**
   * Shutdown the connection manager: any new request will be rejected.
   */
  public void shutdown() {
    status.compareAndSet(0, 1);
  }

  /**
   * Close the connection manager, all endpoints are closed forcibly.
   */
  public void close() {
    while (true) {
      int val = status.get();
      if (val > 1) {
        break;
      } else if (status.compareAndSet(val, 2)) {
        for (Endpoint<C> endpoint : endpointMap.values()) {
          endpoint.close();
        }
        break;
      }
    }
  }
}
