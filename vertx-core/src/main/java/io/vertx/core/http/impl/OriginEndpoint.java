package io.vertx.core.http.impl;

import io.vertx.core.spi.endpoint.EndpointBuilder;

public class OriginEndpoint<L> {

  final EndpointBuilder<L, OriginServer> builder;
  L list;

  public OriginEndpoint(EndpointBuilder<L, OriginServer> builder, L list) {
    this.builder = builder;
    this.list = list;
  }
}
