package io.vertx.core.http.impl;

import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.pool.ConnectionManager;
import io.vertx.core.net.impl.pool.Endpoint;
import io.vertx.core.net.impl.pool.EndpointProvider;
import io.vertx.core.net.impl.pool.Lease;
import io.vertx.core.spi.naming.NameResolver;

import java.util.Optional;

class EndpointResolver<S> extends ConnectionManager<EndpointKey, Lease<HttpClientConnection>> {

  public EndpointResolver(HttpClientImpl httpClient, NameResolver<S> resolver) {
    super(new EndpointProvider<>() {

      private final EndpointProvider<EndpointKey, Lease<HttpClientConnection>> provider = httpClient.httpCM.provider;
      private final ConnectionManager<MyEndpointKey, Lease<HttpClientConnection>> httpCM = new ConnectionManager<>(new EndpointProvider<MyEndpointKey, Lease<HttpClientConnection>>() {
        @Override
        public Endpoint<Lease<HttpClientConnection>> create(MyEndpointKey key, Runnable dispose) {
          return provider.create(key, new Runnable() {
            @Override
            public void run() {
              key.cleanup();
              dispose.run();
            }
          });
        }
      });

      abstract class MyEndpointKey extends EndpointKey {
        public MyEndpointKey(boolean ssl, ProxyOptions proxyOptions, SocketAddress serverAddr, SocketAddress peerAddr) {
          super(ssl, proxyOptions, serverAddr, peerAddr);
        }
        abstract void cleanup();
      }

      @Override
      public Endpoint<Lease<HttpClientConnection>> create(EndpointKey key, Runnable dispose) {
        return new Endpoint<>(() -> {
        }) {
          private Future<S> resolved;
          @Override
          public Future<Lease<HttpClientConnection>> requestConnection(ContextInternal ctx, long timeout) {
            if (resolved == null) {
              resolved = resolver.resolve(key.serverAddr.hostName());
            }
            return resolved.compose(state -> {
              SocketAddress origin = resolver.pickAddress(state);
              MyEndpointKey key2 = new MyEndpointKey(key.ssl, key.proxyOptions, origin, origin) {
                @Override
                void cleanup() {
                  if (resolver.removeAddress(state, origin)) {
                    resolver.dispose(state);
                  }
                }
              };
              return httpCM.withEndpoint(key2, endpoint -> {
                Future<Lease<HttpClientConnection>> fut = endpoint.requestConnection(ctx, timeout);
                if (fut != null) {
                  return Optional.of(fut);
                }
                return Optional.empty();
              });
            });
          }
        };
      }
    });
  }
}
