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

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.ServerWebSocketHandshake;

import static io.vertx.core.http.HttpHeaders.UPGRADE;
import static io.vertx.core.http.HttpHeaders.WEBSOCKET;

/**
 * An {@code Handler<HttpServerRequest>} decorator that handles
 * <ul>
 *   <li>{@code ServerWebSocket} dispatch to a WebSocket handler when necessary.</li>
 *   <li>invalid HTTP version sent by the client</li>
 * </ul>
 *
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http1xServerRequestHandler implements Handler<HttpServerRequest> {

  private final HttpServerConnectionHandler handlers;

  public Http1xServerRequestHandler(HttpServerConnectionHandler handlers) {
    this.handlers = handlers;
  }

  @Override
  public void handle(HttpServerRequest req) {
    Handler<ServerWebSocket> wsHandler = handlers.webSocketHandler;
    Handler<ServerWebSocketHandshake> wsHandshakeHandler = handlers.webSocketHandshakeHandler;
    Handler<HttpServerRequest> reqHandler = handlers.requestHandler;
    if (wsHandler != null || wsHandshakeHandler != null) {
      if (req.headers().contains(UPGRADE, WEBSOCKET, true) && handlers.server.wsAccept()) {
        // Missing upgrade header + null request handler will be handled when creating the handshake by sending a 400 error
        ((Http1xServerRequest)req).webSocketHandshake().onComplete(ar -> {
          if (ar.succeeded()) {
            ServerWebSocketHandshaker handshake = (ServerWebSocketHandshaker) ar.result();
            if (wsHandshakeHandler == null) {
              handshake.accept();
            } else {
              wsHandshakeHandler.handle(handshake);
            }
            if (wsHandler != null) {
              handshake.onSuccess(wsHandler);
            }
          }
        });
      } else {
        if (reqHandler != null) {
          reqHandler.handle(req);
        } else {
          req.response().setStatusCode(400).end();
        }
      }
    } else if (req.version() == null) {
      // Invalid HTTP version, i.e not HTTP/1.1 or HTTP/1.0
      req.response().setStatusCode(501).end();
      req.connection().close();
    } else {
      reqHandler.handle(req);
    }
  }
}
