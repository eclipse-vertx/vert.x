/*
 * Copyright (c) 2017 The original author or authors
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

import java.io.Serializable;

/**
 * @author Rikard Björklind
 */
public class ClusterNodeInfo implements Serializable {
  public String nodeId;
  public String host;
  public int port;

  public ClusterNodeInfo() {
  }

  public ClusterNodeInfo(String nodeId, String host, int port) {
    this.nodeId = nodeId;
    this.host = host;
    this.port = port;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ClusterNodeInfo that = (ClusterNodeInfo) o;

    if (port != that.port) return false;
    if (nodeId != null ? !nodeId.equals(that.nodeId) : that.nodeId != null) return false;
    return host != null ? host.equals(that.host) : that.host == null;
  }

  @Override
  public int hashCode() {
    int result = nodeId != null ? nodeId.hashCode() : 0;
    result = 31 * result + (host != null ? host.hashCode() : 0);
    result = 31 * result + port;
    return result;
  }

  @Override
  public String toString() {
    return nodeId + ":" + host + ":" + port;
  }
}
