package io.vertx.core.http.impl;

import io.vertx.core.Vertx;
import io.vertx.core.net.Address;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.spi.endpoint.EndpointResolver;

public class OriginResolver implements AddressResolver<Origin> {

  @Override
  public EndpointResolver<Origin, ?, ?, ?> endpointResolver(Vertx vertx) {
    return null;
  }
}
