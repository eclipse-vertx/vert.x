package io.vertx.core.http.impl;


import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;

import java.util.function.Function;

public class HttpClientBuilderImpl implements HttpClientBuilder {

  private final VertxInternal vertx;
  private HttpClientOptions clientOptions;
  private PoolOptions poolOptions;
  private Handler<HttpConnection> connectHandler;
  private Function<HttpClientResponse, Future<RequestOptions>> redirectHandler;

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

  private CloseFuture resolveCloseFuture() {
    ContextInternal context = vertx.getContext();
    return context != null ? context.closeFuture() : vertx.closeFuture();
  }

  @Override
  public HttpClient build() {
    HttpClientOptions co = clientOptions != null ? clientOptions : new HttpClientOptions();
    PoolOptions po = poolOptions != null ? poolOptions : new PoolOptions();
    HttpClient client;
    CloseFuture closeFuture = new CloseFuture();
    if (co.isShared()) {
      client = vertx.createSharedClient(SharedHttpClient.SHARED_MAP_NAME, co.getName(), closeFuture, cf -> vertx.createHttpPoolClient(co, po, cf));
      client = new SharedHttpClient(vertx, closeFuture, client);
    } else {
      client = vertx.createHttpPoolClient(co, po, closeFuture);
    }
    Handler<HttpConnection> connectHandler = this.connectHandler;
    if (connectHandler != null) {
      client.connectionHandler(connectHandler);
    }
    Function<HttpClientResponse, Future<RequestOptions>> redirectHandler = this.redirectHandler;
    if (redirectHandler != null) {
      client.redirectHandler(redirectHandler);
    }
    resolveCloseFuture().add(closeFuture);
    return client;
  }
}
