package io.vertx.core.http.impl;


import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.config.HttpClientConfig;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpChannelConnector;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.core.net.endpoint.EndpointResolver;
import io.vertx.core.net.impl.NetClientConfig;
import io.vertx.core.net.impl.tcp.NetClientBuilder;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.function.Function;

public final class HttpClientBuilderInternal implements HttpClientBuilder {

  private final VertxInternal vertx;
  private HttpClientOptions clientOptions;
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

  @Override
  public HttpClientBuilder with(HttpClientOptions options) {
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

  private EndpointResolver endpointResolver(HttpClientOptions co) {
    LoadBalancer _loadBalancer = loadBalancer;
    AddressResolver<?> _addressResolver = addressResolver;
    if (_addressResolver != null) {
      if (_loadBalancer == null) {
        _loadBalancer = LoadBalancer.ROUND_ROBIN;
      }
      return new EndpointResolverImpl<>(vertx, _addressResolver.endpointResolver(vertx), _loadBalancer, co.getKeepAliveTimeout() * 1000);
    }
    return null;
  }

  private HttpClientImpl createHttpClientImpl(EndpointResolver resolver,
                                              Handler<HttpConnection> connectionHandler,
                                              Function<HttpClientResponse, Future<RequestOptions>> redirectHandler,
                                              HttpClientOptions co2,
                                              LoadBalancer loadBalancer,
                                              PoolOptions po) {
    HttpClientConfig config = new HttpClientConfig(co2);
    HttpClientMetrics<?, ?, ?> metrics = vertx.metrics() != null ? vertx.metrics().createHttpClientMetrics(co2) : null;
    NetClientInternal tcpClient = new NetClientBuilder(vertx, netClientConfig(config)).metrics(metrics).build();
    HttpChannelConnector channelConnector = Http1xOrH2ChannelConnector.create(tcpClient, config, metrics);
    HttpClientImpl.Transport transport = new HttpClientImpl.Transport(
      connectionHandler,
      channelConnector,
      config.isVerifyHost(),
      config.isSsl(),
      config.getDefaultHost(),
      config.getDefaultPort(),
      config.getMaxRedirects(),
      config.getDefaultProtocolVersion(),
      config.getSslOptions()
    );
    return new HttpClientImpl(vertx, resolver, redirectHandler, metrics, po,
      config.getProxyOptions(), config.getNonProxyHosts(), transport, loadBalancer, config.getFollowAlternativeServices(), resolverKeepAlive) {
      @Override
      public HttpClientOptions options() {
        return co2;
      }
    };
  }

  private static NetClientConfig netClientConfig(HttpClientConfig httpConfig) {
    NetClientConfig config = new NetClientConfig();
    config.setTransportOptions(httpConfig.getTcpOptions());
    config.setSslOptions(httpConfig.getSslOptions() != null ? httpConfig.getSslOptions() : null);
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
    config.setSsl(httpConfig.isSsl());
    return config;
  }

  private Handler<HttpConnection> connectionHandler(HttpClientOptions options) {
    int windowSize = options.getHttp2ConnectionWindowSize();
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
    // Copy options here ????
    HttpClientOptions co = clientOptions != null ? clientOptions : new HttpClientOptions();
    PoolOptions po = poolOptions != null ? poolOptions : new PoolOptions();
    CloseFuture cf = resolveCloseFuture();
    HttpClientAgent client;
    Closeable closeable;
    EndpointResolver resolver = endpointResolver(co);
    Handler<HttpConnection> connectHandler = connectionHandler(co);
    if (co.isShared()) {
      CloseFuture closeFuture = new CloseFuture();
      client = vertx.createSharedResource("__vertx.shared.httpClients", co.getName(), closeFuture, cf_ -> {
        HttpClientImpl impl = createHttpClientImpl(resolver, connectHandler, redirectHandler, co, loadBalancer, po);
        cf_.add(completion -> impl.close().onComplete(completion));
        return impl;
      });
      client = new CleanableHttpClient((HttpClientInternal) client, vertx.cleaner(), (timeout, timeunit) -> closeFuture.close());
      closeable = closeFuture;
    } else {
      HttpClientImpl impl = createHttpClientImpl(resolver, connectHandler, redirectHandler, co, loadBalancer, po);
      closeable = impl;
      client = new CleanableHttpClient(impl, vertx.cleaner(), impl::shutdown);
    }
    cf.add(closeable);
    return client;
  }
}
