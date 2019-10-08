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

import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

/**
 * Clustered {@link io.vertx.core.eventbus.EventBus} node details.
 *
 * @author Thomas Segismont
 */
public final class NodeInfo {

  private final String nodeId;
  private final String host;
  private final int port;
  private final JsonObject metadata;

  public NodeInfo(String nodeId, String host, int port, JsonObject metadata) {
    Objects.requireNonNull(nodeId, "nodeId is null");
    Objects.requireNonNull(host, "host is null");
    Arguments.requireInRange(port, 1, 65535, "Not an actual port: " + port);
    this.nodeId = nodeId;
    this.host = host;
    this.port = port;
    this.metadata = metadata;
  }

  public String getNodeId() {
    return nodeId;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public JsonObject getMetadata() {
    return metadata;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    NodeInfo nodeInfo = (NodeInfo) o;

    return nodeId.equals(nodeInfo.nodeId);
  }

  @Override
  public int hashCode() {
    return nodeId.hashCode();
  }

  @Override
  public String toString() {
    return nodeId + "_" + host + ":" + port;
  }
}
