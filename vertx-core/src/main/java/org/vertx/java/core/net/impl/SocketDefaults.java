package org.vertx.java.core.net.impl;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.net.Socket;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SocketDefaults {

  private static final Logger log = LoggerFactory.getLogger(SocketDefaults.class);

  public static SocketDefaults instance = new SocketDefaults();

  private boolean tcpNoDelay = true;
  private int tcpSendBufferSize;
  private int tcpReceiveBufferSize;
  private boolean tcpKeepAlive;
  private boolean reuseAddress;
  private int soLinger;
  private int trafficClass;

  private SocketDefaults() {
    try {
      Socket csock = new Socket();
      tcpNoDelay = csock.getTcpNoDelay();
      tcpSendBufferSize = csock.getSendBufferSize();
      tcpReceiveBufferSize = csock.getReceiveBufferSize();
      tcpKeepAlive = csock.getKeepAlive();
      reuseAddress = csock.getReuseAddress();
      soLinger = csock.getSoLinger();
      trafficClass = csock.getTrafficClass();
    } catch (Exception e) {
      log.warn("Failed to read socket defaults, using Vert.x defaults instead");
      tcpNoDelay = true;
      tcpSendBufferSize = 8 * 1024;
      tcpReceiveBufferSize = 32 * 1024;
      tcpKeepAlive = false;
      reuseAddress = false;
      soLinger = -1;
      trafficClass = 0;
    }
  }

  public boolean isTcpNoDelay() {
    return tcpNoDelay;
  }

  public int getTcpSendBufferSize() {
    return tcpSendBufferSize;
  }

  public int getTcpReceiveBufferSize() {
    return tcpReceiveBufferSize;
  }

  public boolean isTcpKeepAlive() {
    return tcpKeepAlive;
  }

  public boolean isReuseAddress() {
    return reuseAddress;
  }

  public int getSoLinger() {
    return soLinger;
  }

  public int getTrafficClass() {
    return trafficClass;
  }
}
