/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
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

import java.util.Objects;

/**
 * Address of a clustered Vert.x node.
 *
 * @author Thomas Segismont
 */
public final class NodeAddress {

  private final String host;
  private final int port;

  public NodeAddress(String host, int port) {
    this.host = Objects.requireNonNull(host, "host is null");
    Arguments.requireInRange(port, 1, 65535, "Not an actual port: " + port);
    this.port = port;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  @Override
  public String toString() {
    return "NodeAddress{" +
      "host=" + host +
      ", port=" + port +
      '}';
  }
}
