/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.spi.cluster;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;

/**
 *
 * An asynchronous multi-map.
 *
 * A multi-map holds a List of values against each key as opposed to a single value, as with a Map.
 *
 * The cluster implementation should ensure that any entries placed in the map from any node are available on any
 * node of the cluster.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public interface AsyncMultiMap<K, V> {

  /**
   * Add a value to the list of values for that key in the map
   * @param k The key
   * @param v The value
   * @param completionHandler This will be called when the entry has been added
   */
  void add(K k, V v, Handler<AsyncResult<Void>> completionHandler);

  /**
   * Get a list of values from the map for the key
   * @param k The key
   * @param resultHandler This will be called with the list of values for the key. The type of the values returned
   *                      must be {@link ChoosableIterable}
   */
  void get(K k, Handler<AsyncResult<ChoosableIterable<V>>> resultHandler);

  /**
   * Remove a value from the list of values for that key in the map.
   * @param k The key
   * @param v The value
   * @param completionHandler This will be called when the remove is complete
   */
  void remove(K k, V v, Handler<AsyncResult<Void>> completionHandler);

  /**
   * Remove all the specified values from all keys in the map
   * @param v The value
   * @param completionHandler This will be called when the remove is complete
   */
  void removeAllForValue(V v, Handler<AsyncResult<Void>> completionHandler);
}
