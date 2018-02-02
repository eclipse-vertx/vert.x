/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.shareddata.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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

  private final Vertx vertx;
  private final ConcurrentMap<K, Holder<V>> map;

  public LocalAsyncMapImpl(Vertx vertx) {
    this.vertx = vertx;
    map = new ConcurrentHashMap<>();
  }

  @Override
  public void get(final K k, Handler<AsyncResult<V>> resultHandler) {
    Holder<V> h = map.get(k);
    if (h != null && h.hasNotExpired()) {
      resultHandler.handle(Future.succeededFuture(h.value));
    } else {
      resultHandler.handle(Future.succeededFuture());
    }
  }

  @Override
  public void put(final K k, final V v, Handler<AsyncResult<Void>> resultHandler) {
    Holder<V> previous = map.put(k, new Holder<>(v));
    if (previous != null && previous.expires()) {
      vertx.cancelTimer(previous.timerId);
    }
    resultHandler.handle(Future.succeededFuture());
  }

  @Override
  public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> resultHandler) {
    Holder<V> h = map.putIfAbsent(k, new Holder<>(v));
    resultHandler.handle(Future.succeededFuture(h == null ? null : h.value));
  }

  @Override
  public void put(K k, V v, long timeout, Handler<AsyncResult<Void>> completionHandler) {
    long timestamp = System.nanoTime();
    long timerId = vertx.setTimer(timeout, l -> removeIfExpired(k));
    Holder<V> previous = map.put(k, new Holder<>(v, timerId, timeout, timestamp));
    if (previous != null && previous.expires()) {
      vertx.cancelTimer(previous.timerId);
    }
    completionHandler.handle(Future.succeededFuture());
  }

  private void removeIfExpired(K k) {
    map.computeIfPresent(k, (key, holder) -> holder.hasNotExpired() ? holder : null);
  }

  @Override
  public void putIfAbsent(K k, V v, long timeout, Handler<AsyncResult<V>> completionHandler) {
    long timestamp = System.nanoTime();
    long timerId = vertx.setTimer(timeout, l -> removeIfExpired(k));
    Holder<V> existing = map.putIfAbsent(k, new Holder<>(v, timerId, timeout, timestamp));
    if (existing != null) {
      if (existing.expires()) {
        vertx.cancelTimer(timerId);
      }
      completionHandler.handle(Future.succeededFuture(existing.value));
    } else {
      completionHandler.handle(Future.succeededFuture());
    }
  }

  @Override
  public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
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
    resultHandler.handle(Future.succeededFuture(result.get()));
  }

  @Override
  public void replace(K k, V v, Handler<AsyncResult<V>> resultHandler) {
    Holder<V> previous = map.replace(k, new Holder<>(v));
    if (previous != null) {
      if (previous.expires()) {
        vertx.cancelTimer(previous.timerId);
      }
      resultHandler.handle(Future.succeededFuture(previous.value));
    } else {
      resultHandler.handle(Future.succeededFuture());
    }
  }

  @Override
  public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
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
    resultHandler.handle(Future.succeededFuture(h == result));
  }

  @Override
  public void clear(Handler<AsyncResult<Void>> resultHandler) {
    map.clear();
    resultHandler.handle(Future.succeededFuture());
  }

  @Override
  public void size(Handler<AsyncResult<Integer>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(map.size()));
  }

  @Override
  public void keys(Handler<AsyncResult<Set<K>>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(new HashSet<>(map.keySet())));
  }

  @Override
  public void values(Handler<AsyncResult<List<V>>> asyncResultHandler) {
    List<V> result = map.values().stream()
      .filter(Holder::hasNotExpired)
      .map(h -> h.value)
      .collect(toList());
    asyncResultHandler.handle(Future.succeededFuture(result));
  }

  @Override
  public void entries(Handler<AsyncResult<Map<K, V>>> asyncResultHandler) {
    Map<K, V> result = new HashMap<>(map.size());
    map.forEach((key, holder) -> {
      if (holder.hasNotExpired()) {
        result.put(key, holder.value);
      }
    });
    asyncResultHandler.handle(Future.succeededFuture(result));
  }

  @Override
  public void remove(final K k, Handler<AsyncResult<V>> resultHandler) {
    Holder<V> previous = map.remove(k);
    if (previous != null) {
      if (previous.expires()) {
        vertx.cancelTimer(previous.timerId);
      }
      resultHandler.handle(Future.succeededFuture(previous.value));
    } else {
      resultHandler.handle(Future.succeededFuture());
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
