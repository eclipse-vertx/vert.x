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

package io.vertx.core.spi.cluster;


import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;

import java.util.List;
import java.util.Map;

/**
 *
 * A cluster provider for Vert.x must implement this interface.
 *
 * In order for HA to work correctly, all implementations of this interface MUST be implemented such that:
 *
 * 1. Whenever a node joins or leaves the cluster the registered NodeListener (if any) MUST be called with the
 * appropriate join or leave event.
 * 2. For all nodes that are part of the cluster, the registered NodeListener MUST be called with the exact same
 * sequence of join and leave events on all nodes.
 * 3. For any particular join or leave event that is handled in any NodeListener, anywhere in the cluster, the List
 * of nodes returned by getNodes must be identical.
 * 4. All of the methods in the implementation must return immediately, i.e. they must not block while the operation
 * is being executed. If the underlying implementation does block, then {@link io.vertx.core.Vertx#executeBlocking}
 * should be used to run the operation on a worker.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ClusterManager {

  void setVertx(Vertx vertx);

  /**
   * Return an async multi-map for the given name
   */
  <K, V> void getAsyncMultiMap(String name, Handler<AsyncResult<AsyncMultiMap<K, V>>> resultHandler);

  /**
   * Return an async map for the given name
   */
  <K, V> void getAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler);

  /**
   * Return a synchronous map for the given name
   */
  <K, V> Map<K, V> getSyncMap(String name);

  void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler);

  void getCounter(String name, Handler<AsyncResult<Counter>> resultHandler);

  /**
   * Return the unique node ID for this node
   */
  String getNodeID();

  /**
   * Return a list of node IDs corresponding to the nodes in the cluster
   *
   */
  List<String> getNodes();

  /**
   * Set a listener that will be called when a node joins or leaves the cluster.
   *
   * @param listener
   */
  void nodeListener(NodeListener listener);

  /**
   * Join the cluster
   */
  void join(Handler<AsyncResult<Void>> resultHandler);

  /**
   * Leave the cluster
   */
  void leave(Handler<AsyncResult<Void>> resultHandler);

  boolean isActive();
}
