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

import java.util.Objects;

/**
 * Registration data stored by the cluster manager.
 *
 * @author Thomas Segismont
 */
public final class RegistrationInfo {

  private final NodeInfo nodeInfo;
  private final String address;
  private final long seq;
  private final boolean localOnly;

  public RegistrationInfo(NodeInfo nodeInfo, String address, long seq, boolean localOnly) {
    Objects.requireNonNull(nodeInfo, "nodeInfo is null");
    Objects.requireNonNull(address, "address is null");
    this.nodeInfo = nodeInfo;
    this.address = address;
    this.seq = seq;
    this.localOnly = localOnly;
  }

  public NodeInfo getNodeInfo() {
    return nodeInfo;
  }

  public String getAddress() {
    return address;
  }

  public long getSeq() {
    return seq;
  }

  public boolean isLocalOnly() {
    return localOnly;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RegistrationInfo that = (RegistrationInfo) o;

    return seq == that.seq && nodeInfo.equals(that.nodeInfo) && address.equals(that.address);
  }

  @Override
  public int hashCode() {
    int result = nodeInfo.hashCode();
    result = 31 * result + address.hashCode();
    result = 31 * result + (int) (seq ^ (seq >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "RegistrationInfo{" +
      "nodeInfo=" + nodeInfo +
      ", address='" + address + '\'' +
      ", seq=" + seq +
      ", localOnly=" + localOnly +
      '}';
  }
}
