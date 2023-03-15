/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
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
import io.vertx.core.Future;

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
   * @return a future notified with the map
   * @throws IllegalStateException if the parent {@link io.vertx.core.Vertx} instance is not clustered
   */
  <K, V> Future<AsyncMap<K, V>> getClusterWideMap(String name);

  /**
   * Get the {@link AsyncMap} with the specified name. When clustered, the map is accessible to all nodes in the cluster
   * and data put into the map from any node is visible to to any other node.
   * <p>
   *   <strong>WARNING</strong>: In clustered mode, asynchronous shared maps rely on distributed data structures provided by the cluster manager.
   *   Beware that the latency relative to asynchronous shared maps operations can be much higher in clustered than in local mode.
   * </p>
   *
   * @param name the name of the map
   * @return a future notified with the map
   */
  <K, V> Future<AsyncMap<K, V>> getAsyncMap(String name);

  /**
   * Get the {@link AsyncMap} with the specified name.
   * <p>
   * When clustered, the map is <b>NOT</b> accessible to all nodes in the cluster.
   * Only the instance which created the map can put and retrieve data from this map.
   *
   * @param name the name of the map
   * @return a future notified with the map
   */
  <K, V> Future<AsyncMap<K, V>> getLocalAsyncMap(String name);

  /**
   * Get an asynchronous lock with the specified name. The lock will be passed to the handler when it is available.
   * <p>
   *   In general lock acquision is unordered, so that sequential attempts to acquire a lock,
   *   even from a single thread, can happen in non-sequential order.
   * </p>
   *
   * @param name  the name of the lock
   * @return a future notified with the lock
   */
  Future<Lock> getLock(String name);

  /**
   * Like {@link #getLock(String)} but specifying a timeout. If the lock is not obtained within the timeout
   * a failure will be sent to the handler.
   * <p>
   *   In general lock acquision is unordered, so that sequential attempts to acquire a lock,
   *   even from a single thread, can happen in non-sequential order.
   * </p>
   *
   * @param name  the name of the lock
   * @param timeout  the timeout in ms
   * @return a future notified with the lock
   */
  Future<Lock> getLockWithTimeout(String name, long timeout);

  /**
   * Get an asynchronous local lock with the specified name. The lock will be passed to the handler when it is available.
   * <p>
   *   In general lock acquision is unordered, so that sequential attempts to acquire a lock,
   *   even from a single thread, can happen in non-sequential order.
   * </p>
   *
   * @param name  the name of the lock
   * @return a future notified with the lock
   */
  Future<Lock> getLocalLock(String name);

  /**
   * Like {@link #getLocalLock(String)} but specifying a timeout. If the lock is not obtained within the timeout
   * a failure will be sent to the handler.
   * <p>
   *   In general lock acquision is unordered, so that sequential attempts to acquire a lock,
   *   even from a single thread, can happen in non-sequential order.
   * </p>
   *
   * @param name  the name of the lock
   * @param timeout  the timeout in ms
   * @return a future notified with the lock
   */
  Future<Lock> getLocalLockWithTimeout(String name, long timeout);

  /**
   * Get an asynchronous counter. The counter will be passed to the handler.
   *
   * @param name  the name of the counter.
   * @return a future notified with the counter
   */
  Future<Counter> getCounter(String name);

  /**
   * Get an asynchronous local counter. The counter will be passed to the handler.
   *
   * @param name  the name of the counter.
   * @return a future notified with the counter
   */
  Future<Counter> getLocalCounter(String name);

  /**
   * Return a {@code LocalMap} with the specific {@code name}.
   *
   * @param name  the name of the map
   * @return the map
   */
  <K, V> LocalMap<K, V> getLocalMap(String name);

}
