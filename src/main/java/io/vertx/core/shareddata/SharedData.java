/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
 *   <li>synchronous shared maps (local)</li>
 *   <li>asynchronous maps (local or cluster-wide)</li>
 *   <li>asynchronous locks (local or cluster-wide)</li>
 *   <li>asynchronous counters (local or cluster-wide)</li>
 * </ul>
 * <p>
 * <p>
 *   <strong>WARNING</strong>: In clustered mode, asynchronous maps/locks/counters rely on distributed data structures provided by the cluster manager.
 *   Beware that the latency relative to asynchronous maps/locks/counters operations can be much higher in clustered than in local mode.
 * </p>
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
   * @throws IllegalStateException if the parent {@link io.vertx.core.Vertx} instance is not clustered
   */
  <K, V> void getClusterWideMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler);

  /**
   * Get the {@link AsyncMap} with the specified name. When clustered, the map is accessible to all nodes in the cluster
   * and data put into the map from any node is visible to to any other node.
   * <p>
   *   <strong>WARNING</strong>: In clustered mode, asynchronous shared maps rely on distributed data structures provided by the cluster manager.
   *   Beware that the latency relative to asynchronous shared maps operations can be much higher in clustered than in local mode.
   * </p>
   *
   * @param name the name of the map
   * @param resultHandler the map will be returned asynchronously in this handler
   */
  <K, V> void getAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler);

  /**
   * Get an asynchronous lock with the specified name. The lock will be passed to the handler when it is available.
   * <p>
   *   In general lock acquision is unordered, so that sequential attempts to acquire a lock,
   *   even from a single thread, can happen in non-sequential order.
   * </p>
   *
   * @param name  the name of the lock
   * @param resultHandler  the handler
   */
  void getLock(String name, Handler<AsyncResult<Lock>> resultHandler);

  /**
   * Like {@link #getLock(String, Handler)} but specifying a timeout. If the lock is not obtained within the timeout
   * a failure will be sent to the handler.
   * <p>
   *   In general lock acquision is unordered, so that sequential attempts to acquire a lock,
   *   even from a single thread, can happen in non-sequential order.
   * </p>
   *
   * @param name  the name of the lock
   * @param timeout  the timeout in ms
   * @param resultHandler  the handler
   */
  void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler);

  /**
   * Get an asynchronous counter. The counter will be passed to the handler.
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
