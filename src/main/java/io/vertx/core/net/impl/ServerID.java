/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import java.io.Serializable;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServerID implements Serializable {

  public int port;
  public String host;

  public ServerID(int port, String host) {
    this.port = port;
    this.host = host;
  }

  public ServerID() {
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof ServerID)) return false;

    ServerID serverID = (ServerID) o;

    if (port != serverID.port) return false;
    if (!host.equals(serverID.host)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = port;
    result = 31 * result + host.hashCode();
    return result;
  }

  public String toString() {
    return host + ":" + port;
  }
}
