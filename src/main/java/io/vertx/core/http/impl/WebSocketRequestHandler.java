/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * An {@code Handler<HttpServerRequest>} decorator that handles {@code ServerWebSocket} dispatch to a WebSocket handler
 * when necessary.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class WebSocketRequestHandler implements Handler<HttpServerRequest> {

  private final HttpServerMetrics metrics;
  private final HttpHandlers handlers;

  WebSocketRequestHandler(HttpServerMetrics metrics, HttpHandlers handlers) {
    this.metrics = metrics;
    this.handlers = handlers;
  }

  @Override
  public void handle(HttpServerRequest req) {
    if (req.headers().contains(io.vertx.core.http.HttpHeaders.UPGRADE, io.vertx.core.http.HttpHeaders.WEBSOCKET, true)) {
      ServerWebSocketImpl ws = ((Http1xServerConnection)req.connection()).createWebSocket((HttpServerRequestImpl) req);
      if (METRICS_ENABLED && metrics != null) {
        ws.setMetric(metrics.connected(((Http1xServerConnection)req.connection()).metric(), ws));
      }
      if (handlers.wsHandler != null) {
        handlers.wsHandler.handle(ws);
        if (!ws.isRejected()) {
          ws.connectNow();
        } else {
          req.response().setStatusCode(ws.getRejectedStatus().code()).end();
        }
      } else {
        handlers.requestHandler.handle(req);
      }
    } else {
      handlers.requestHandler.handle(req);
    }
  }
}
