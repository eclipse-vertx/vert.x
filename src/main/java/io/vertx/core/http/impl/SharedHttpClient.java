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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.*;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;

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
  public void close(Handler<AsyncResult<Void>> handler) {
    ContextInternal closingCtx = vertx.getOrCreateContext();
    closeFuture.close(handler != null ? closingCtx.promise(handler) : null);
  }

  @Override
  public Future<Void> close() {
    ContextInternal closingCtx = vertx.getOrCreateContext();
    PromiseInternal<Void> promise = closingCtx.promise();
    closeFuture.close(promise);
    return promise.future();
  }

  @Override
  public void request(RequestOptions options, Handler<AsyncResult<HttpClientRequest>> handler) {
    delegate.request(options, handler);
  }

  @Override
  public Future<HttpClientRequest> request(RequestOptions options) {
    return delegate.request(options);
  }

  @Override
  public void request(HttpMethod method, int port, String host, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler) {
    delegate.request(method, port, host, requestURI, handler);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, int port, String host, String requestURI) {
    return delegate.request(method, port, host, requestURI);
  }

  @Override
  public void request(HttpMethod method, String host, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler) {
    delegate.request(method, host, requestURI, handler);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String host, String requestURI) {
    return delegate.request(method, host, requestURI);
  }

  @Override
  public void request(HttpMethod method, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler) {
    delegate.request(method, requestURI, handler);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String requestURI) {
    return delegate.request(method, requestURI);
  }

  @Override
  public void webSocket(int port, String host, String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    delegate.webSocket(port, host, requestURI, handler);
  }

  @Override
  public Future<WebSocket> webSocket(int port, String host, String requestURI) {
    return delegate.webSocket(port, host, requestURI);
  }

  @Override
  public void webSocket(String host, String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    delegate.webSocket(host, requestURI, handler);
  }

  @Override
  public Future<WebSocket> webSocket(String host, String requestURI) {
    return delegate.webSocket(host, requestURI);
  }

  @Override
  public void webSocket(String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    delegate.webSocket(requestURI, handler);
  }

  @Override
  public Future<WebSocket> webSocket(String requestURI) {
    return delegate.webSocket(requestURI);
  }

  @Override
  public void webSocket(WebSocketConnectOptions options, Handler<AsyncResult<WebSocket>> handler) {
    delegate.webSocket(options, handler);
  }

  @Override
  public Future<WebSocket> webSocket(WebSocketConnectOptions options) {
    return delegate.webSocket(options);
  }

  @Override
  public void webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols, Handler<AsyncResult<WebSocket>> handler) {
    delegate.webSocketAbs(url, headers, version, subProtocols, handler);
  }

  @Override
  public Future<WebSocket> webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols) {
    return delegate.webSocketAbs(url, headers, version, subProtocols);
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
