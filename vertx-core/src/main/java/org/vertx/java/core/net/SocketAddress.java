/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.vertx.java.core.net;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SocketAddress {

  private final String hostAddress;
  private final int port;

  public SocketAddress(int port, String host) {
    this.port = port;
    this.hostAddress = host;
  }

  public String getHostAddress() {
    return hostAddress;
  }

  public int getPort() {
    return port;
  }

  public String toString() {
    return hostAddress + ":" + port;
  }
}
