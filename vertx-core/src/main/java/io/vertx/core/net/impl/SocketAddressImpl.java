package io.vertx.core.net.impl;

import io.vertx.core.net.SocketAddress;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SocketAddressImpl implements SocketAddress{

  private final String hostAddress;
  private final int port;

  public SocketAddressImpl(int port, String host) {
    this.port = port;
    this.hostAddress = host;
  }

  public String hostAddress() {
    return hostAddress;
  }

  public int hostPort() {
    return port;
  }

  public String toString() {
    return hostAddress + ":" + port;
  }
}
