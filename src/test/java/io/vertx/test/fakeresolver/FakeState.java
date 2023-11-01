package io.vertx.test.fakeresolver;

import io.vertx.core.net.SocketAddress;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FakeState {

  final String name;
  final List<FakeEndpoint> endpoints;
  int index;
  volatile boolean valid;

  FakeState(String name, List<SocketAddress> endpoints) {
    this.name = name;
    this.endpoints = endpoints.stream().map(s -> new FakeEndpoint(this, s)).collect(Collectors.toList());
    this.valid = true;
  }

  FakeState(String name, SocketAddress... endpoints) {
    this(name, Arrays.asList(endpoints));
  }

  List<SocketAddress> addresses() {
    return endpoints.stream().map(e -> e.socketAddress).collect(Collectors.toList());
  }
}
