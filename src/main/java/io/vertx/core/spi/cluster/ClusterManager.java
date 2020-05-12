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

package io.vertx.core.spi.cluster;


import io.vertx.core.Promise;
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

  /**
   * Invoked before this cluster node tries to join the cluster.
   * <p>
   * Implementations must signal the provided {@code nodeSelector} when messaging handler registrations are added or removed
   * by sending a {@link RegistrationUpdateEvent} with {@link NodeSelector#registrationsUpdated(RegistrationUpdateEvent)}.
   *
   * @param vertx        the Vert.x instance
   * @param nodeSelector the {@link NodeSelector} that must receive {@link RegistrationUpdateEvent}.
   */
  void init(Vertx vertx, NodeSelector nodeSelector);

  /**
   * Return an {@link AsyncMap} for the given {@code name}.
   */
  <K, V> void getAsyncMap(String name, Promise<AsyncMap<K, V>> promise);

  /**
   * Return a synchronous map for the given {@code name}.
   */
  <K, V> Map<K, V> getSyncMap(String name);

  /**
   * Attempts to acquire a {@link Lock} for the given {@code name} within {@code timeout} milliseconds.
   */
  void getLockWithTimeout(String name, long timeout, Promise<Lock> promise);

  /**
   * Return a {@link Counter} for the given {@code name}.
   */
  void getCounter(String name, Promise<Counter> promise);

  /**
   * Return the unique node identifier for this node.
   */
  String getNodeId();

  /**
   * Return a list of node identifiers corresponding to the nodes in the cluster.
   */
  List<String> getNodes();

  /**
   * Set a listener that will be called when a node joins or leaves the cluster.
   */
  void nodeListener(NodeListener listener);

  /**
   * Store the details about this clustered node.
   */
  void setNodeInfo(NodeInfo nodeInfo, Promise<Void> promise);

  /**
   * Get details about this clustered node.
   */
  NodeInfo getNodeInfo();

  /**
   * Get details about a specific node in the cluster.
   *
   * @param nodeId the clustered node id
   */
  void getNodeInfo(String nodeId, Promise<NodeInfo> promise);

  /**
   * Join the cluster.
   */
  void join(Promise<Void> promise);

  /**
   * Leave the cluster.
   */
  void leave(Promise<Void> promise);

  /**
   * Is the cluster manager active?
   *
   * @return true if active, false otherwise
   */
  boolean isActive();

  /**
   * Share a new messaging handler registration with other nodes in the cluster.
   */
  void addRegistration(String address, RegistrationInfo registrationInfo, Promise<Void> promise);

  /**
   * Signal removal of a messaging handler registration to other nodes in the cluster.
   */
  void removeRegistration(String address, RegistrationInfo registrationInfo, Promise<Void> promise);

  /**
   * Get the messaging handler currently registered in the cluster.
   */
  void getRegistrations(String address, Promise<List<RegistrationInfo>> promise);
}
