/*
 * Copyright (c) 2011-2017 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
    Holder<V> h = map.computeIfPresent(k, (key, holder) -> notExpiredOrNull(holder));
    resultHandler.handle(Future.succeededFuture(valueOrNull(h)));
  }

  private Holder<V> notExpiredOrNull(Holder<V> holder) {
    return holder.hasNotExpired() ? holder : null;
  }

  private V valueOrNull(Holder<V> h) {
    return h != null ? h.value : null;
  }

  @Override
  public void put(final K k, final V v, Handler<AsyncResult<Void>> resultHandler) {
    map.put(k, new Holder<>(v));
    resultHandler.handle(Future.succeededFuture());
  }

  @Override
  public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> resultHandler) {
    Holder<V> h = map.putIfAbsent(k, new Holder<>(v));
    resultHandler.handle(Future.succeededFuture(valueOrNull(h)));
  }

  @Override
  public void put(K k, V v, long timeout, Handler<AsyncResult<Void>> completionHandler) {
    map.put(k, new Holder<>(v, timeout));
    completionHandler.handle(Future.succeededFuture());
    vertx.setTimer(timeout, l -> map.computeIfPresent(k, (key, holder) -> notExpiredOrNull(holder)));
  }

  @Override
  public void putIfAbsent(K k, V v, long timeout, Handler<AsyncResult<V>> completionHandler) {
    Holder<V> h = map.putIfAbsent(k, new Holder<>(v, timeout));
    completionHandler.handle(Future.succeededFuture(valueOrNull(h)));
    if (h == null) {
      vertx.setTimer(timeout, l -> map.computeIfPresent(k, (key, holder) -> notExpiredOrNull(holder)));
    }
  }

  @Override
  public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(map.remove(k, new Holder<>(v))));
  }

  @Override
  public void replace(K k, V v, Handler<AsyncResult<V>> resultHandler) {
    Holder<V> h = map.replace(k, new Holder<>(v));
    resultHandler.handle(Future.succeededFuture(valueOrNull(h)));
  }

  @Override
  public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
    resultHandler.handle(Future.succeededFuture(map.replace(k, new Holder<>(oldValue), new Holder<>(newValue))));
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
    Holder<V> h = map.remove(k);
    resultHandler.handle(Future.succeededFuture(valueOrNull(h)));
  }

  private static class Holder<V> {
    final V value;
    final long expiresOn;

    Holder(V value) {
      Objects.requireNonNull(value);
      this.value = value;
      this.expiresOn = -1;
    }

    Holder(V value, long ttl) {
      Objects.requireNonNull(value);
      this.value = value;
      this.expiresOn = System.currentTimeMillis() + ttl;
    }

    boolean hasNotExpired() {
      return expiresOn <= 0 || System.currentTimeMillis() <= expiresOn;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Holder<?> holder = (Holder<?>) o;

      if (!value.equals(holder.value)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public String toString() {
      return "Holder{" + "value=" + value + ", expiresOn=" + expiresOn + '}';
    }
  }
}
