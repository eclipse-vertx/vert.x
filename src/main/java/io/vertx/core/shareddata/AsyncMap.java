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

package io.vertx.core.shareddata;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * An asynchronous map.
 * <p>
 * {@link AsyncMap} does <em>not</em> allow {@code null} to be used as a key or value.
 *
 * @implSpec Implementations of the interface must handle {@link io.vertx.core.shareddata.impl.ClusterSerializable}
 * implementing objects.
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface AsyncMap<K, V> {

  /**
   * Get a value from the map, asynchronously.
   *
   * @param k  the key
   * @param resultHandler - this will be called some time later with the async result.
   */
  default void get(K k, Handler<AsyncResult<@Nullable V>> resultHandler) {
    get(k).onComplete(resultHandler);
  }

  /**
   * Same as {@link #get(K, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<@Nullable V> get(K k);

  /**
   * Put a value in the map, asynchronously.
   *
   * @param k  the key
   * @param v  the value
   * @param completionHandler - this will be called some time later to signify the value has been put
   */
  default void put(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    put(k, v).onComplete(completionHandler);
  }

  /**
   * Same as {@link #put(K, V, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Void> put(K k, V v);

  /**
   * Like {@link #put} but specifying a time to live for the entry. Entry will expire and get evicted after the
   * ttl.
   *
   * @param k  the key
   * @param v  the value
   * @param ttl  The time to live (in ms) for the entry
   * @param completionHandler  the handler
   */
  default void put(K k, V v, long ttl, Handler<AsyncResult<Void>> completionHandler) {
    put(k, v, ttl).onComplete(completionHandler);
  }

  /**
   * Same as {@link #put(K, V, long, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Void> put(K k, V v, long ttl);

  /**
   * Put the entry only if there is no entry with the key already present. If key already present then the existing
   * value will be returned to the handler, otherwise null.
   *
   * @param k  the key
   * @param v  the value
   * @param completionHandler  the handler
   */
  default void putIfAbsent(K k, V v, Handler<AsyncResult<@Nullable V>> completionHandler) {
    putIfAbsent(k, v).onComplete(completionHandler);
  }

  /**
   * Same as {@link #putIfAbsent(K, V, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<@Nullable V> putIfAbsent(K k, V v);

  /**
   * Link {@link #putIfAbsent} but specifying a time to live for the entry. Entry will expire and get evicted
   * after the ttl.
   *
   * @param k  the key
   * @param v  the value
   * @param ttl  The time to live (in ms) for the entry
   * @param completionHandler  the handler
   */
  default void putIfAbsent(K k, V v, long ttl, Handler<AsyncResult<@Nullable V>> completionHandler) {
    putIfAbsent(k, v, ttl).onComplete(completionHandler);
  }

  /**
   * Same as {@link #putIfAbsent(K, V, long, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<@Nullable V> putIfAbsent(K k, V v, long ttl);

  /**
   * Remove a value from the map, asynchronously.
   *
   * @param k  the key
   * @param resultHandler - this will be called some time later to signify the value has been removed
   */
  default void remove(K k, Handler<AsyncResult<@Nullable V>> resultHandler) {
    remove(k).onComplete(resultHandler);
  }

  /**
   * Same as {@link #remove(K, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<@Nullable V> remove(K k);

  /**
   * Remove a value from the map, only if entry already exists with same value.
   *
   * @param k  the key
   * @param v  the value
   * @param resultHandler - this will be called some time later to signify the value has been removed
   */
  default void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
    removeIfPresent(k, v).onComplete(resultHandler);
  }

  /**
   * Same as {@link #removeIfPresent(K, V, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Boolean> removeIfPresent(K k, V v);

  /**
   * Replace the entry only if it is currently mapped to some value
   *
   * @param k  the key
   * @param v  the new value
   * @param resultHandler  the result handler will be passed the previous value
   */
  default void replace(K k, V v, Handler<AsyncResult<@Nullable V>> resultHandler) {
    replace(k, v).onComplete(resultHandler);
  }

  /**
   * Same as {@link #replace(K, V, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<@Nullable V> replace(K k, V v);

  /**
   * Replace the entry only if it is currently mapped to a specific value
   *
   * @param k  the key
   * @param oldValue  the existing value
   * @param newValue  the new value
   * @param resultHandler the result handler
   */
  default void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
    replaceIfPresent(k, oldValue, newValue).onComplete(resultHandler);
  }

  /**
   * Same as {@link #replaceIfPresent(K, V, V, Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Boolean> replaceIfPresent(K k, V oldValue, V newValue);

  /**
   * Clear all entries in the map
   *
   * @param resultHandler  called on completion
   */
  default void clear(Handler<AsyncResult<Void>> resultHandler) {
    clear().onComplete(resultHandler);
  }

  /**
   * Same as {@link #clear(Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Void> clear();

  /**
   * Provide the number of entries in the map
   *
   * @param resultHandler  handler which will receive the number of entries
   */
  default void size(Handler<AsyncResult<Integer>> resultHandler) {
    size().onComplete(resultHandler);
  }

  /**
   * Same as {@link #size(Handler)} but returns a {@code Future} of the asynchronous result
   */
  Future<Integer> size();

  /**
   * Get the keys of the map, asynchronously.
   * <p>
   * Use this method with care as the map may contain a large number of keys,
   * which may not fit entirely in memory of a single node.
   * In this case, the invocation will result in an {@link OutOfMemoryError}.
   *
   * @param resultHandler invoked when the operation completes
   */
  @GenIgnore
  default void keys(Handler<AsyncResult<Set<K>>> resultHandler) {
    keys().onComplete(resultHandler);
  }

  /**
   * Same as {@link #keys(Handler)} but returns a {@code Future} of the asynchronous result
   */
  @GenIgnore
  Future<Set<K>> keys();

  /**
   * Get the values of the map, asynchronously.
   * <p>
   * Use this method with care as the map may contain a large number of values,
   * which may not fit entirely in memory of a single node.
   * In this case, the invocation will result in an {@link OutOfMemoryError}.
   *
   * @param resultHandler invoked when the operation completes
   */
  @GenIgnore
  default void values(Handler<AsyncResult<List<V>>> resultHandler) {
    values().onComplete(resultHandler);
  }

  /**
   * Same as {@link #values(Handler)} but returns a {@code Future} of the asynchronous result
   */
  @GenIgnore
  Future<List<V>> values();

  /**
   * Get the entries of the map, asynchronously.
   * <p>
   * Use this method with care as the map may contain a large number of entries,
   * which may not fit entirely in memory of a single node.
   * In this case, the invocation will result in an {@link OutOfMemoryError}.
   *
   * @param resultHandler invoked when the operation completes
   */
  @GenIgnore
  default void entries(Handler<AsyncResult<Map<K, V>>> resultHandler) {
    entries().onComplete(resultHandler);
  }

  /**
   * Same as {@link #entries(Handler)} but returns a {@code Future} of the asynchronous result
   */
  @GenIgnore
  Future<Map<K, V>> entries();
}
