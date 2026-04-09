package io.vertx.core.http.impl;


import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.http.Http1ClientConfig;
import io.vertx.core.http.Http2ClientConfig;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.http.impl.quic.QuicHttpClientTransport;
import io.vertx.core.http.impl.tcp.TcpHttpClientTransport;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpClientTransport;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.LogConfig;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.core.net.endpoint.EndpointResolver;
import io.vertx.core.net.TcpClientConfig;
import io.vertx.core.net.impl.tcp.NetClientBuilder;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

public final class HttpClientBuilderInternal implements HttpClientBuilder {

  private final VertxInternal vertx;
  private HttpClientConfig clientConfig;
  private HttpClientOptions clientOptions; // To be removed
  private ClientSSLOptions sslOptions;
  private SSLEngineOptions sslEngineOptions;
  private boolean forceSNI;
  private PoolOptions poolOptions;
  private Handler<HttpConnection> connectHandler;
  private Function<HttpClientResponse, Future<RequestOptions>> redirectHandler;
  private AddressResolver<?> addressResolver;
  private LoadBalancer loadBalancer;
  private Duration resolverIdleTimeout;

  public HttpClientBuilderInternal(VertxInternal vertx) {
    this.vertx = vertx;
    this.resolverIdleTimeout = Duration.ofSeconds(10);
  }

  public HttpClientBuilderInternal with(HttpClientConfig config) {
    this.clientConfig = config;
    this.forceSNI = false;
    return this;
  }

  @Override
  public HttpClientBuilderInternal with(HttpClientOptions options) {
    if (options != null) {
      this.clientConfig = new HttpClientConfig(options);
      this.sslOptions = options.getSslOptions();
      this.sslEngineOptions = options.getSslEngineOptions();
      this.clientOptions = options;
      this.forceSNI = options.isForceSni();
    } else {
      this.clientConfig = null;
      this.sslOptions = null;
      this.sslEngineOptions = null;
      this.clientOptions = null;
      this.forceSNI = false;
    }
    return this;
  }

  @Override
  public HttpClientBuilderInternal with(PoolOptions options) {
    this.poolOptions = options;
    return this;
  }

  @Override
  public HttpClientBuilderInternal with(ClientSSLOptions options) {
    this.sslOptions = options;
    return this;
  }

  @Override
  public HttpClientBuilderInternal with(SSLEngineOptions engine) {
    this.sslEngineOptions = engine;
    return this;
  }

  @Override
  public HttpClientBuilderInternal withConnectHandler(Handler<HttpConnection> handler) {
    this.connectHandler = handler;
    return this;
  }

  @Override
  public HttpClientBuilderInternal withRedirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler) {
    this.redirectHandler = handler;
    return this;
  }

  @Override
  public HttpClientBuilderInternal withAddressResolver(AddressResolver<?> resolver) {
    this.addressResolver = resolver;
    return this;
  }

  @Override
  public HttpClientBuilderInternal withLoadBalancer(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
    return this;
  }

  public HttpClientBuilderInternal resolverIdleTimeout(Duration timeout) {
    if (timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Invalid resolver idle timeout");
    }
    this.resolverIdleTimeout = timeout;
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
      return new EndpointResolverImpl<>(vertx, _addressResolver.endpointResolver(vertx), _loadBalancer, resolverIdleTimeout.toMillis());
    }
    return null;
  }

  private HttpClientImpl createHttpClientImpl(HttpClientConfig config,
                                              ClientSSLOptions sslOptions,
                                              HttpClientMetrics<?, ?> httpMetrics,
                                              EndpointResolver resolver,
                                              Function<HttpClientResponse, Future<RequestOptions>> redirectHandler,
                                              HttpClientTransport tcpTransport,
                                              HttpClientTransport quicTransport) {
    boolean followAlternativeServices;
    ProxyOptions proxyOptions;
    List<String> nonProxyHosts;
    if (config != null) {
      proxyOptions = config.getTcpConfig().getProxyOptions();
      nonProxyHosts = config.getTcpConfig().getNonProxyHosts();
      followAlternativeServices = config.getFollowAlternativeServices();
    } else {
      proxyOptions = null;
      nonProxyHosts = null;
      followAlternativeServices = false;
    }
    PoolOptions po;
    po = poolOptions != null ? poolOptions : new PoolOptions();
    HttpClientOptions options = HttpClientBuilderInternal.this.clientOptions;
    Handler<HttpConnection> connectHandler = connectionHandler(config);
    List<HttpVersion> versions = config.getVersions();
    return new LegacyHttpClient(
      vertx,
      resolver,
      redirectHandler,
      httpMetrics,
      po,
      proxyOptions,
      nonProxyHosts,
      loadBalancer,
      followAlternativeServices,
      resolverIdleTimeout,
      config.isVerifyHost(),
      config.isSsl(),
      config.getDefaultHost(),
      config.getDefaultPort(),
      config.getMaxRedirects(),
      versions,
      sslOptions,
      connectHandler,
      tcpTransport,
      quicTransport,
      config,
      options);
  }

  private static class LegacyHttpClient extends HttpClientImpl {
    private final HttpClientConfig config;
    private final HttpClientOptions options;
    public LegacyHttpClient(
      VertxInternal vertx,
      EndpointResolver resolver,
      Function<HttpClientResponse, Future<RequestOptions>> redirectHandler, HttpClientMetrics<?, ?> httpMetrics,
      PoolOptions poolOptions,
      ProxyOptions defaultProxyOptions,
      List<String> nonProxyHosts,
      LoadBalancer loadBalancer,
      boolean followAlternativeServices,
      Duration resolverIdeTimeout,
      boolean verifyHost,
      boolean defaultSsl,
      String defaultHost,
      int defaultPort,
      int maxRedirects,
      List<HttpVersion> versions,
      ClientSSLOptions sslOptions,
      Handler<HttpConnection> connectHandler,
      HttpClientTransport tcpTransport,
      HttpClientTransport quicTransport,
      HttpClientConfig config,
      HttpClientOptions options) {
      super(vertx, resolver, redirectHandler, httpMetrics, poolOptions, defaultProxyOptions, nonProxyHosts, loadBalancer, followAlternativeServices, resolverIdeTimeout, verifyHost, defaultSsl, defaultHost, defaultPort, maxRedirects, versions, sslOptions, connectHandler, tcpTransport, quicTransport);
      this.config = config;
      this.options = options;
    }
    @Override
    public HttpClientOptions options() {
      return options == null ? new HttpClientOptions() : new HttpClientOptions(options);
    }

    @Override
    public HttpClientConfig config() {
      return new HttpClientConfig(config);
    }
  }

  private static TcpClientConfig netClientConfig(HttpClientConfig httpConfig) {
    TcpClientConfig config = new TcpClientConfig(httpConfig.getTcpConfig());
    config.setProxyOptions(null);
    config.setSsl(false);
    ObservabilityConfig observabilityConfig = httpConfig.getObservabilityConfig();
    if (observabilityConfig != null) {
      config.setMetricsName(observabilityConfig.getMetricsName());
    }
    return config;
  }

  private Handler<HttpConnection> connectionHandler(HttpClientConfig config) {
    int windowSize;
    if (config.getHttp2Config() != null) {
      windowSize = config.getHttp2Config().getConnectionWindowSize();
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
  public HttpClientInternal build() {

    HttpClientConfig co = clientConfig;
    if (co == null) {
      // We assume default client configuration
      co = new HttpClientConfig(new HttpClientOptions());
    }

    ClientSSLOptions ssl;
    if (sslOptions != null) {
      ssl = sslOptions.copy();
    } else {
      ssl = null;
    }

    if (co.getVersions().isEmpty()) {
      throw new IllegalStateException("HTTP client must be configured for at least one HTTP version");
    }

    HttpClientMetrics<?, ?> httpMetrics;
    if (vertx.metrics() != null) {
      httpMetrics = vertx.metrics() != null ? vertx.metrics().createHttpClientMetrics(co) : null;
    } else {
      httpMetrics = null;
    }

    HttpClientTransport quicTransport;
    if (co.getVersions().contains(HttpVersion.HTTP_3)) {
      quicTransport = new QuicHttpClientTransport(vertx, co);
    } else {
      quicTransport = null;
    }

    HttpClientTransport transport;
    String shared;
    EndpointResolver resolver;
    List<HttpVersion> supportedVersions = co.getVersions();
    if (supportedVersions.contains(HttpVersion.HTTP_1_0) || supportedVersions.contains(HttpVersion.HTTP_1_1) || supportedVersions.contains(HttpVersion.HTTP_2)) {
      resolver = endpointResolver(co);
      shared = co.isShared() ? co.getName() : null;
      TcpClientConfig clientConfig = netClientConfig(co)
        .setProxyOptions(null);
      NetClientInternal tcpClient = new NetClientBuilder(vertx, clientConfig)
        .protocol("http")
        .sslOptions(sslOptions)
        .sslEngineOptions(sslEngineOptions)
        .build();
      LogConfig logConfig = co.getTcpConfig().getLogConfig();
      ObservabilityConfig observabilityConfig = co.getObservabilityConfig();
      transport = new TcpHttpClientTransport(
        tcpClient,
        observabilityConfig != null ? observabilityConfig.getTracingPolicy() : null,
        co.isDecompressionEnabled(),
        logConfig != null && logConfig.isEnabled(),
        logConfig != null ? logConfig.getDataFormat() : null,
        forceSNI,
        supportedVersions.contains(HttpVersion.HTTP_1_1) || supportedVersions.contains(HttpVersion.HTTP_1_0) ? (co.getHttp1Config() != null ? co.getHttp1Config() : new Http1ClientConfig()) : null,
        supportedVersions.contains(HttpVersion.HTTP_2) ? (co.getHttp2Config() != null ? co.getHttp2Config() : new Http2ClientConfig()) : null,
        co.getTcpConfig().getIdleTimeout(),
        co.getTcpConfig().getReadIdleTimeout(),
        co.getTcpConfig().getWriteIdleTimeout(),
        httpMetrics
      );
    } else {
      resolver = null;
      transport = null;
      shared = null;
    }


    HttpClientConfig co2 = co;
    CloseFuture cf = resolveCloseFuture();
    HttpClientInternal client;
    Closeable closeable;
    if (shared != null) {
      CloseFuture closeFuture = new CloseFuture();
      client = vertx.createSharedResource("__vertx.shared.httpClients", co.getName(), closeFuture, cf_ -> {
        HttpClientImpl impl = createHttpClientImpl(co2, ssl, httpMetrics, resolver, redirectHandler, transport, quicTransport);
        cf_.add(completion -> impl.close().onComplete(completion));
        return impl;
      });
      client = new CleanableHttpClient(client, vertx.cleaner(), timeout -> closeFuture.close());
      closeable = closeFuture;
    } else {
      HttpClientImpl impl = createHttpClientImpl(co2, ssl, httpMetrics, resolver, redirectHandler, transport, quicTransport);
      closeable = impl;
      client = new CleanableHttpClient(impl, vertx.cleaner(), impl::shutdown);
    }
    cf.add(closeable);
    return client;
  }
}
