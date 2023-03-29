package io.vertx.core.http.impl;

import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.pool.ConnectionManager;
import io.vertx.core.net.impl.pool.Endpoint;
import io.vertx.core.net.impl.pool.Lease;
import io.vertx.core.spi.naming.NameResolver;

import java.util.Optional;

class EndpointResolver<S> extends ConnectionManager<EndpointKey, Lease<HttpClientConnection>> {

  public EndpointResolver(HttpClientImpl httpClient, NameResolver<S> resolver) {
    super((key, dispose) -> {
      return new Endpoint<Lease<HttpClientConnection>>(() -> {
      }) {

        private Future<S> resolved;

        @Override
        public Future<Lease<HttpClientConnection>> requestConnection(ContextInternal ctx, long timeout) {
          if (resolved == null) {
            resolved = resolver.resolve(key.serverAddr.hostName());
          }
          return resolved.compose(state -> {
            SocketAddress origin = resolver.pickName(state);
            EndpointKey key2 = new EndpointKey(key.ssl, key.proxyOptions, origin, origin);
            Future<Lease<HttpClientConnection>> abc = httpClient.httpCM.getConnection(ctx, key2, timeout);
            httpClient.httpCM.withEndpoint(key, endpoint -> {
              Future<Lease<HttpClientConnection>> fut = endpoint.requestConnection(ctx, timeout);
              if (fut != null) {
                return Optional.of(fut);
              }
              return Optional.empty();
            });
            return abc;
          });
        }
      };
    });
  }
}
