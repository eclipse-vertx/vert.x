/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
  void get(K k, Handler<AsyncResult<@Nullable V>> resultHandler);

  /**
   * Same as {@link #get(K, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<@Nullable V> get(K k) {
    Promise<V> promise = Promise.promise();
    get(k, promise);
    return promise.future();
  }

  /**
   * Put a value in the map, asynchronously.
   *
   * @param k  the key
   * @param v  the value
   * @param completionHandler - this will be called some time later to signify the value has been put
   */
  void put(K k, V v, Handler<AsyncResult<Void>> completionHandler);

  /**
   * Same as {@link #put(K, V, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Void> put(K k, V v) {
    Promise<Void> promise = Promise.promise();
    put(k, v, promise);
    return promise.future();
  }

  /**
   * Like {@link #put} but specifying a time to live for the entry. Entry will expire and get evicted after the
   * ttl.
   *
   * @param k  the key
   * @param v  the value
   * @param ttl  The time to live (in ms) for the entry
   * @param completionHandler  the handler
   */
  void put(K k, V v, long ttl, Handler<AsyncResult<Void>> completionHandler);

  /**
   * Same as {@link #put(K, V, long, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Void> put(K k, V v, long ttl) {
    Promise<Void> promise = Promise.promise();
    put(k, v, ttl);
    return promise.future();
  }

  /**
   * Put the entry only if there is no entry with the key already present. If key already present then the existing
   * value will be returned to the handler, otherwise null.
   *
   * @param k  the key
   * @param v  the value
   * @param completionHandler  the handler
   */
  void putIfAbsent(K k, V v, Handler<AsyncResult<@Nullable V>> completionHandler);

  /**
   * Same as {@link #putIfAbsent(K, V, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<@Nullable V> putIfAbsent(K k, V v) {
    Promise<V> promise = Promise.promise();
    putIfAbsent(k, v, promise);
    return promise.future();
  }

  /**
   * Link {@link #putIfAbsent} but specifying a time to live for the entry. Entry will expire and get evicted
   * after the ttl.
   *
   * @param k  the key
   * @param v  the value
   * @param ttl  The time to live (in ms) for the entry
   * @param completionHandler  the handler
   */
  void putIfAbsent(K k, V v, long ttl, Handler<AsyncResult<@Nullable V>> completionHandler);

  /**
   * Same as {@link #putIfAbsent(K, V, long, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<@Nullable V> putIfAbsent(K k, V v, long ttl) {
    Promise<V> promise = Promise.promise();
    putIfAbsent(k, v, ttl, promise);
    return promise.future();
  }

  /**
   * Remove a value from the map, asynchronously.
   *
   * @param k  the key
   * @param resultHandler - this will be called some time later to signify the value has been removed
   */
  void remove(K k, Handler<AsyncResult<@Nullable V>> resultHandler);

  /**
   * Same as {@link #remove(K, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<@Nullable V> remove(K k) {
    Promise<V> promise = Promise.promise();
    remove(k, promise);
    return promise.future();
  }

  /**
   * Remove a value from the map, only if entry already exists with same value.
   *
   * @param k  the key
   * @param v  the value
   * @param resultHandler - this will be called some time later to signify the value has been removed
   */
  void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler);

  /**
   * Same as {@link #removeIfPresent(K, V, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Boolean> removeIfPresent(K k, V v) {
    Promise<Boolean> promise = Promise.promise();
    removeIfPresent(k, v, promise);
    return promise.future();
  }

  /**
   * Replace the entry only if it is currently mapped to some value
   *
   * @param k  the key
   * @param v  the new value
   * @param resultHandler  the result handler will be passed the previous value
   */
  void replace(K k, V v, Handler<AsyncResult<@Nullable V>> resultHandler);

  /**
   * Same as {@link #replace(K, V, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<@Nullable V> replace(K k, V v) {
    Promise<V> promise = Promise.promise();
    replace(k, v, promise);
    return promise.future();
  }

  /**
   * Replace the entry only if it is currently mapped to a specific value
   *
   * @param k  the key
   * @param oldValue  the existing value
   * @param newValue  the new value
   * @param resultHandler the result handler
   */
  void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler);

  /**
   * Same as {@link #replaceIfPresent(K, V, V, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Boolean> replaceIfPresent(K k, V oldValue, V newValue) {
    Promise<Boolean> promise = Promise.promise();
    replaceIfPresent(k, oldValue, newValue, promise);
    return promise.future();
  }

  /**
   * Clear all entries in the map
   *
   * @param resultHandler  called on completion
   */
  void clear(Handler<AsyncResult<Void>> resultHandler);

  /**
   * Same as {@link #clear(Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Void> clear() {
    Promise<Void> promise = Promise.promise();
    clear(promise);
    return promise.future();
  }

  /**
   * Provide the number of entries in the map
   *
   * @param resultHandler  handler which will receive the number of entries
   */
  void size(Handler<AsyncResult<Integer>> resultHandler);

  /**
   * Same as {@link #size(Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<Integer> size() {
    Promise<Integer> promise = Promise.promise();
    size(promise);
    return promise.future();
  }

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
  void keys(Handler<AsyncResult<Set<K>>> resultHandler);

  /**
   * Same as {@link #keys(Handler)} but returns a {@code Future} of the asynchronous result
   */
  @GenIgnore
  default Future<Set<K>> keys() {
    Promise<Set<K>> promise = Promise.promise();
    keys(promise);
    return promise.future();
  }

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
  void values(Handler<AsyncResult<List<V>>> resultHandler);

  /**
   * Same as {@link #values(Handler)} but returns a {@code Future} of the asynchronous result
   */
  @GenIgnore
  default Future<List<V>> values() {
    Promise<List<V>> promise = Promise.promise();
    values(promise);
    return promise.future();
  }

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
  void entries(Handler<AsyncResult<Map<K, V>>> resultHandler);

  /**
   * Same as {@link #entries(Handler)} but returns a {@code Future} of the asynchronous result
   */
  @GenIgnore
  default Future<Map<K, V>> entries() {
    Promise<Map<K, V>> promise = Promise.promise();
    entries(promise);
    return promise.future();
  }
}
