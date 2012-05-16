/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.net.impl;

import java.beans.ConstructorProperties;
import java.io.Serializable;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServerID implements Serializable {

  private static final long serialVersionUID = 1L;

  private final int port;
  
  private final String host;

  @ConstructorProperties({"port", "host"})
  public ServerID(int port, String host) {
    this.port = port;
    this.host = host;
  }

  public int getPort() {
	  return port;
  }

  public String getHost() {
	  return host;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

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
