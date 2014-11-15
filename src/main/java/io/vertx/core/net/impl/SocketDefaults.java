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

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.net.Socket;

public class SocketDefaults {

  private static final Logger log = LoggerFactory.getLogger(SocketDefaults.class);

  public static final SocketDefaults instance = new SocketDefaults();

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
