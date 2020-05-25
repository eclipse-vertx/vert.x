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
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.impl.ClusterSerializable;

import java.util.Objects;

/**
 * Details about a clustered Vert.x node.
 *
 * @author Thomas Segismont
 */
public class NodeInfo implements ClusterSerializable {

  private String host;
  private int port;
  private JsonObject metadata;

  public NodeInfo() {
  }

  public NodeInfo(String host, int port, JsonObject metadata) {
    this.host = Objects.requireNonNull(host, "host is null");
    Arguments.requireInRange(port, 1, 65535, "Not an actual port: " + port);
    this.port = port;
    this.metadata = metadata;
  }

  public String host() {
    return host;
  }

  public int port() {
    return port;
  }

  public JsonObject metadata() {
    return metadata;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    NodeInfo nodeInfo = (NodeInfo) o;

    if (port != nodeInfo.port) return false;
    if (!host.equals(nodeInfo.host)) return false;
    return Objects.equals(metadata, nodeInfo.metadata);
  }

  @Override
  public int hashCode() {
    int result = host.hashCode();
    result = 31 * result + port;
    result = 31 * result + (metadata != null ? metadata.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "NodeInfo{" +
      "host='" + host + '\'' +
      ", port=" + port +
      ", metadata=" + metadata +
      '}';
  }

  @Override
  public void writeToBuffer(Buffer buffer) {
    buffer.appendInt(host.length()).appendString(host);
    buffer.appendInt(port);
    if (metadata == null) {
      buffer.appendInt(-1);
    } else {
      Buffer buf = metadata.toBuffer();
      buffer.appendInt(buf.length()).appendBuffer(buf);
    }
  }

  @Override
  public int readFromBuffer(int start, Buffer buffer) {
    int pos = start;
    int len = buffer.getInt(pos);
    pos += 4;
    host = buffer.getString(pos, pos + len);
    pos += len;
    port = buffer.getInt(pos);
    pos += 4;
    len = buffer.getInt(pos);
    pos += 4;
    if (len == 0) {
      metadata = new JsonObject();
    } else if (len > 0) {
      metadata = new JsonObject(buffer.getBuffer(pos, pos + len));
      pos += len;
    }
    return pos;
  }
}
