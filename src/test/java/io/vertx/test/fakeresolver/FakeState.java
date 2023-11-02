package io.vertx.test.fakeresolver;

import io.vertx.core.spi.resolver.address.Endpoint;

import java.util.List;

public class FakeState {

  final String name;
  final List<Endpoint<FakeEndpoint>> endpoints;
  volatile boolean isValid;

  FakeState(String name, List<Endpoint<FakeEndpoint>> endpoints) {
    this.name = name;
    this.endpoints = endpoints;
    this.isValid = true;
  }
}
