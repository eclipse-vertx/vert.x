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

package io.vertx.core.shareddata.impl;

import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.*;
import static java.util.stream.Collectors.*;

/**
 * @author Thomas Segismont
 */
public class LocalAsyncMapImpl<K, V> implements AsyncMap<K, V> {

  private final VertxInternal vertx;
  private final ConcurrentMap<K, Holder<V>> map;

  public LocalAsyncMapImpl(VertxInternal vertx) {
    this.vertx = vertx;
    map = new ConcurrentHashMap<>();
  }

  @Override
  public Future<V> get(K k) {
    ContextInternal ctx = vertx.getOrCreateContext();
    Holder<V> h = map.get(k);
    if (h != null && h.hasNotExpired()) {
      return ctx.succeededFuture(h.value);
    } else {
      return ctx.succeededFuture();
    }
  }

  @Override
  public Future<Void> put(K k, V v) {
    ContextInternal ctx = vertx.getOrCreateContext();
    Holder<V> previous = map.put(k, new Holder<>(v));
    if (previous != null && previous.expires()) {
      vertx.cancelTimer(previous.timerId);
    }
    return ctx.succeededFuture();
  }

  @Override
  public Future<V> putIfAbsent(K k, V v) {
    ContextInternal ctx = vertx.getOrCreateContext();
    Holder<V> h = map.putIfAbsent(k, new Holder<>(v));
    return ctx.succeededFuture(h == null ? null : h.value);
  }

  @Override
  public Future<Void> put(K k, V v, long ttl) {
    ContextInternal ctx = vertx.getOrCreateContext();
    long timestamp = System.nanoTime();
    long timerId = vertx.setTimer(ttl, l -> removeIfExpired(k));
    Holder<V> previous = map.put(k, new Holder<>(v, timerId, ttl, timestamp));
    if (previous != null && previous.expires()) {
      vertx.cancelTimer(previous.timerId);
    }
    return ctx.succeededFuture();
  }

  private void removeIfExpired(K k) {
    map.computeIfPresent(k, (key, holder) -> holder.hasNotExpired() ? holder : null);
  }

  @Override
  public Future<V> putIfAbsent(K k, V v, long ttl) {
    ContextInternal ctx = vertx.getOrCreateContext();
    long timestamp = System.nanoTime();
    long timerId = vertx.setTimer(ttl, l -> removeIfExpired(k));
    Holder<V> existing = map.putIfAbsent(k, new Holder<>(v, timerId, ttl, timestamp));
    if (existing != null) {
      vertx.cancelTimer(timerId);
      return ctx.succeededFuture(existing.value);
    } else {
      return ctx.succeededFuture();
    }
  }

  @Override
  public Future<Boolean> removeIfPresent(K k, V v) {
    ContextInternal ctx = vertx.getOrCreateContext();
    AtomicBoolean result = new AtomicBoolean();
    map.computeIfPresent(k, (key, holder) -> {
      if (holder.value.equals(v)) {
        result.compareAndSet(false, true);
        if (holder.expires()) {
          vertx.cancelTimer(holder.timerId);
        }
        return null;
      }
      return holder;
    });
    return ctx.succeededFuture(result.get());
  }

  @Override
  public Future<V> replace(K k, V v) {
    ContextInternal ctx = vertx.getOrCreateContext();
    Holder<V> previous = map.replace(k, new Holder<>(v));
    if (previous != null) {
      if (previous.expires()) {
        vertx.cancelTimer(previous.timerId);
      }
      return ctx.succeededFuture(previous.value);
    } else {
      return ctx.succeededFuture();
    }
  }

  @Override
  public Future<V> replace(K k, V v, long ttl) {
    ContextInternal ctx = vertx.getOrCreateContext();
    long timestamp = System.nanoTime();
    long timerId = vertx.setTimer(ttl, l -> removeIfExpired(k));
    Holder<V> previous = map.replace(k, new Holder<>(v, timerId, ttl, timestamp));
    if (previous != null) {
      if (previous.expires()) {
        vertx.cancelTimer(previous.timerId);
      }
      return ctx.succeededFuture(previous.value);
    } else {
      return ctx.succeededFuture();
    }
  }

  @Override
  public Future<Boolean> replaceIfPresent(K k, V oldValue, V newValue) {
    ContextInternal ctx = vertx.getOrCreateContext();
    Holder<V> h = new Holder<>(newValue);
    Holder<V> result = map.computeIfPresent(k, (key, holder) -> {
      if (holder.value.equals(oldValue)) {
        if (holder.expires()) {
          vertx.cancelTimer(holder.timerId);
        }
        return h;
      }
      return holder;
    });
    return ctx.succeededFuture(h == result);
  }

  @Override
  public Future<Boolean> replaceIfPresent(K k, V oldValue, V newValue, long ttl) {
    ContextInternal ctx = vertx.getOrCreateContext();
    long timestamp = System.nanoTime();
    long timerId = vertx.setTimer(ttl, l -> removeIfExpired(k));
    Holder<V> h = new Holder<>(newValue, timerId, ttl, timestamp);
    Holder<V> result = map.computeIfPresent(k, (key, holder) -> {
      if (holder.value.equals(oldValue)) {
        if (holder.expires()) {
          vertx.cancelTimer(holder.timerId);
        }
        return h;
      }
      return holder;
    });
    if(h == result) {
      return ctx.succeededFuture(true);
    } else {
      vertx.cancelTimer(timerId);
      return ctx.succeededFuture(false);
    }
  }

  @Override
  public Future<Void> clear() {
    ContextInternal ctx = vertx.getOrCreateContext();
    map.clear();
    return ctx.succeededFuture();
  }

  @Override
  public Future<Integer> size() {
    ContextInternal ctx = vertx.getOrCreateContext();
    return ctx.succeededFuture(map.size());
  }

  @Override
  public Future<Set<K>> keys() {
    ContextInternal ctx = vertx.getOrCreateContext();
    return ctx.succeededFuture(new HashSet<>(map.keySet()));
  }

  @Override
  public Future<List<V>> values() {
    ContextInternal ctx = vertx.getOrCreateContext();
    List<V> result = map.values().stream()
      .filter(Holder::hasNotExpired)
      .map(h -> h.value)
      .collect(toList());
    return ctx.succeededFuture(result);
  }

  @Override
  public Future<Map<K, V>> entries() {
    ContextInternal ctx = vertx.getOrCreateContext();
    Map<K, V> result = new HashMap<>(map.size());
    map.forEach((key, holder) -> {
      if (holder.hasNotExpired()) {
        result.put(key, holder.value);
      }
    });
    return ctx.succeededFuture(result);
  }

  @Override
  public Future<V> remove(K k) {
    ContextInternal ctx = vertx.getOrCreateContext();
    Holder<V> previous = map.remove(k);
    if (previous != null) {
      if (previous.expires()) {
        vertx.cancelTimer(previous.timerId);
      }
      return ctx.succeededFuture(previous.value);
    } else {
      return ctx.succeededFuture();
    }
  }

  private static class Holder<V> {
    final V value;
    final long timerId;
    final long ttl;
    final long timestamp;

    Holder(V value) {
      Objects.requireNonNull(value);
      this.value = value;
      timestamp = ttl = timerId = 0;
    }

    Holder(V value, long timerId, long ttl, long timestamp) {
      Objects.requireNonNull(value);
      if (ttl < 1) {
        throw new IllegalArgumentException("ttl must be positive: " + ttl);
      }
      this.value = value;
      this.timerId = timerId;
      this.ttl = ttl;
      this.timestamp = timestamp;
    }

    boolean expires() {
      return ttl > 0;
    }

    boolean hasNotExpired() {
      return !expires() || MILLISECONDS.convert(System.nanoTime() - timestamp, NANOSECONDS) < ttl;
    }

    @Override
    public String toString() {
      return "Holder{" + "value=" + value + ", timerId=" + timerId + ", ttl=" + ttl + ", timestamp=" + timestamp + '}';
    }
  }
}
