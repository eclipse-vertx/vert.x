package io.vertx.core.http.impl;


import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.loadbalancing.LoadBalancer;
import io.vertx.core.spi.resolver.address.AddressResolver;

import java.util.function.Function;

public class HttpClientBuilderImpl implements HttpClientBuilder {

  private final VertxInternal vertx;
  private HttpClientOptions clientOptions;
  private PoolOptions poolOptions;
  private Handler<HttpConnection> connectHandler;
  private Function<HttpClientResponse, Future<RequestOptions>> redirectHandler;
  private io.vertx.core.net.AddressResolver addressResolver;
  private LoadBalancer loadBalancer = LoadBalancer.ROUND_ROBIN;

  public HttpClientBuilderImpl(VertxInternal vertx) {
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
  public HttpClientBuilder withAddressResolver(io.vertx.core.net.AddressResolver addressResolver) {
    this.addressResolver = addressResolver;
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

  @Override
  public HttpClient build() {
    HttpClientOptions co = clientOptions != null ? clientOptions : new HttpClientOptions();
    PoolOptions po = poolOptions != null ? poolOptions : new PoolOptions();
    CloseFuture cf = resolveCloseFuture();
    HttpClient client;
    Closeable closeable;
    if (loadBalancer != null && addressResolver == null) {
      addressResolver = vertx.hostnameResolver();
    }
    if (co.isShared()) {
      CloseFuture closeFuture = new CloseFuture();
      client = vertx.createSharedResource("__vertx.shared.httpClients", co.getName(), closeFuture, cf_ -> {
        AddressResolver<?, ?, ?> resolver = addressResolver != null ? addressResolver.resolver(vertx) : null;
        HttpClientImpl impl = new HttpClientImpl(vertx, resolver, loadBalancer, co, po);
        cf_.add(completion -> impl.close().onComplete(completion));
        return impl;
      });
      client = new CleanableHttpClient((HttpClientInternal) client, vertx.cleaner(), (timeout, timeunit) -> closeFuture.close());
      closeable = closeFuture;
    } else {
      AddressResolver<?, ?, ?> resolver = addressResolver != null ? addressResolver.resolver(vertx) : null;
      HttpClientImpl impl = new HttpClientImpl(vertx, resolver, loadBalancer, co, po);
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
