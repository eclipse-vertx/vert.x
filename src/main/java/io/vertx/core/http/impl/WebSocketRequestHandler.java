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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
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
      handle((HttpServerRequestImpl) req);
    } else {
      handlers.requestHandler.handle(req);
    }
  }

  /**
   * Handle the request when a websocket upgrade header is present.
   */
  private void handle(HttpServerRequestImpl req) {
    Buffer body = Buffer.buffer();
    boolean[] failed = new boolean[1];
    req.handler(buff -> {
      if (!failed[0]) {
        body.appendBuffer(buff);
        if (body.length() > 8192) {
          failed[0] = true;
          // Request Entity Too Large
          HttpServerResponseImpl resp = req.response();
          resp.setStatusCode(413).end();
          resp.close();
        }
      }
    });
    req.endHandler(v -> {
      if (!failed[0]) {
        handle(req, body);
      }
    });
  }

  /**
   * Handle the request once we have the full body.
   */
  private void handle(HttpServerRequestImpl req, Buffer body) {
    DefaultHttpRequest nettyReq = req.getRequest();
    nettyReq = new DefaultFullHttpRequest(
      nettyReq.protocolVersion(),
      nettyReq.method(),
      nettyReq.uri(),
      body.getByteBuf(),
      nettyReq.headers(),
      EmptyHttpHeaders.INSTANCE
    );
    req.setRequest(nettyReq);
    if (handlers.wsHandler != null) {
      ServerWebSocketImpl ws = ((Http1xServerConnection)req.connection()).createWebSocket(req);
      handlers.wsHandler.handle(ws);
      if (!ws.isRejected()) {
        ws.connectNow();
      } else {
        req.response().setStatusCode(ws.getRejectedStatus().code()).end();
      }
    } else {
      handlers.requestHandler.handle(req);
    }
  }
}
