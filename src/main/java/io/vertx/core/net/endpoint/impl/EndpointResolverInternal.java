package io.vertx.core.net.endpoint.impl;

import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.endpoint.Endpoint;
import io.vertx.core.net.endpoint.EndpointResolver;

public interface EndpointResolverInternal<A extends Address> extends EndpointResolver<A> {

  Future<Endpoint> lookupEndpoint(ContextInternal ctx, A address);

  /**
   * Check expired endpoints, this method is called by the client periodically to give the opportunity to trigger eviction
   * or refreshes.
   */
  void checkExpired();


}
