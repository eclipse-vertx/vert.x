/*
 * Copyright (c) 2011-2013 The original author or authors
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
