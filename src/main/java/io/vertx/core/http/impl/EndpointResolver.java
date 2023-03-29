package io.vertx.core.http.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.SrvRecord;
import io.vertx.core.impl.AddressResolver;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.pool.ConnectionManager;
import io.vertx.core.net.impl.pool.Endpoint;
import io.vertx.core.net.impl.pool.EndpointProvider;
import io.vertx.core.net.impl.pool.Lease;
import io.vertx.core.spi.naming.NameResolver;

import java.net.InetSocketAddress;
import java.util.List;

class EndpointResolver<S> extends ConnectionManager<EndpointKey, Lease<HttpClientConnection>> {

  public EndpointResolver(HttpClientImpl httpClient, NameResolver<S> resolver) {
    super((ctx, key, dispose) -> {
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
            return httpClient.httpCM.getConnection(ctx, key2, timeout);
          });
        }
      };
    });
  }
}
