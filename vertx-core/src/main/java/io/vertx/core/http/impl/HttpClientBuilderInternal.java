package io.vertx.core.http.impl;


import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpChannelConnector;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.core.net.endpoint.EndpointResolver;
import io.vertx.core.net.impl.tcp.NetClientBuilder;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.function.Function;

public final class HttpClientBuilderInternal implements HttpClientBuilder {

  private final VertxInternal vertx;
  private HttpClientOptions clientOptions;
  private Http3ClientOptions http3ClientOptions;
  private PoolOptions poolOptions;
  private Handler<HttpConnection> connectHandler;
  private Function<HttpClientResponse, Future<RequestOptions>> redirectHandler;
  private AddressResolver<?> addressResolver;
  private LoadBalancer loadBalancer = null;

  public HttpClientBuilderInternal(VertxInternal vertx) {
    this.vertx = vertx;
  }

  @Override
  public HttpClientBuilder with(HttpClientOptions options) {
    this.clientOptions = options;
    return this;
  }

  @Override
  public HttpClientBuilder with(Http3ClientOptions options) {
    this.http3ClientOptions = options;
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

  private CloseFuture resolveCloseFuture() {
    ContextInternal context = vertx.getContext();
    return context != null ? context.closeFuture() : vertx.closeFuture();
  }

  private EndpointResolver endpointResolver(HttpClientOptions co) {
    LoadBalancer _loadBalancer = loadBalancer;
    AddressResolver<?> _addressResolver = addressResolver;
    if (_loadBalancer != null) {
      if (_addressResolver == null) {
        _addressResolver = vertx.nameResolver();
      }
    } else {
      if (_addressResolver != null) {
        _loadBalancer = LoadBalancer.ROUND_ROBIN;
      }
    }
    if (_addressResolver != null) {
      return new EndpointResolverImpl<>(vertx, _addressResolver.endpointResolver(vertx), _loadBalancer, co.getKeepAliveTimeout() * 1000);
    }
    return null;
  }

  private HttpClientImpl createHttpClientImpl(EndpointResolver resolver,
                                              Handler<HttpConnection> connectHandler,
                                              HttpClientMetrics<?, ?, ?> metrics,
                                              Function<HttpClientResponse, Future<RequestOptions>> redirectHandler,
                                              HttpChannelConnector connector,
                                              HttpClientImpl.Config config,
                                              ProxyOptions proxyOptions,
                                              ClientSSLOptions sslOptions,
                                              PoolOptions poolOptions) {
    return new HttpClientImpl(vertx, connectHandler, redirectHandler, connector, metrics, resolver, poolOptions, proxyOptions, sslOptions, config);
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
    EndpointResolver resolver;
    String shared;
    HttpChannelConnector channelConnector;
    HttpClientImpl.Config config = new HttpClientImpl.Config();
    Handler<HttpConnection> connectHandler;
    HttpClientMetrics<?, ?, ?> metrics;
    ProxyOptions proxyOptions;
    ClientSSLOptions sslOptions;
    PoolOptions po;
    if (http3ClientOptions != null) {
      Http3ClientOptions co = new Http3ClientOptions(http3ClientOptions);
      config.nonProxyHosts = null;
      config.verifyHost = false;
      config.defaultSsl = true;
      config.defaultHost = "localhost";
      config.defaultPort = 8443;
      config.maxRedirects = HttpClientOptions.DEFAULT_MAX_REDIRECTS;
      config.initialPoolKind = 1; // Multiplexed
      shared = null;
      connectHandler = this.connectHandler;
      resolver = null;
      metrics = null;
      sslOptions = co.getSslOptions();
      po = new PoolOptions();
      proxyOptions = null;
      channelConnector = new Http3ChannelConnector(vertx, co);
    } else {
      HttpClientOptions co = clientOptions != null ? clientOptions : new HttpClientOptions();
      resolver = endpointResolver(co);
      shared = co.isShared() ? co.getName() : null;
      metrics = vertx.metrics() != null ? vertx.metrics().createHttpClientMetrics(co) : null;
      NetClientInternal tcpClient = new NetClientBuilder(vertx, new NetClientOptions(co).setProxyOptions(null)).metrics(metrics).build();
      channelConnector = new Http1xOrH2ChannelConnector(tcpClient, co, metrics);
      config.nonProxyHosts = co.getNonProxyHosts();
      config.verifyHost = co.isVerifyHost();
      config.defaultSsl = co.isSsl();
      config.defaultHost = co.getDefaultHost();
      config.defaultPort = co.getDefaultPort();
      config.maxRedirects = co.getMaxRedirects();
      config.initialPoolKind = co.getProtocolVersion() == HttpVersion.HTTP_2 ? 1 : 0;
      connectHandler = connectionHandler(co);
      sslOptions = co.getSslOptions();
      proxyOptions = co.getProxyOptions();
      po = poolOptions != null ? poolOptions : new PoolOptions();
    }
    CloseFuture cf = resolveCloseFuture();
    HttpClientAgent client;
    Closeable closeable;
    if (shared != null) {
      CloseFuture closeFuture = new CloseFuture();
      client = vertx.createSharedResource("__vertx.shared.httpClients", shared, closeFuture, cf_ -> {
        HttpClientImpl impl = createHttpClientImpl(resolver, connectHandler, metrics, redirectHandler, channelConnector, config, proxyOptions, sslOptions, po);
        cf_.add(completion -> impl.close().onComplete(completion));
        return impl;
      });
      client = new CleanableHttpClient((HttpClientInternal) client, vertx.cleaner(), (timeout, timeunit) -> closeFuture.close());
      closeable = closeFuture;
    } else {
      HttpClientImpl impl = createHttpClientImpl(resolver, connectHandler, metrics, redirectHandler, channelConnector, config, proxyOptions, sslOptions, po);
      closeable = impl;
      client = new CleanableHttpClient(impl, vertx.cleaner(), impl::shutdown);
    }
    cf.add(closeable);
    return client;
  }
}
