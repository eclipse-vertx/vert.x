/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package org.vertx.java.core.spi.cluster;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;


/**
 *
 * An asynchronous map.
 *
 * The cluster implementation should ensure that any entries placed in the map from any node are available on any
 * node of the cluster.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public interface AsyncMap<K, V> {

  /**
   * Get a value from the map, asynchronously.
   * @param k The key
   * @param resultHandler - this will be called some time later with the async result.
   */
  void get(K k, Handler<AsyncResult<V>> resultHandler);

  /**
   * Put a value in the map, asynchronously.
   * @param k The key
   * @param v The value
   * @param completionHandler - this will be called some time later to signify the value has been put
   */
  void put(K k, V v, Handler<AsyncResult<Void>> completionHandler);

  /**
   * Remove a value from the map, asynchronously.
   * @param k The key
   * @param completionHandler - this will be called some time later to signify the value has been removed
   */
  void remove(K k, Handler<AsyncResult<Void>> completionHandler);
}
