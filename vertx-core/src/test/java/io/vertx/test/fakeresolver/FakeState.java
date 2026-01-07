package io.vertx.test.fakeresolver;

import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.endpoint.EndpointBuilder;

public class FakeState<B> {

  final FakeRegistration registration;
  final String name;
  final EndpointBuilder<B, FakeServerEndpoint> builder;
  B endpoints;

  FakeState(String name, FakeRegistration registration, EndpointBuilder<B, FakeServerEndpoint> builder) {
    this.name = name;
    this.registration = registration;
    this.builder = builder;
  }

  void refresh() {
    EndpointBuilder<B, FakeServerEndpoint> b = builder;
    for (SocketAddress socketAddress : registration.servers) {
      b = b.addServer(new FakeServerEndpoint(socketAddress));
    }
    endpoints = b.build();
  }
}
