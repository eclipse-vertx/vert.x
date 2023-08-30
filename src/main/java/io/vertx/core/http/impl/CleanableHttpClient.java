/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.impl.NetClientInternal;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.resolver.AddressResolver;

import java.lang.ref.Cleaner;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A lightweight proxy of Vert.x {@link HttpClient} that can be collected by the garbage collector and release
 * the resources when it happens with a {@code 30} seconds grace period.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableHttpClient implements HttpClientInternal {

  static class Action implements Runnable {
    private final BiFunction<Long, TimeUnit, Future<Void>> dispose;
    private long timeout = 30L;
    private TimeUnit timeUnit = TimeUnit.SECONDS;
    private Future<Void> closeFuture;
    private Action(BiFunction<Long, TimeUnit, Future<Void>> dispose) {
      this.dispose = dispose;
    }
    @Override
    public void run() {
      closeFuture = dispose.apply(timeout, timeUnit);
    }
  }

  public final HttpClientInternal delegate;
  private final Cleaner.Cleanable cleanable;
  private final Action action;

  public CleanableHttpClient(HttpClientInternal delegate, Cleaner cleaner, BiFunction<Long, TimeUnit, Future<Void>> dispose) {
    this.action = new Action(dispose);
    this.delegate = delegate;
    this.cleanable = cleaner.register(this, action);
  }

  @Override
  public Future<HttpClientRequest> request(RequestOptions options) {
    return delegate.request(options);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, int port, String host, String requestURI) {
    return delegate.request(method, port, host, requestURI);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String host, String requestURI) {
    return delegate.request(method, host, requestURI);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String requestURI) {
    return delegate.request(method, requestURI);
  }

  @Override
  public Future<WebSocket> webSocket(String host, String requestURI) {
    return delegate.webSocket(host, requestURI);
  }

  @Override
  public Future<WebSocket> webSocket(String requestURI) {
    return delegate.webSocket(requestURI);
  }

  @Override
  public Future<WebSocket> webSocket(WebSocketConnectOptions options) {
    return delegate.webSocket(options);
  }

  @Override
  public Future<WebSocket> webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols) {
    return delegate.webSocketAbs(url, headers, version, subProtocols);
  }

  @Override
  public ClientWebSocket webSocket() {
    return delegate.webSocket();
  }

  @Override
  public Future<Void> updateSSLOptions(SSLOptions options) {
    return delegate.updateSSLOptions(options);
  }

  @Override
  @Fluent
  public HttpClient connectionHandler(Handler<HttpConnection> handler) {
    return delegate.connectionHandler(handler);
  }

  @Override
  @Fluent
  public HttpClient redirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler) {
    return delegate.redirectHandler(handler);
  }

  @Override
  @GenIgnore
  public Function<HttpClientResponse, Future<RequestOptions>> redirectHandler() {
    return delegate.redirectHandler();
  }

  @Override
  public Future<Void> close(long timeout, TimeUnit timeUnit) {
    if (timeout < 0L) {
      throw new IllegalArgumentException();
    }
    if (timeUnit == null) {
      throw new IllegalArgumentException();
    }
    action.timeout = timeout;
    action.timeUnit = timeUnit;
    cleanable.clean();
    return action.closeFuture;
  }

  @Override
  public VertxInternal vertx() {
    return delegate.vertx();
  }

  @Override
  public HttpClientOptions options() {
    return delegate.options();
  }

  @Override
  public boolean isMetricsEnabled() {
    return delegate.isMetricsEnabled();
  }

  @Override
  public Metrics getMetrics() {
    return delegate.getMetrics();
  }

  @Override
  public NetClientInternal netClient() {
    return delegate.netClient();
  }

  @Override
  public Future<Void> closeFuture() {
    return delegate.closeFuture();
  }

  @Override
  public void addressResolver(AddressResolver addressResolver) {
    delegate.addressResolver(addressResolver);
  }

  @Override
  public Future<HttpClientRequest> request(Address address, HttpMethod method, int port, String host, String requestURI) {
    return delegate.request(address, method, port, host, requestURI);
  }

  @Override
  public void close(Promise<Void> completion) {
    delegate.close(completion);
  }


}
