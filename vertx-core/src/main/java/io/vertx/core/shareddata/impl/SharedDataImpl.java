/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.shareddata.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.Arguments;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.shareddata.*;
import io.vertx.core.spi.cluster.ClusterManager;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SharedDataImpl implements SharedData {

  public static final long DEFAULT_LOCK_TIMEOUT = 10 * 1000;

  private final VertxInternal vertx;
  private final ClusterManager clusterManager;
  private final LocalAsyncLocks localAsyncLocks;
  private final ConcurrentMap<String, LocalAsyncMapImpl<?, ?>> localAsyncMaps = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Counter> localCounters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, LocalMap<?, ?>> localMaps = new ConcurrentHashMap<>();

  public SharedDataImpl(VertxInternal vertx, ClusterManager clusterManager) {
    this.vertx = vertx;
    this.clusterManager = clusterManager;
    localAsyncLocks = new LocalAsyncLocks();
  }

  @Override
  public <K, V> Future<AsyncMap<K, V>> getClusterWideMap(String name) {
    Objects.requireNonNull(name, "name");
    if (clusterManager == null) {
      throw new IllegalStateException("Can't get cluster wide map if not clustered");
    }
    Promise<AsyncMap<K, V>> promise = vertx.promise();
    clusterManager.getAsyncMap(name, promise);
    return promise.future().map(WrappedAsyncMap::new);
  }

  @Override
  public <K, V> Future<AsyncMap<K, V>> getAsyncMap(String name) {
    Objects.requireNonNull(name, "name");
    if (clusterManager == null) {
      return getLocalAsyncMap(name);
    } else {
      Promise<AsyncMap<K, V>> promise = vertx.promise();
      clusterManager.getAsyncMap(name, promise);
      return promise.future().map(WrappedAsyncMap::new);
    }
  }

  @Override
  public Future<Lock> getLock(String name) {
    return getLockWithTimeout(name, DEFAULT_LOCK_TIMEOUT);
  }

  @Override
  public Future<Lock> getLockWithTimeout(String name, long timeout) {
    Objects.requireNonNull(name, "name");
    Arguments.require(timeout >= 0, "timeout must be >= 0");
    if (clusterManager == null) {
      return getLocalLockWithTimeout(name, timeout);
    } else {
      Promise<Lock> promise = vertx.promise();
      clusterManager.getLockWithTimeout(name, timeout, promise);
      return promise.future();
    }
  }

  @Override
  public Future<Lock> getLocalLock(String name) {
    return getLocalLockWithTimeout(name, DEFAULT_LOCK_TIMEOUT);
  }

  @Override
  public Future<Lock> getLocalLockWithTimeout(String name, long timeout) {
    Objects.requireNonNull(name, "name");
    Arguments.require(timeout >= 0, "timeout must be >= 0");
    return localAsyncLocks.acquire(vertx.getOrCreateContext(), name, timeout);
  }

  @Override
  public Future<Counter> getCounter(String name) {
    Objects.requireNonNull(name, "name");
    if (clusterManager == null) {
      return getLocalCounter(name);
    } else {
      Promise<Counter> promise = vertx.promise();
      clusterManager.getCounter(name, promise);
      return promise.future();
    }
  }

  /**
   * Return a {@code Map} with the specific {@code name}. All invocations of this method with the same value of {@code name}
   * are guaranteed to return the same {@code Map} instance. <p>
   */
  @SuppressWarnings("unchecked")
  @Override
  public <K, V> LocalMap<K, V> getLocalMap(String name) {
    return (LocalMap<K, V>) localMaps.computeIfAbsent(name, n -> new LocalMapImpl<>(n, localMaps));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K, V> Future<AsyncMap<K, V>> getLocalAsyncMap(String name) {
    LocalAsyncMapImpl<K, V> asyncMap = (LocalAsyncMapImpl<K, V>) localAsyncMaps.computeIfAbsent(name, n -> new LocalAsyncMapImpl<>(vertx));
    ContextInternal context = vertx.getOrCreateContext();
    return context.succeededFuture(new WrappedAsyncMap<>(asyncMap));
  }

  @Override
  public Future<Counter> getLocalCounter(String name) {
    Counter counter = localCounters.computeIfAbsent(name, n -> new AsynchronousCounter(vertx));
    ContextInternal context = vertx.getOrCreateContext();
    return context.succeededFuture(counter);
  }

  private static void checkType(Object obj) {
    if (obj == null) {
      throw new IllegalArgumentException("Cannot put null in key or value of async map");
    }
    // All immutables and byte arrays are Serializable by the platform
    if (!(obj instanceof Serializable || obj instanceof ClusterSerializable)) {
      throw new IllegalArgumentException("Invalid type: " + obj.getClass().getName() + " to put in async map");
    }
  }

  public static final class WrappedAsyncMap<K, V> implements AsyncMap<K, V> {

    private final AsyncMap<K, V> delegate;

    WrappedAsyncMap(AsyncMap<K, V> other) {
      this.delegate = other;
    }

    @Override
    public Future<V> get(K k) {
      checkType(k);
      return delegate.get(k);
    }

    @Override
    public Future<Void> put(K k, V v) {
      checkType(k);
      checkType(v);
      return delegate.put(k, v);
    }

    @Override
    public Future<Void> put(K k, V v, long ttl) {
      checkType(k);
      checkType(v);
      return delegate.put(k, v, ttl);
    }

    @Override
    public Future<V> putIfAbsent(K k, V v) {
      checkType(k);
      checkType(v);
      return delegate.putIfAbsent(k, v);
    }

    @Override
    public Future<V> putIfAbsent(K k, V v, long ttl) {
      checkType(k);
      checkType(v);
      return delegate.putIfAbsent(k, v, ttl);
    }

    @Override
    public Future<V> remove(K k) {
      checkType(k);
      return delegate.remove(k);
    }

    @Override
    public Future<Boolean> removeIfPresent(K k, V v) {
      checkType(k);
      checkType(v);
      return delegate.removeIfPresent(k, v);
    }

    @Override
    public Future<V> replace(K k, V v) {
      checkType(k);
      checkType(v);
      return delegate.replace(k, v);
    }

    @Override
    public Future<V> replace(K k, V v, long ttl) {
      checkType(k);
      checkType(v);
      return delegate.replace(k, v, ttl);
    }

    @Override
    public Future<Boolean> replaceIfPresent(K k, V oldValue, V newValue) {
      checkType(k);
      checkType(oldValue);
      checkType(newValue);
      return delegate.replaceIfPresent(k, oldValue, newValue);
    }

    @Override
    public Future<Boolean> replaceIfPresent(K k, V oldValue, V newValue, long ttl) {
      checkType(k);
      checkType(oldValue);
      checkType(newValue);
      return delegate.replaceIfPresent(k, oldValue, newValue, ttl);
    }

    @Override
    public Future<Void> clear() {
      return delegate.clear();
    }

    @Override
    public Future<Integer> size() {
      return delegate.size();
    }

    @Override
    public Future<Set<K>> keys() {
      return delegate.keys();
    }

    @Override
    public Future<List<V>> values() {
      return delegate.values();
    }

    @Override
    public Future<Map<K, V>> entries() {
      return delegate.entries();
    }

    public AsyncMap<K, V> getDelegate() {
      return delegate;
    }
  }
}
