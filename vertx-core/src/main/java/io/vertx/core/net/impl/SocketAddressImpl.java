package io.vertx.core.net.impl;

import io.vertx.core.impl.Arguments;
import io.vertx.core.net.SocketAddress;

import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SocketAddressImpl implements SocketAddress{

  private final String hostAddress;
  private final int port;

  public SocketAddressImpl(int port, String host) {
    Objects.requireNonNull(host, "no null host accepted");
    Arguments.require(!host.isEmpty(), "no empty host accepted");
    Arguments.requireInRange(port, 0, 65535, "port p must be in range 0 <= p <= 65535");
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
