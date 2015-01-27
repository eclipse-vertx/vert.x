/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.shareddata;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * Shared data allows you to share data safely between different parts of your application in a safe way.
 * <p>
 * Shared data provides:
 * <ul>
 *   <li>Cluster wide maps which can be accessed from any node of the cluster</li>
 *   <li>Cluster wide locks which can be used to give exclusive access to resources across the cluster</li>
 *   <li>Cluster wide counters used to maintain counts consistently across the cluster</li>
 *   <li>Local maps for sharing data safely in the same Vert.x instance</li>
 * </ul>
 * <p>
 * Please see the documentation for more information.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface SharedData {

  /**
   * Get the cluster wide map with the specified name. The map is accessible to all nodes in the cluster and data
   * put into the map from any node is visible to to any other node.
   *
   * @param name  the name of the map
   * @param resultHandler  the map will be returned asynchronously in this handler
   */
  <K, V> void getClusterWideMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler);

  /**
   * Get a cluster wide lock with the specified name. The lock will be passed to the handler when it is available.
   *
   * @param name  the name of the lock
   * @param resultHandler  the handler
   */
  void getLock(String name, Handler<AsyncResult<Lock>> resultHandler);

  /**
   * Like {@link #getLock(String, Handler)} but specifying a timeout. If the lock is not obtained within the timeout
   * a failure will be sent to the handler
   * @param name  the name of the lock
   * @param timeout  the timeout in ms
   * @param resultHandler  the handler
   */
  void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler);

  /**
   * Get a cluster wide counter. The counter will be passed to the handler.
   *
   * @param name  the name of the counter.
   * @param resultHandler  the handler
   */
  void getCounter(String name, Handler<AsyncResult<Counter>> resultHandler);

  /**
   * Return a {@code LocalMap} with the specific {@code name}.
   *
   * @param name  the name of the map
   * @return the msp
   */
  <K, V> LocalMap<K, V> getLocalMap(String name);

}
