/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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
import io.vertx.core.http.ClientWebSocket;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.SSLOptions;

import java.util.List;

public class SharedWebSocketClient implements WebSocketClient {

  public static final String SHARED_MAP_NAME = "__vertx.shared.webSocketClients";

  private final VertxInternal vertx;
  private final CloseFuture closeFuture;
  private final WebSocketClient delegate;

  public SharedWebSocketClient(VertxInternal vertx, CloseFuture closeFuture, WebSocketClient delegate) {
    this.vertx = vertx;
    this.closeFuture = closeFuture;
    this.delegate = delegate;
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
  public ClientWebSocket webSocket() {
    return delegate.webSocket();
  }

  @Override
  public void connect(WebSocketConnectOptions options, Handler<AsyncResult<WebSocket>> handler) {
    delegate.connect(options, handler);
  }

  @Override
  public Future<WebSocket> connect(WebSocketConnectOptions options) {
    return delegate.connect(options);
  }

  @Override
  public Future<Void> updateSSLOptions(SSLOptions options) {
    return delegate.updateSSLOptions(options);
  }

  @Override
  public void updateSSLOptions(SSLOptions options, Handler<AsyncResult<Void>> handler) {
    delegate.updateSSLOptions(options, handler);
  }

  @Override
  public boolean isMetricsEnabled() {
    return delegate.isMetricsEnabled();
  }
}
