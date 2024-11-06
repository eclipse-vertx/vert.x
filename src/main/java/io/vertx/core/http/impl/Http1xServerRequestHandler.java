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

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.ServerWebSocketHandshake;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.util.List;

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
    Handler<ServerWebSocket> wsHandler = handlers.wsHandler;
    Handler<HttpServerRequest> reqHandler = handlers.requestHandler;
    Handler<ServerWebSocketHandshake> wsHandshakeHandler = handlers.wsHandshakeHandler;
    if (wsHandler != null) {
      if (req.headers().contains(UPGRADE, WEBSOCKET, true) && handlers.server.wsAccept()) {
        // Missing upgrade header + null request handler will be handled when creating the handshake by sending a 400 error
        // handle((Http1xServerRequest) req, wsHandler);
        Future<ServerWebSocket> fut = ((Http1xServerRequest) req).webSocket();
        if (wsHandshakeHandler != null) {
          fut.onComplete(ar -> {
            if (ar.succeeded()) {
              ServerWebSocket ws = ar.result();
              Promise<Integer> promise = Promise.promise();
              ws.setHandshake(promise.future());
              wsHandshakeHandler.handle(new ServerWebSocketHandshake() {
                @Override
                public MultiMap headers() {
                  return ws.headers();
                }
                @Override
                public @Nullable String scheme() {
                  return ws.scheme();
                }
                @Override
                public @Nullable HostAndPort authority() {
                  return ws.authority();
                }
                @Override
                public String uri() {
                  return ws.uri();
                }
                @Override
                public String path() {
                  return ws.path();
                }
                @Override
                public @Nullable String query() {
                  return ws.query();
                }
                @Override
                public Future<ServerWebSocket> accept() {
                  promise.complete(101);
                  wsHandler.handle(ws);
                  return Future.succeededFuture(ws);
                }
                @Override
                public Future<Void> reject(int status) {
                  promise.complete(status);
                  return Future.succeededFuture();
                }
                @Override
                public SocketAddress remoteAddress() {
                  return ws.remoteAddress();
                }
                @Override
                public SocketAddress localAddress() {
                  return ws.localAddress();
                }
                @Override
                public boolean isSsl() {
                  return ws.isSsl();
                }
                @Override
                public SSLSession sslSession() {
                  return ws.sslSession();
                }
                @Override
                public List<Certificate> peerCertificates() throws SSLPeerUnverifiedException {
                  return ws.peerCertificates();
                }
              });
            } else {

            }
          });
        } else {
          fut.onComplete(ar -> {
            if (ar.succeeded()) {
              ServerWebSocketImpl ws = (ServerWebSocketImpl) ar.result();
              wsHandler.handle(ws);
              ws.tryHandshake(101);
            } else {
              // ????
            }
          });
        }
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
      req.response().close();
    } else {
      reqHandler.handle(req);
    }
  }
}
