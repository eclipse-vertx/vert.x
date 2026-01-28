package io.vertx.core.http.impl;


import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.http.Http1ClientConfig;
import io.vertx.core.http.Http2ClientConfig;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpClientTransport;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.NetworkLogging;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.core.net.endpoint.EndpointResolver;
import io.vertx.core.net.TcpClientConfig;
import io.vertx.core.net.impl.tcp.NetClientBuilder;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class HttpClientBuilderInternal implements HttpClientBuilder {

  private final VertxInternal vertx;
  private HttpClientConfig clientConfig;
  private HttpClientOptions clientOptions; // To be removed
  private PoolOptions poolOptions;
  private Handler<HttpConnection> connectHandler;
  private Function<HttpClientResponse, Future<RequestOptions>> redirectHandler;
  private AddressResolver<?> addressResolver;
  private LoadBalancer loadBalancer;
  private Duration resolverKeepAlive;

  public HttpClientBuilderInternal(VertxInternal vertx) {
    this.vertx = vertx;
    this.resolverKeepAlive = Duration.ofSeconds(10);
  }

  public HttpClientBuilder with(HttpClientConfig config) {
    this.clientConfig = new HttpClientConfig(config);
    return this;
  }

  @Override
  public HttpClientBuilder with(HttpClientOptions options) {
    this.clientConfig = new HttpClientConfig(options);
    this.clientOptions = options;
    return this;
  }

  @Override
  public HttpClientBuilder with(PoolOptions options) {
    this.poolOptions = options;
    return this;
  }

  @Override
  public HttpClientBuilder withConnectHandler(Handler<HttpConnection> handler) {
    this.connectHandler = handler;
    return this;
  }

  @Override
  public HttpClientBuilder withRedirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler) {
    this.redirectHandler = handler;
    return this;
  }

  @Override
  public HttpClientBuilder withAddressResolver(AddressResolver<?> resolver) {
    this.addressResolver = resolver;
    return this;
  }

  @Override
  public HttpClientBuilder withLoadBalancer(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
    return this;
  }

  public HttpClientBuilderInternal resolverTtl(Duration ttl) {
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("Invalid TTL");
    }
    this.resolverKeepAlive = ttl;
    return this;
  }

  private CloseFuture resolveCloseFuture() {
    ContextInternal context = vertx.getContext();
    return context != null ? context.closeFuture() : vertx.closeFuture();
  }

  private EndpointResolver endpointResolver(HttpClientConfig co) {
    LoadBalancer _loadBalancer = loadBalancer;
    AddressResolver<?> _addressResolver = addressResolver;
    if (_addressResolver != null) {
      if (_loadBalancer == null) {
        _loadBalancer = LoadBalancer.ROUND_ROBIN;
      }
      return new EndpointResolverImpl<>(vertx, _addressResolver.endpointResolver(vertx), _loadBalancer, co.getHttp1Config().getKeepAliveTimeout().toMillis());
    }
    return null;
  }

  private HttpClientImpl createHttpClientImpl(HttpClientConfig clientConfig,
                                              HttpClientMetrics<?, ?, ?> metrics,
                                              EndpointResolver resolver,
                                              Function<HttpClientResponse, Future<RequestOptions>> redirectHandler,
                                              HttpClientTransport tcpTransport,
                                              HttpClientTransport quicTransport) {
    boolean followAlternativeServices;
    ProxyOptions proxyOptions;
    List<String> nonProxyHosts;
    if (clientConfig != null) {
      proxyOptions = clientConfig.getProxyOptions();
      nonProxyHosts = clientConfig.getNonProxyHosts();
      followAlternativeServices = clientConfig.getFollowAlternativeServices();
    } else {
      proxyOptions = null;
      nonProxyHosts = null;
      followAlternativeServices = false;
    }
    PoolOptions po;
    po = poolOptions != null ? poolOptions : new PoolOptions();
    HttpClientOptions options = HttpClientBuilderInternal.this.clientOptions;
    Handler<HttpConnection> connectHandler = connectionHandler(clientConfig);
    return new HttpClientImpl(
      vertx,
      resolver,
      redirectHandler,
      metrics,
      po,
      proxyOptions,
      nonProxyHosts,
      loadBalancer,
      followAlternativeServices,
      resolverKeepAlive,
      clientConfig.isVerifyHost(),
      clientConfig.isSsl(),
      clientConfig.getDefaultHost(),
      clientConfig.getDefaultPort(),
      clientConfig.getMaxRedirects(),
      clientConfig.getDefaultProtocolVersion(),
      clientConfig.getSslOptions(),
      connectHandler,
      tcpTransport,
      quicTransport) {
      @Override
      public HttpClientOptions options() {
        return options == null ? new HttpClientOptions() : new HttpClientOptions(options);
      }
    };
  }

  private static TcpClientConfig netClientConfig(HttpClientConfig httpConfig) {
    TcpClientConfig config = new TcpClientConfig();
    config.setTransportOptions(httpConfig.getTcpOptions());
    config.setSslEngineOptions(httpConfig.getSslEngineOptions() != null ? httpConfig.getSslEngineOptions().copy() : null);
    config.setConnectTimeout(httpConfig.getConnectTimeout());
    config.setMetricsName(httpConfig.getMetricsName());
    config.setProxyOptions(null);
    config.setNonProxyHosts(httpConfig.getNonProxyHosts() != null ? new ArrayList<>(httpConfig.getNonProxyHosts()) : null);
    config.setIdleTimeout(httpConfig.getIdleTimeout());
    config.setReadIdleTimeout(httpConfig.getReadIdleTimeout());
    config.setWriteIdleTimeout(httpConfig.getWriteIdleTimeout());
    config.setNetworkLogging(httpConfig.getNetworkLogging() != null ? new NetworkLogging(httpConfig.getNetworkLogging()) : null);
    config.setSsl(false);
    return config;
  }

  private Handler<HttpConnection> connectionHandler(HttpClientConfig options) {
    int windowSize;
    if (options.getHttp2Config() != null) {
      windowSize = options.getHttp2Config().getConnectionWindowSize();
    } else {
      windowSize = 0;
    }

    Handler<HttpConnection> handler = connectHandler;
    if (windowSize > 0) {
      return connection -> {
        if (connection.protocolVersion() == HttpVersion.HTTP_2) {
          connection.setWindowSize(windowSize);
        }
        if (handler != null) {
          handler.handle(connection);
        }
      };
    }
    return handler;
  }

  @Override
  public HttpClientAgent build() {

    HttpClientConfig co = clientConfig;
    if (co == null) {
      // We assume default client configuration
      co = new HttpClientConfig(new HttpClientOptions());
    }

    HttpClientTransport quicTransport;
    HttpClientMetrics<?, ?, ?> metrics;
    if (co.getSupportedVersions().contains(HttpVersion.HTTP_3)) {
      metrics = vertx.metrics() != null ? vertx.metrics().createHttpClientMetrics(new HttpClientOptions()) : null;
      quicTransport = new Http3ClientTransport(vertx, metrics, co);
    } else {
      quicTransport = null;
      metrics = null;
    }

    HttpClientTransport transport;
    String shared;
    EndpointResolver resolver;
    List<HttpVersion> supportedVersions = co.getSupportedVersions();
    if (supportedVersions.contains(HttpVersion.HTTP_1_0) || supportedVersions.contains(HttpVersion.HTTP_1_1) || supportedVersions.contains(HttpVersion.HTTP_2)) {
      resolver = endpointResolver(co);
      shared = co.isShared() ? co.getName() : null;
      if (metrics == null && vertx.metrics() != null) {
        // Todo : change this (breaking)
        metrics = vertx.metrics().createHttpClientMetrics(new HttpClientOptions().setMetricsName(co.getMetricsName()));
      }
      TcpClientConfig netClientConfig = netClientConfig(co);
      NetClientInternal tcpClient = new NetClientBuilder(vertx, netClientConfig.setProxyOptions(null), null).metrics(metrics).build();
      transport = new Http1xOrH2ClientTransport(
        tcpClient,
        co.getTracingPolicy(),
        co.isDecompressionSupported(),
        co.getNetworkLogging() != null,
        co.getNetworkLogging() != null ? co.getNetworkLogging().getDataFormat() : null,
        co.isForceSni(),
        supportedVersions.contains(HttpVersion.HTTP_1_1) || supportedVersions.contains(HttpVersion.HTTP_1_0) ? (co.getHttp1Config() != null ? co.getHttp1Config() : new Http1ClientConfig()) : null,
        supportedVersions.contains(HttpVersion.HTTP_2) ? (co.getHttp2Config() != null ? co.getHttp2Config() : new Http2ClientConfig()) : null,
        co.getIdleTimeout(),
        co.getReadIdleTimeout(),
        co.getWriteIdleTimeout(),
        metrics
      );
    } else {
      resolver = null;
      transport = null;
      shared = null;
    }


    HttpClientConfig co2 = co;
    CloseFuture cf = resolveCloseFuture();
    HttpClientAgent client;
    Closeable closeable;
    if (shared != null) {
      CloseFuture closeFuture = new CloseFuture();
      HttpClientMetrics<?, ?, ?> m = metrics;
      client = vertx.createSharedResource("__vertx.shared.httpClients", co.getName(), closeFuture, cf_ -> {
        HttpClientImpl impl = createHttpClientImpl(co2, m, resolver, redirectHandler, transport, quicTransport);
        cf_.add(completion -> impl.close().onComplete(completion));
        return impl;
      });
      client = new CleanableHttpClient((HttpClientInternal) client, vertx.cleaner(), (timeout, timeunit) -> closeFuture.close());
      closeable = closeFuture;
    } else {
      HttpClientImpl impl = createHttpClientImpl(co2, metrics, resolver, redirectHandler, transport, quicTransport);
      closeable = impl;
      client = new CleanableHttpClient(impl, vertx.cleaner(), impl::shutdown);
    }
    cf.add(closeable);
    return client;
  }
}
