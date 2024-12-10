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

package io.vertx.core.internal.resource;

import io.vertx.core.Future;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The resource manager associates an arbitrary {@code <K>} key with a reference counted resource, it also tracks all
 * resources so they can be closed when the manager is closed.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ResourceManager<K, R extends ManagedResource> {

  private static final Consumer<ManagedResource> EXPIRED_CHECKER = ManagedResource::checkExpired;

  private final Map<K, R> resources = new ConcurrentHashMap<>();
  private final AtomicInteger status = new AtomicInteger();

  public ResourceManager() {
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
  public void forEach(Consumer<ManagedResource> consumer) {
    resources.values().forEach(consumer);
  }

  /**
   * Resolve the couple {@code key} as an endpoint, the {@code function} is then applied on this endpoint and the value returned.
   *
   * @param key the endpoint key
   * @param function the function to apply on the endpoint
   * @return the value returned by the function when applied on the resolved endpoint.
   */
  public <T> T withResource(K key, Function<K, R> provider, BiFunction<R, Boolean, T> function) {
    checkStatus();
    ManagedResource[] ref = new ManagedResource[1];
    while (true) {
      ref[0] = null;
      R resource = resources.computeIfAbsent(key, k -> {
        R r = provider.apply(key);
        r.cleaner = () -> resources.remove(key, ref[0]);
        ref[0] = r;
        return r;
      });
      if (resource.before()) {
        T value = function.apply(resource, resource == ref[0]);
        resource.after();
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
  public <T> T withResource(K key, Function<K, R> provider, Predicate<R> checker, BiFunction<R, Boolean, T> function) {
    checkStatus();
    ManagedResource[] ref = new ManagedResource[1];
    while (true) {
      ref[0] = null;
      R endpoint = resources.compute(key, (k, prev) -> {
        if (prev != null && checker.test(prev)) {
          return prev;
        }
        if (prev != null) {
          // Do we need to do anything else ????
        }
        R ep = provider.apply(key);
        ep.cleaner = () -> resources.remove(key, ref[0]);
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
   * Get a resource resolved by {@code key}
   *
   * @param key the resource key
   * @return the future resolved with the resource
   */
  public <T> Future<T> withResourceAsync(K key, Function<K, R> provider, BiFunction<R, Boolean, Future<T>> function) {
    checkStatus();
    ManagedResource[] ref = new ManagedResource[1];
    while (true) {
      ref[0] = null;
      R endpoint = resources.computeIfAbsent(key, k -> {
        R ep = provider.apply(key);
        ep.cleaner = () -> resources.remove(key, ref[0]);
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
      throw new IllegalStateException("Resource manager shutdown");
    } else if (st == 2) {
      throw new IllegalStateException("Resource manager closed");
    }
  }

  /**
   * Shutdown the resource manager: any new request will be rejected.
   */
  public void shutdown() {
    if (status.compareAndSet(0, 1)) {
      for (ManagedResource resource : resources.values()) {
        resource.shutdown();
      }
      status.set(2);
    }
  }

  /**
   * Close the resource manager, all resource are closed forcibly.
   */
  public void close() {
    shutdown();
    if (status.compareAndSet(2, 3)) {
      for (ManagedResource resource : resources.values()) {
        resource.close();
      }
      status.set(4);
    }
  }
}
