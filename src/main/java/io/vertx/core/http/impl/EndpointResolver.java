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
import io.vertx.core.net.impl.pool.Lease;

import java.net.InetSocketAddress;
import java.util.List;

class EndpointResolver extends ConnectionManager<EndpointKey, Lease<HttpClientConnection>> {

  private final HttpClientImpl httpClient;

  public EndpointResolver(HttpClientImpl httpClient) {
    super((ctx, key, dispose) -> {
      return new Endpoint<Lease<HttpClientConnection>>(() -> {
      }) {

        private final DnsClient dnsClient = httpClient.vertx().createDnsClient(53530, "127.0.0.1");
        private Future<List<SrvRecord>> resolved;
        private int index;

        @Override
        public Future<Lease<HttpClientConnection>> requestConnection(ContextInternal ctx, long timeout) {
          if (resolved == null) {
            resolved = dnsClient.resolveSRV(key.serverAddr.hostName());
          }
          return resolved.compose(list -> {
            if (list.size() > 0) {
              SrvRecord record = list.get((index++) % list.size());
              String host = record.target();
              int port = record.port();
              SocketAddress origin = SocketAddress.inetSocketAddress(port, host);
              EndpointKey key2 = new EndpointKey(key.ssl, key.proxyOptions, origin, origin);
              return httpClient.httpCM.getConnection(ctx, key2, timeout);
            }
            return Future.failedFuture("Not resolved");
          });
        }
      };
    });
    this.httpClient = httpClient;
  }
}
