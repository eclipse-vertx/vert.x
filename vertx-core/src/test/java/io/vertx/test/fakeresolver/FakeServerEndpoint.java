package io.vertx.test.fakeresolver;

import io.vertx.core.net.SocketAddress;

public class FakeServerEndpoint {

  boolean available;
  final SocketAddress socketAddress;

  FakeServerEndpoint(SocketAddress socketAddress) {
    this.available = true;
    this.socketAddress = socketAddress;
  }

  public SocketAddress socketAddress() {
    return socketAddress;
  }

}
