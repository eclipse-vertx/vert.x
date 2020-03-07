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
package io.vertx.core.http.impl;

import io.vertx.core.net.SocketAddress;

final class EndpointKey {

  final boolean ssl;
  final SocketAddress serverAddr;
  final SocketAddress peerAddr;

  EndpointKey(boolean ssl, SocketAddress serverAddr, SocketAddress peerAddr) {
    if (serverAddr == null) {
      throw new NullPointerException("No null server address");
    }
    if (peerAddr == null) {
      throw new NullPointerException("No null peer address");
    }
    this.ssl = ssl;
    this.peerAddr = peerAddr;
    this.serverAddr = serverAddr;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EndpointKey that = (EndpointKey) o;
    return ssl == that.ssl && serverAddr.equals(that.serverAddr) && peerAddr.equals(that.peerAddr);
  }

  @Override
  public int hashCode() {
    int result = ssl ? 1 : 0;
    result = 31 * result + peerAddr.hashCode();
    result = 31 * result + serverAddr.hashCode();
    return result;
  }
}
