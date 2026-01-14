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
  public <T> T withResource(K key, Function<K, R> provider, Function<R, R> checker, BiFunction<R, Boolean, T> function) {
    checkStatus();
    ManagedResource[] ref = new ManagedResource[1];
    while (true) {
      ref[0] = null;
      R entry = resources.compute(key, (k, prev) -> {
        R res;
        if (prev != null) {
          R next = checker.apply(prev);
          if (next == prev) {
            return prev;
          } else if (next == null) {
            res = provider.apply(key);
          } else {
            res = next;
          }
        } else {
          res = provider.apply(key);
        }
        res.cleaner = () -> resources.remove(key, res);
        ref[0] = res;
        return res;
      });
      T value = function.apply(entry, entry == ref[0]);
      if (value != null) {
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
    return withResource(key, provider, Function.identity(), (res, created) -> {
      if (res.before()) {
        return function.apply(res, created).andThen(ar -> res.after());
      }
      return null;
    });
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

  /**
   * @return number of resources held by the manager
   */
  public int size() {
    return resources.size();
  }
}
