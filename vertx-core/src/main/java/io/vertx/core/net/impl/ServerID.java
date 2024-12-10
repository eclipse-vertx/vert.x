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

package io.vertx.core.net.impl;

import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public final class ServerID {

  private final int port;
  private final String host;

  public ServerID(int port, String host) {
    this.port = port;
    this.host = host;
  }

  public int port() {
    return port;
  }

  public String host() {
    return host;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ServerID)) {
      return false;
    }
    ServerID that = (ServerID) o;
    return port == that.port && Objects.equals(host, that.host);
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
