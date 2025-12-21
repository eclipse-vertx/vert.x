package io.vertx.core.http.impl;

import io.vertx.core.Future;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.endpoint.EndpointBuilder;
import io.vertx.core.spi.endpoint.EndpointResolver;

import java.util.Map;

public class OriginEndpointResolver<L> implements EndpointResolver<Origin, OriginServer, OriginEndpoint<L>, L> {

  private final VertxInternal vertx;

  public OriginEndpointResolver(VertxInternal vertx) {
    this.vertx = vertx;
  }

  @Override
  public Origin tryCast(Address address) {
    return address instanceof Origin ? (Origin)address : null;
  }

  @Override
  public SocketAddress addressOf(OriginServer server) {
    return server.origin;
  }

  @Override
  public Map<String, String> propertiesOf(OriginServer server) {
    return EndpointResolver.super.propertiesOf(server);
  }

  @Override
  public Future<OriginEndpoint<L>> resolve(Origin address, EndpointBuilder<L, OriginServer> builder) {
    return vertx
      .nameResolver()
      .resolve(address.host)
      .map(addr -> {
        // Todo : ensure we only do a single resolver lookup with the subsequent actual HTTP request
        EndpointBuilder<L, OriginServer> builder2 = builder;
        builder2 = builder2.addServer(new OriginServer(SocketAddress.inetSocketAddress(address.port, address.host)));
        return new OriginEndpoint<>(builder, builder2.build());
      });
  }

  @Override
  public L endpoint(OriginEndpoint<L> state) {
    return state.list;
  }

  @Override
  public boolean isValid(OriginEndpoint<L> state) {
    // Need time eviction ???
    return true;
  }

  @Override
  public void dispose(OriginEndpoint<L> data) {
  }

  @Override
  public void close() {
  }
}
