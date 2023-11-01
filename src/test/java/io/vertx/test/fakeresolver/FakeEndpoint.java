package io.vertx.test.fakeresolver;

import io.vertx.core.http.ResolvingHttpClientTest;
import io.vertx.core.net.SocketAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeEndpoint {

  final SocketAddress socketAddress;

  FakeEndpoint(SocketAddress socketAddress) {
    this.socketAddress = socketAddress;
  }

  public SocketAddress socketAddress() {
    return socketAddress;
  }

}
