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

package io.vertx.core.spi.cluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import java.util.function.Predicate;

/**
 *
 * An asynchronous multi-map.
 *
 * A multi-map holds a Set of values against each key as opposed to a single value, as with a Map. A value appears only
 * once for a key (no duplicated values for the same key).
 *
 * The cluster implementation should ensure that any entries placed in the map from any node are available on any
 * node of the cluster.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public interface AsyncMultiMap<K, V> {

  /**
   * Add a value to the values for that key in the map.
   * @param k The key
   * @param v The value
   * @param completionHandler This will be called when the entry has been added
   */
  void add(K k, V v, Handler<AsyncResult<Void>> completionHandler);

  /**
   * Get the values from the map for the key. If the key isn't in the map, an empty {@link ChoosableIterable} must
   * be returned.
   * @param k The key
   * @param resultHandler This will be called with the list of values for the key. The type of the values returned
   *                      must be {@link ChoosableIterable}
   */
  void get(K k, Handler<AsyncResult<ChoosableIterable<V>>> resultHandler);

  /**
   * Remove a value from the values of that key in the map.
   * @param k The key
   * @param v The value
   * @param completionHandler This will be called with {@code true} if the value was found and {@code false} otherwise,
   *                          when the remove is complete
   */
  void remove(K k, V v, Handler<AsyncResult<Boolean>> completionHandler);

  /**
   * Remove the specified value from all values found {@code equals} associated to all keys in the map.
   * @param v The value
   * @param completionHandler This will be called when the remove is complete
   */
  void removeAllForValue(V v, Handler<AsyncResult<Void>> completionHandler);

  /**
   * Remove values which satisfies the given predicate in all keys.
   *
   * @param p                 The predicate
   * @param completionHandler This will be called when the remove is complete
   */
  void removeAllMatching(Predicate<V> p, Handler<AsyncResult<Void>> completionHandler);
}
