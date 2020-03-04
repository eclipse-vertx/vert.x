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
