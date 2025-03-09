package io.vertx.core.http.impl;


import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;
import io.vertx.core.net.endpoint.EndpointResolver;

import java.util.function.Function;

public final class HttpClientBuilderInternal implements HttpClientBuilder {

  private final VertxInternal vertx;
  private HttpClientOptions clientOptions;
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

  @Override
  public HttpClientAgent build() {
    HttpClientOptions co = clientOptions != null ? clientOptions : new HttpClientOptions();
    PoolOptions po = poolOptions != null ? poolOptions : new PoolOptions();
    CloseFuture cf = resolveCloseFuture();
    HttpClientAgent client;
    Closeable closeable;
    EndpointResolver resolver = endpointResolver(co);
    if (co.isShared()) {
      CloseFuture closeFuture = new CloseFuture();
      client = vertx.createSharedResource("__vertx.shared.httpClients", co.getName(), closeFuture, cf_ -> {

        HttpClientImpl impl = new HttpClientImpl(vertx, resolver, co, po);
        cf_.add(completion -> impl.close().onComplete(completion));
        return impl;
      });
      client = new CleanableHttpClient((HttpClientInternal) client, vertx.cleaner(), (timeout, timeunit) -> closeFuture.close());
      closeable = closeFuture;
    } else {
      HttpClientImpl impl = new HttpClientImpl(vertx, resolver, co, po);
      closeable = impl;
      client = new CleanableHttpClient(impl, vertx.cleaner(), impl::shutdown);
    }
    cf.add(closeable);
    if (redirectHandler != null) {
      ((HttpClientImpl)((CleanableHttpClient)client).delegate).redirectHandler(redirectHandler);
    }
    if (connectHandler != null) {
      ((HttpClientImpl)((CleanableHttpClient)client).delegate).connectionHandler(connectHandler);
    }
    return client;
  }
}
