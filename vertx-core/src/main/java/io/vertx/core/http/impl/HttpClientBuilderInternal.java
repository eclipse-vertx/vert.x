package io.vertx.core.http.impl;


import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.core.net.endpoint.EndpointResolver;
import io.vertx.core.net.impl.tcp.NetClientBuilder;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.List;
import java.util.function.Function;

public final class HttpClientBuilderInternal implements HttpClientBuilder {

  private final VertxInternal vertx;
  private HttpClientOptions clientOptions;
  private Http3ClientOptions http3ClientOptions;
  private PoolOptions poolOptions;
  private Handler<HttpConnection> connectHandler;
  private Function<HttpClientResponse, Future<RequestOptions>> redirectHandler;
  private AddressResolver<?> addressResolver;
  private LoadBalancer loadBalancer;

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

  private HttpClientImpl createHttpClientImpl(HttpClientMetrics<?, ?, ?> metrics,
                                              EndpointResolver resolver,
                                              Function<HttpClientResponse, Future<RequestOptions>> redirectHandler,
                                              HttpClientImpl.Transport config, HttpClientImpl.Transport quicTransport) {
    ProxyOptions proxyOptions;
    List<String> nonProxyHosts;
    if (clientOptions != null) {
      proxyOptions = clientOptions.getProxyOptions();
      nonProxyHosts = clientOptions.getNonProxyHosts();
    } else {
      proxyOptions = null;
      nonProxyHosts = null;
    }
    PoolOptions po;
    po = poolOptions != null ? poolOptions : new PoolOptions();
    return new HttpClientImpl(vertx, resolver, redirectHandler, metrics, po, proxyOptions, nonProxyHosts, config, quicTransport);
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

    HttpClientOptions co = clientOptions;

    // Copy options here ????
    HttpClientImpl.Transport quicTransport;
    HttpClientMetrics<?, ?, ?> metrics;
    if (http3ClientOptions != null) {
      Http3ClientOptions co3 = new Http3ClientOptions(http3ClientOptions);
      metrics = vertx.metrics() != null ? vertx.metrics().createHttpClientMetrics(co == null ? new HttpClientOptions() : co) : null;
      quicTransport = new HttpClientImpl.Transport(
        connectHandler,
        new Http3ChannelConnector(vertx, metrics, co3),
        co3.isVerifyHost(),
        true,
        co3.getDefaultHost(),
        co3.getDefaultPort(),
        co3.getMaxRedirects(),
        HttpVersion.HTTP_3,
        co3.getSslOptions()
      );
    } else {
      quicTransport = null;
      metrics = null;
      if (co == null) {
        co = new HttpClientOptions();
      }
    }

    HttpClientImpl.Transport transport;
    String shared;
    EndpointResolver resolver;
    if (co != null) {
      resolver = endpointResolver(co);
      shared = co.isShared() ? co.getName() : null;
      if (metrics == null && vertx.metrics() != null) {
        metrics = vertx.metrics().createHttpClientMetrics(co);
      }
      NetClientInternal tcpClient = new NetClientBuilder(vertx, new NetClientOptions(co).setProxyOptions(null)).metrics(metrics).build();
      Handler<HttpConnection> connectHandler = connectionHandler(co);
      transport = new HttpClientImpl.Transport(
        connectHandler,
        new Http1xOrH2ChannelConnector(tcpClient, co, metrics),
        co.isVerifyHost(),
        co.isSsl(),
        co.getDefaultHost(),
        co.getDefaultPort(),
        co.getMaxRedirects(),
        co.getProtocolVersion(),
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
      client = vertx.createSharedResource("__vertx.shared.httpClients", shared, closeFuture, cf_ -> {
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
