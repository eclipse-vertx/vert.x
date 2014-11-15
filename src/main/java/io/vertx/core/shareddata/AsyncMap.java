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

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;


/**
 *
 * An asynchronous map.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
@VertxGen
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

  void putIfAbsent(K k, V v, Handler<AsyncResult<V>> completionHandler);

  /**
   * Remove a value from the map, asynchronously.
   * @param k The key
   * @param resultHandler - this will be called some time later to signify the value has been removed
   */
  void remove(K k, Handler<AsyncResult<V>> resultHandler);

  void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler);

  void replace(K k, V v, Handler<AsyncResult<V>> resultHandler);

  void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler);

  void clear(Handler<AsyncResult<Void>> resultHandler);

}
