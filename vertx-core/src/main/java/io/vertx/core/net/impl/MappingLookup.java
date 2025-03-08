package io.vertx.core.net.impl;

import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.endpoint.EndpointBuilder;

import java.util.Collections;
import java.util.List;

class MappingLookup<A extends Address, B> {

  private static final List INITIAL = Collections.singletonList(new Object());

  final A address;
  final EndpointBuilder<B, SocketAddress> builder;
  List<SocketAddress> endpoints;
  B list;

  MappingLookup(A name, EndpointBuilder<B, SocketAddress> builder) {
    this.endpoints = INITIAL;
    this.address = name;
    this.builder = builder;
  }
}
