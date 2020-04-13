/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
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
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import static io.vertx.core.http.HttpHeaders.UPGRADE;
import static io.vertx.core.http.HttpHeaders.WEBSOCKET;
import static io.vertx.core.http.impl.HttpUtils.SC_SWITCHING_PROTOCOLS;

/**
 * An {@code Handler<HttpServerRequest>} decorator that handles {@code ServerWebSocket} dispatch to a WebSocket handler
 * when necessary.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class WebSocketRequestHandler implements Handler<HttpServerRequest> {

  private final HttpServerConnectionHandler handlers;

  public WebSocketRequestHandler(HttpServerConnectionHandler handlers) {
    this.handlers = handlers;
  }

  @Override
  public void handle(HttpServerRequest req) {
    if (req.headers()
      .contains(UPGRADE, WEBSOCKET, true)
      || handlers.requestHandler == null) {
      if (handlers.server.wsAccept()) {
        // Missing upgrade header + null request handler will be handled when creating the handshake by sending a 400 error
        handle((Http1xServerRequest) req);
      } else {
        req.response()
          .setStatusCode(HttpUtils.SC_BAD_GATEWAY)
          .end();
      }
    } else {
      handlers.requestHandler.handle(req);
    }
  }

  /**
   * Handle the request when a WebSocket upgrade header is present.
   */
  private void handle(Http1xServerRequest req) {
    Buffer body = Buffer.buffer();
    boolean[] failed = new boolean[1];
    req.handler(buff -> {
      if (!failed[0]) {
        body.appendBuffer(buff);
        if (body.length() > 8192) {
          failed[0] = true;
          // Request Entity Too Large
          Http1xServerResponse resp = req.response();
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
  private void handle(Http1xServerRequest req, Buffer body) {
    HttpRequest nettyReq = req.nettyRequest();
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
      if (ws == null) {
        // Response is already sent
        return;
      }
      handlers.wsHandler.handle(ws);
      // Attempt to handshake
      ws.tryHandshake(SC_SWITCHING_PROTOCOLS);
    } else {
      handlers.requestHandler.handle(req);
    }
  }
}
