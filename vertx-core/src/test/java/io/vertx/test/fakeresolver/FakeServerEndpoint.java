package io.vertx.test.fakeresolver;

import io.vertx.core.net.SocketAddress;

public class FakeServerEndpoint {

  final SocketAddress socketAddress;

  FakeServerEndpoint(SocketAddress socketAddress) {
    this.socketAddress = socketAddress;
  }

  public SocketAddress socketAddress() {
    return socketAddress;
  }

}
