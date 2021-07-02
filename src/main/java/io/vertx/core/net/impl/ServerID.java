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

import java.io.Serializable;
import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServerID implements Serializable {

  public final int port;
  public final String host;
  public final boolean ssl;


  public ServerID(int port, String host, boolean ssl) {
    this.port = port;
    this.host = host;
    this.ssl = ssl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ServerID)) return false;

    ServerID that = (ServerID) o;
    return port == that.port && ssl == that.ssl && Objects.equals(host, that.host);
  }

  @Override
  public int hashCode() {
    int result = port;
    result = 31 * result + host.hashCode();
    if (ssl) {
      result *= 2;
    }
    return result;
  }

  public String toString() {
    return host + ":" + port;
  }
}
