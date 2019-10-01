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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.shareddata.impl.ClusterSerializable;

import java.util.Objects;

/**
 * Clustered {@link io.vertx.core.eventbus.EventBus} node details.
 *
 * @author Thomas Segismont
 */
public final class NodeInfo implements ClusterSerializable {

  private final String nodeId;
  private final String host;
  private final int port;

  public NodeInfo(String nodeId, String host, int port) {
    Objects.requireNonNull(nodeId, "nodeId is null");
    Objects.requireNonNull(host, "host is null");
    Arguments.requireInRange(port, 1, 65535, "Not an actual port");
    this.nodeId = nodeId;
    this.host = host;
    this.port = port;
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

  @Override
  public void writeToBuffer(Buffer buffer) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public int readFromBuffer(int pos, Buffer buffer) {
    throw new UnsupportedOperationException("Not implemented yet");
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
