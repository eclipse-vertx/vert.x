package io.vertx.test.fakeresolver;

import io.vertx.core.net.SocketAddress;

public class FakeEndpoint {

  final SocketAddress socketAddress;

  FakeEndpoint(SocketAddress socketAddress) {
    this.socketAddress = socketAddress;
  }

  public SocketAddress socketAddress() {
    return socketAddress;
  }

}
