/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
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

import java.util.List;

/**
 * View of a clustered node.
 */
public interface ClusteredNode {

  /**
   * Return the unique node identifier for this node.
   */
  String getNodeId();

  /**
   * Get the messaging handler currently registered in the cluster.
   */
  void getRegistrations(String address, Promise<List<RegistrationInfo>> promise);

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
   * Return a list of node identifiers corresponding to the nodes in the cluster.
   */
  List<String> getNodes();

}
