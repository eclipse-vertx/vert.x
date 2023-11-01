package io.vertx.test.fakeresolver;

import java.util.List;

public class FakeState {

  final String name;
  final List<io.vertx.core.spi.net.Endpoint<FakeEndpoint>> endpoints;
  volatile boolean isValid;

  FakeState(String name, List<io.vertx.core.spi.net.Endpoint<FakeEndpoint>> endpoints) {
    this.name = name;
    this.endpoints = endpoints;
    this.isValid = true;
  }
}
