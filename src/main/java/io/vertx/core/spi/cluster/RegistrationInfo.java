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
import io.vertx.core.shareddata.impl.ClusterSerializable;

import java.util.Objects;

/**
 * Registration data stored by the cluster manager.
 *
 * @author Thomas Segismont
 */
public class RegistrationInfo implements ClusterSerializable {

  private String nodeId;
  private long seq;
  private boolean localOnly;

  public RegistrationInfo() {
  }

  public RegistrationInfo(String nodeId, long seq, boolean localOnly) {
    Objects.requireNonNull(nodeId, "nodeId is null");
    this.nodeId = nodeId;
    this.seq = seq;
    this.localOnly = localOnly;
  }

  public String nodeId() {
    return nodeId;
  }

  public long seq() {
    return seq;
  }

  public boolean localOnly() {
    return localOnly;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RegistrationInfo that = (RegistrationInfo) o;

    if (seq != that.seq) return false;
    if (localOnly != that.localOnly) return false;
    return nodeId.equals(that.nodeId);
  }

  @Override
  public int hashCode() {
    int result = nodeId.hashCode();
    result = 31 * result + (int) (seq ^ (seq >>> 32));
    result = 31 * result + (localOnly ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "RegistrationInfo{" +
      "nodeId=" + nodeId +
      ", seq=" + seq +
      ", localOnly=" + localOnly +
      '}';
  }

  @Override
  public void writeToBuffer(Buffer buffer) {
    buffer.appendInt(nodeId.length()).appendString(nodeId);
    buffer.appendLong(seq);
    buffer.appendByte((byte) (localOnly ? 1 : 0));
  }

  @Override
  public int readFromBuffer(int start, Buffer buffer) {
    int pos = start;
    int len = buffer.getInt(pos);
    pos += 4;
    nodeId = buffer.getString(pos, pos + len);
    pos += len;
    seq = buffer.getLong(pos);
    pos += 8;
    localOnly = buffer.getByte(pos) > 0;
    pos += 1;
    return pos;
  }
}
