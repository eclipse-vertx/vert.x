package io.vertx.internal.core.net.endpoint;

import io.vertx.core.Future;
import io.vertx.internal.core.ContextInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.endpoint.Endpoint;
import io.vertx.core.net.endpoint.EndpointResolver;

public interface EndpointResolverInternal extends EndpointResolver {

  Future<Endpoint> lookupEndpoint(ContextInternal ctx, Address address);

  /**
   * Check expired endpoints, this method is called by the client periodically to give the opportunity to trigger eviction
   * or refreshes.
   */
  void checkExpired();


}
