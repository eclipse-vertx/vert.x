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
import io.vertx.core.http.*;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.VertxInternal;

/**
 *
 */
public class WebSocketClientImpl extends HttpClientBase implements WebSocketClient {

  public WebSocketClientImpl(VertxInternal vertx, HttpClientOptions options, CloseFuture closeFuture) {
    super(vertx, options, closeFuture);
  }

  @Override
  public ClientWebSocket webSocket() {
    return new ClientWebSocketImpl(this);
  }

  @Override
  public void connect(WebSocketConnectOptions options, Handler<AsyncResult<WebSocket>> handler) {
    webSocket(options, handler);
  }

  @Override
  public Future<WebSocket> connect(WebSocketConnectOptions options) {
    return webSocket(options);
  }

}
