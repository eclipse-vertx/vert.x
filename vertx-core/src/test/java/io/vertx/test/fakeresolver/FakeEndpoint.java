package io.vertx.test.fakeresolver;

import io.vertx.core.net.SocketAddress;

import java.util.List;

public class FakeEndpoint {

  final List<SocketAddress> addresses;
  final boolean valid;

  public FakeEndpoint(List<SocketAddress> addresses, boolean valid) {
    this.addresses = addresses;
    this.valid = valid;
  }
}
