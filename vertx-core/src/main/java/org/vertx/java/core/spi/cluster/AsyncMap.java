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
