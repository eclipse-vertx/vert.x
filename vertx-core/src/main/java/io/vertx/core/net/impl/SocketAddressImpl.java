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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SocketAddressImpl that = (SocketAddressImpl) o;

    if (port != that.port) return false;
    if (hostAddress != null ? !hostAddress.equals(that.hostAddress) : that.hostAddress != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = hostAddress != null ? hostAddress.hashCode() : 0;
    result = 31 * result + port;
    return result;
  }
}
