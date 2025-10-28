package io.vertx.core.http.impl;


import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.config.Http1ClientConfig;
import io.vertx.core.http.impl.config.HttpClientConfig;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.core.net.endpoint.EndpointResolver;
import io.vertx.core.net.impl.NetClientConfig;
import io.vertx.core.net.impl.tcp.NetClientBuilder;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class HttpClientBuilderInternal implements HttpClientBuilder {

  private final VertxInternal vertx;
  private HttpClientConfig clientOptions;
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

  public HttpClientBuilder with(HttpClientConfig options) {
    this.clientOptions = new HttpClientConfig(options);
    return this;
  }

  @Override
  public HttpClientBuilder with(HttpClientOptions options) {
    this.clientOptions = new HttpClientConfig(options);
    return this;
  }

  @Override
  public HttpClientBuilder with(Http3ClientOptions options) {
    this.clientOptions = new HttpClientConfig(options);
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

  private HttpClientImpl createHttpClientImpl(HttpClientMetrics<?, ?, ?> metrics,
                                              EndpointResolver resolver,
                                              Function<HttpClientResponse, Future<RequestOptions>> redirectHandler,
                                              HttpClientImpl.Transport config,
                                              HttpClientImpl.Transport quicTransport) {
    boolean followAlternativeServices;
    ProxyOptions proxyOptions;
    List<String> nonProxyHosts;
    if (clientOptions != null) {
      proxyOptions = clientOptions.getProxyOptions();
      nonProxyHosts = clientOptions.getNonProxyHosts();
      followAlternativeServices = clientOptions.getFollowAlternativeServices();
    } else {
      proxyOptions = null;
      nonProxyHosts = null;
      followAlternativeServices = false;
    }
    PoolOptions po;
    po = poolOptions != null ? poolOptions : new PoolOptions();
    return new HttpClientImpl(vertx, resolver, redirectHandler, metrics, po,
      proxyOptions, nonProxyHosts, loadBalancer, followAlternativeServices, resolverKeepAlive, config, quicTransport) {
      @Override
      public HttpClientOptions options() {
        return null;
      }
    };
  }

  private static NetClientConfig netClientConfig(HttpClientConfig httpConfig) {
    NetClientConfig config = new NetClientConfig();
    config.setTransportOptions(httpConfig.getTcpOptions());
    config.setSslOptions(null);
    config.setSslEngineOptions(httpConfig.getSslEngineOptions() != null ? httpConfig.getSslEngineOptions().copy() : null);
    config.setConnectTimeout(httpConfig.getConnectTimeout());
    config.setMetricsName(httpConfig.getMetricsName());
    config.setProxyOptions(null);
    config.setNonProxyHosts(httpConfig.getNonProxyHosts() != null ? new ArrayList<>(httpConfig.getNonProxyHosts()) : null);
    config.setIdleTimeout(httpConfig.getIdleTimeout());
    config.setReadIdleTimeout(httpConfig.getReadIdleTimeout());
    config.setWriteIdleTimeout(httpConfig.getWriteIdleTimeout());
    config.setLogActivity(httpConfig.getLogActivity());
    config.setActivityLogDataFormat(httpConfig.getActivityLogDataFormat());
    config.setSsl(false);
    config.setSslOptions(null);
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
        connection.setWindowSize(windowSize);
        if (handler != null) {
          handler.handle(connection);
        }
      };
    }
    return handler;
  }

  @Override
  public HttpClientAgent build() {

    HttpClientConfig co = clientOptions;
    if (co == null) {
      // We assume default client configuration
      co = new HttpClientConfig(new HttpClientOptions());
    }

    // Copy options here ????
    HttpClientImpl.Transport quicTransport;
    HttpClientMetrics<?, ?, ?> metrics;
    if (co.getHttp3Config() != null) {
      metrics = vertx.metrics() != null ? vertx.metrics().createHttpClientMetrics(new HttpClientOptions()) : null;
      quicTransport = new HttpClientImpl.Transport(
        connectHandler,
        new Http3ChannelConnector(vertx, metrics, co),
        co.isVerifyHost(),
        true,
        co.getDefaultHost(),
        co.getDefaultPort(),
        co.getMaxRedirects(),
        HttpVersion.HTTP_3,
        co.getSslOptions()
      );
    } else {
      quicTransport = null;
      metrics = null;
      if (co == null) {
        co = new HttpClientConfig();
      }
    }

    HttpClientImpl.Transport transport;
    String shared;
    EndpointResolver resolver;
    if (co.getHttp1Config() != null && co.getHttp2Config() != null) {
      resolver = endpointResolver(co);
      shared = co.isShared() ? co.getName() : null;
      if (metrics == null && vertx.metrics() != null) {
        // Todo : change this (breaking)
        metrics = vertx.metrics().createHttpClientMetrics(new HttpClientOptions().setMetricsName(co.getMetricsName()));
      }
      NetClientConfig netClientConfig = netClientConfig(co);
      NetClientInternal tcpClient = new NetClientBuilder(vertx, netClientConfig.setProxyOptions(null)).metrics(metrics).build();
      Handler<HttpConnection> connectHandler = connectionHandler(co);
      transport = new HttpClientImpl.Transport(
        connectHandler,
        Http1xOrH2ChannelConnector.create(tcpClient, co, metrics),
        co.isVerifyHost(),
        co.isSsl(),
        co.getDefaultHost(),
        co.getDefaultPort(),
        co.getMaxRedirects(),
        co.getDefaultProtocolVersion(),
        co.getSslOptions()
      );
    } else {
      resolver = null;
      transport = null;
      shared = null;
    }


    CloseFuture cf = resolveCloseFuture();
    HttpClientAgent client;
    Closeable closeable;
    if (shared != null) {
      CloseFuture closeFuture = new CloseFuture();
      HttpClientMetrics<?, ?, ?> m = metrics;
      client = vertx.createSharedResource("__vertx.shared.httpClients", co.getName(), closeFuture, cf_ -> {
        HttpClientImpl impl = createHttpClientImpl(m, resolver, redirectHandler, transport, quicTransport);
        cf_.add(completion -> impl.close().onComplete(completion));
        return impl;
      });
      client = new CleanableHttpClient((HttpClientInternal) client, vertx.cleaner(), (timeout, timeunit) -> closeFuture.close());
      closeable = closeFuture;
    } else {
      HttpClientImpl impl = createHttpClientImpl(metrics, resolver, redirectHandler, transport, quicTransport);
      closeable = impl;
      client = new CleanableHttpClient(impl, vertx.cleaner(), impl::shutdown);
    }
    cf.add(closeable);
    return client;
  }
}
