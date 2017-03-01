/*
 * Copyright (c) 2011-2017 The original author or authors
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

package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.net.impl.ServerID;

import java.io.Serializable;

/**
 * @author Rikard Björklind
 */
public class ClusterNodeInfo implements Serializable {
  // Make sure we can add new fields in future versions
  private static final long serialVersionUID = 1L;

  public String nodeId;
  public ServerID serverID;

  public ClusterNodeInfo() {
  }

  public ClusterNodeInfo(String nodeId, ServerID serverID) {
    this.nodeId = nodeId;
    this.serverID = serverID;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ClusterNodeInfo that = (ClusterNodeInfo) o;

    if (nodeId != null ? !nodeId.equals(that.nodeId) : that.nodeId != null) return false;
    return serverID != null ? serverID.equals(that.serverID) : that.serverID == null;
  }

  @Override
  public int hashCode() {
    int result = nodeId != null ? nodeId.hashCode() : 0;
    result = 31 * result + (serverID != null ? serverID.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return nodeId + ":" + serverID.toString();
  }
}
