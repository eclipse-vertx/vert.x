/*
 * Copyright (c) 2011-2013 The original author or authors
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

package io.vertx.core.shareddata;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 *
 * An asynchronous map.
 * <p>
 * @implSpec Implementations of the interface must handle {@link io.vertx.core.shareddata.impl.ClusterSerializable}
 *           implementing objects.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
@VertxGen
public interface AsyncMap<K, V> {

  /**
   * Get a value from the map, asynchronously.
   *
   * @param k  the key
   * @param resultHandler - this will be called some time later with the async result.
   */
  void get(K k, Handler<AsyncResult<V>> resultHandler);

  /**
   * Put a value in the map, asynchronously.
   *
   * @param k  the key
   * @param v  the value
   * @param completionHandler - this will be called some time later to signify the value has been put
   */
  void put(K k, V v, Handler<AsyncResult<Void>> completionHandler);

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
   * Put the entry only if there is no entry with the key already present. If key already present then the existing
   * value will be returned to the handler, otherwise null.
   *
   * @param k  the key
   * @param v  the value
   * @param completionHandler  the handler
   */
  void putIfAbsent(K k, V v, Handler<AsyncResult<V>> completionHandler);

  /**
   * Link {@link #putIfAbsent} but specifying a time to live for the entry. Entry will expire and get evicted
   * after the ttl.
   *
   * @param k  the key
   * @param v  the value
   * @param ttl  The time to live (in ms) for the entry
   * @param completionHandler  the handler
   */
  void putIfAbsent(K k, V v, long ttl, Handler<AsyncResult<V>> completionHandler);

  /**
   * Remove a value from the map, asynchronously.
   *
   * @param k  the key
   * @param resultHandler - this will be called some time later to signify the value has been removed
   */
  void remove(K k, Handler<AsyncResult<V>> resultHandler);

  /**
   * Remove a value from the map, only if entry already exists with same value.
   *
   * @param k  the key
   * @param v  the value
   * @param resultHandler - this will be called some time later to signify the value has been removed
   */
  void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler);


  /**
   * Replace the entry only if it is currently mapped to some value
   *
   * @param k  the key
   * @param v  the new value
   * @param resultHandler  the result handler will be passed the previous value
   */
  void replace(K k, V v, Handler<AsyncResult<V>> resultHandler);

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
   * Clear all entries in the map
   *
   * @param resultHandler  called on completion
   */
  void clear(Handler<AsyncResult<Void>> resultHandler);

  /**
   * Provide the number of entries in the map
   *
   * @param resultHandler  handler which will receive the number of entries
   */
  void size(Handler<AsyncResult<Integer>> resultHandler);

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
}
