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

package io.vertx.core.net.impl.endpoint;

import io.vertx.core.Future;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The endpoint manager associates an arbitrary {@code <K>} key with endpoints, it also tracks all endpoints, so they
 * can be closed when the manager is closed.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EndpointManager<K, E extends Endpoint> {

  private static final Consumer<Endpoint> EXPIRED_CHECKER = Endpoint::checkExpired;

  private final Map<K, E> endpointMap = new ConcurrentHashMap<>();
  private final AtomicInteger status = new AtomicInteger();

  public EndpointManager() {
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
  public void forEach(Consumer<Endpoint> consumer) {
    endpointMap.values().forEach(consumer);
  }

  /**
   * Resolve the couple {@code key} as an endpoint, the {@code function} is then applied on this endpoint and the value returned.
   *
   * @param key the endpoint key
   * @param function the function to apply on the endpoint
   * @return the value returned by the function when applied on the resolved endpoint.
   */
  public <T> T withEndpoint(K key, EndpointProvider<K, E> provider, BiFunction<E, Boolean, T> function) {
    checkStatus();
    Endpoint[] ref = new Endpoint[1];
    while (true) {
      ref[0] = null;
      E endpoint = endpointMap.computeIfAbsent(key, k -> {
        E ep = provider.create(key, () -> endpointMap.remove(key, ref[0]));
        ref[0] = ep;
        return ep;
      });
      if (endpoint.before()) {
        T value = function.apply(endpoint, endpoint == ref[0]);
        endpoint.after();
        return value;
      }
    }
  }

  /**
   * Resolve the couple {@code key} as an endpoint, the {@code function} is then applied on this endpoint and the value returned.
   *
   * @param key the endpoint key
   * @param function the function to apply on the endpoint
   * @return the value returned by the function when applied on the resolved endpoint.
   */
  public <T> T withEndpoint2(K key, EndpointProvider<K, E> provider, Predicate<E> checker, BiFunction<E, Boolean, T> function) {
    checkStatus();
    Endpoint[] ref = new Endpoint[1];
    while (true) {
      ref[0] = null;
      E endpoint = endpointMap.compute(key, (k, prev) -> {
        if (prev != null && checker.test(prev)) {
          return prev;
        }
        if (prev != null) {
          // Do we need to do anything else ????
        }
        E ep = provider.create(key, () -> endpointMap.remove(key, ref[0]));
        ref[0] = ep;
        return ep;
      });
      if (endpoint.before()) {
        T value = function.apply(endpoint, endpoint == ref[0]);
        endpoint.after();
        return value;
      }
    }
  }

  /**
   * Get a connection to an endpoint resolved by {@code key}
   *
   * @param key the endpoint key
   * @return the future resolved with the connection
   */
  public <T> Future<T> withEndpointAsync(K key, EndpointProvider<K, E> provider, BiFunction<E, Boolean, Future<T>> function) {
    checkStatus();
    Endpoint[] ref = new Endpoint[1];
    while (true) {
      ref[0] = null;
      E endpoint = endpointMap.computeIfAbsent(key, k -> {
        E ep = provider.create(key, () -> endpointMap.remove(key, ref[0]));
        ref[0] = ep;
        return ep;
      });
      if (endpoint.before()) {
        return function
          .apply(endpoint, endpoint == ref[0])
          .andThen(ar -> {
          endpoint.after();
        });
      }
    }
  }

  private void checkStatus() {
    int st = status.get();
    if (st == 1) {
      throw new IllegalStateException("Pool shutdown");
    } else if (st == 2) {
      throw new IllegalStateException("Pool closed");
    }
  }

  /**
   * Shutdown the connection manager: any new request will be rejected.
   */
  public void shutdown() {
    if (status.compareAndSet(0, 1)) {
      for (Endpoint endpoint : endpointMap.values()) {
        endpoint.shutdown();
      }
      status.set(2);
    }
  }

  /**
   * Close the connection manager, all endpoints are closed forcibly.
   */
  public void close() {
    shutdown();
    if (status.compareAndSet(2, 3)) {
      for (Endpoint endpoint : endpointMap.values()) {
        endpoint.close();
      }
      status.set(4);
    }
  }
}
