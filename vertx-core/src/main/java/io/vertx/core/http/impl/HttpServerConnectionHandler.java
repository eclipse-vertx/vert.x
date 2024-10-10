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

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.ServerWebSocketHandshake;
import io.vertx.core.internal.ContextInternal;

import java.util.ArrayList;

/**
 * HTTP server connection handler.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpServerConnectionHandler implements Handler<HttpServerConnection> {

  final HttpServerImpl server;
  final Handler<HttpServerRequest> requestHandler;
  final Handler<HttpServerRequest> invalidRequestHandler;
  final Handler<ServerWebSocket> webSocketHandler;
  final Handler<ServerWebSocketHandshake> webSocketHandshakeHandler;
  final Handler<HttpConnection> connectionHandler;
  final Handler<Throwable> exceptionHandler;

  HttpServerConnectionHandler(
    HttpServerImpl server,
    Handler<HttpServerRequest> requestHandler,
    Handler<HttpServerRequest> invalidRequestHandler,
    Handler<ServerWebSocket> webSocketHandler,
    Handler<ServerWebSocketHandshake> webSocketHandshakeHandler,
    Handler<HttpConnection> connectionHandler,
    Handler<Throwable> exceptionHandler) {
    this.server = server;
    this.requestHandler = requestHandler;
    this.invalidRequestHandler = invalidRequestHandler == null ? HttpServerRequest.DEFAULT_INVALID_REQUEST_HANDLER : invalidRequestHandler;
    this.webSocketHandler = webSocketHandler;
    this.webSocketHandshakeHandler = webSocketHandshakeHandler;
    this.connectionHandler = connectionHandler;
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public void handle(HttpServerConnection conn) {
    Handler<HttpServerRequest> requestHandler = this.requestHandler;
    if (HttpServerImpl.DISABLE_WEBSOCKETS) {
      // As a performance optimisation you can set a system property to disable WebSockets altogether which avoids
      // some casting and a header check
    } else {
      if (conn instanceof Http1xServerConnection) {
        requestHandler =  new Http1xServerRequestHandler(this);
        Http1xServerConnection c = (Http1xServerConnection) conn;
        initializeWebSocketExtensions(c.channelHandlerContext().pipeline());
      }
    }
    conn.exceptionHandler(exceptionHandler);
    conn.handler(requestHandler);
    conn.invalidRequestHandler(invalidRequestHandler);
    if (connectionHandler != null) {
      // We hand roll event-loop execution in case of a worker context
      ContextInternal ctx = conn.context();
      ContextInternal prev = ctx.beginDispatch();
      try {
        connectionHandler.handle(conn);
      } catch (Exception e) {
        ctx.reportException(e);
      } finally {
        ctx.endDispatch(prev);
      }
    }
  }

  private void initializeWebSocketExtensions(ChannelPipeline pipeline) {
    ArrayList<WebSocketServerExtensionHandshaker> extensionHandshakers = new ArrayList<>();
    if (server.options.getPerFrameWebSocketCompressionSupported()) {
      extensionHandshakers.add(new DeflateFrameServerExtensionHandshaker(server.options.getWebSocketCompressionLevel()));
    }
    if (server.options.getPerMessageWebSocketCompressionSupported()) {
      extensionHandshakers.add(new PerMessageDeflateServerExtensionHandshaker(server.options.getWebSocketCompressionLevel(),
        ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE,
        server.options.getWebSocketAllowServerNoContext(), server.options.getWebSocketPreferredClientNoContext()));
    }
    if (!extensionHandshakers.isEmpty()) {
      WebSocketServerExtensionHandler extensionHandler = new WebSocketServerExtensionHandler(
        extensionHandshakers.toArray(new WebSocketServerExtensionHandshaker[0]));
      pipeline.addBefore("handler", "webSocketExtensionHandler", extensionHandler);
    }
  }
}
