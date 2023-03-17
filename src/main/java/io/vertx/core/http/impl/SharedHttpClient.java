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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.*;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.SSLOptions;

import java.util.List;
import java.util.function.Function;

public class SharedHttpClient implements HttpClientInternal {

  public static final String SHARED_MAP_NAME = "__vertx.shared.httpClients";

  private final VertxInternal vertx;
  private final CloseFuture closeFuture;
  private final HttpClientInternal delegate;

  public SharedHttpClient(VertxInternal vertx, CloseFuture closeFuture, HttpClient delegate) {
    this.vertx = vertx;
    this.closeFuture = closeFuture;
    this.delegate = (HttpClientInternal) delegate;
  }

  @Override
  public Future<Void> close() {
    ContextInternal closingCtx = vertx.getOrCreateContext();
    PromiseInternal<Void> promise = closingCtx.promise();
    closeFuture.close(promise);
    return promise.future();
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
  public Future<WebSocket> webSocket(int port, String host, String requestURI) {
    return delegate.webSocket(port, host, requestURI);
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
  public Future<Void> updateSSLOptions(SSLOptions options) {
    return delegate.updateSSLOptions(options);
  }

  @Override
  public HttpClient connectionHandler(Handler<HttpConnection> handler) {
    return delegate.connectionHandler(handler);
  }

  @Override
  public HttpClient redirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler) {
    return delegate.redirectHandler(handler);
  }

  @Override
  public Function<HttpClientResponse, Future<RequestOptions>> redirectHandler() {
    return delegate.redirectHandler();
  }

  @Override
  public VertxInternal vertx() {
    return delegate.vertx();
  }

  @Override
  public HttpClientOptions options() {
    return delegate.options();
  }
}
