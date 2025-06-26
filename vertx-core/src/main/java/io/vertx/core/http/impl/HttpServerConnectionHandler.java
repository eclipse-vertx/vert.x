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
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.ServerWebSocketHandshake;
import io.vertx.core.http.impl.http2.Http2ServerConnection;
import io.vertx.core.http.impl.http2.Http2ServerRequest;
import io.vertx.core.internal.ContextInternal;

import java.util.ArrayList;

/**
 * HTTP server connection handler.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpServerConnectionHandler implements Handler<HttpServerConnection> {

  final HttpServerImpl server;
  final String serverOrigin;
  final Handler<HttpServerRequest> requestHandler;
  final Handler<HttpServerRequest> invalidRequestHandler;
  final Handler<ServerWebSocket> webSocketHandler;
  final Handler<ServerWebSocketHandshake> webSocketHandshakeHandler;
  final Handler<HttpConnection> connectionHandler;
  final Handler<Throwable> exceptionHandler;
  final int connectionWindowSize;

  HttpServerConnectionHandler(
    HttpServerImpl server,
    String serverOrigin,
    Handler<HttpServerRequest> requestHandler,
    Handler<HttpServerRequest> invalidRequestHandler,
    Handler<ServerWebSocket> webSocketHandler,
    Handler<ServerWebSocketHandshake> webSocketHandshakeHandler,
    Handler<HttpConnection> connectionHandler,
    Handler<Throwable> exceptionHandler,
    int connectionWindowSize) {
    this.server = server;
    this.serverOrigin = serverOrigin;
    this.requestHandler = requestHandler;
    this.invalidRequestHandler = invalidRequestHandler == null ? HttpServerRequest.DEFAULT_INVALID_REQUEST_HANDLER : invalidRequestHandler;
    this.webSocketHandler = webSocketHandler;
    this.webSocketHandshakeHandler = webSocketHandshakeHandler;
    this.connectionHandler = connectionHandler;
    this.exceptionHandler = exceptionHandler;
    this.connectionWindowSize = connectionWindowSize;
  }

  // TOdo : improve this
  private Handler<HttpServerRequest> requestHandler(HttpServerConnection conn) {
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
    return requestHandler;
  }

  @Override
  public void handle(HttpServerConnection conn) {
    final Handler<HttpServerRequest> requestHandler = requestHandler(conn);

    if (connectionWindowSize > 0) {
      conn.setWindowSize(connectionWindowSize);
    }
    conn.exceptionHandler(exceptionHandler);

    if (conn instanceof Http1xServerConnection) {
      Http1xServerConnection http1Conn = (Http1xServerConnection) conn;
      http1Conn.handler(requestHandler);
      http1Conn.invalidRequestHandler(invalidRequestHandler);
    } else {
      Http2ServerConnection http2Conn = (Http2ServerConnection) conn;
      http2Conn.streamHandler(stream -> {
        HttpServerOptions options = server.options;
        Http2ServerRequest request = new Http2ServerRequest(stream, stream.context(), options.isHandle100ContinueAutomatically(),
          options.getMaxFormAttributeSize(), options.getMaxFormFields(), options.getMaxFormBufferedBytes(), serverOrigin);
        request.handler = requestHandler;
        stream.handler(request);
      });
    }

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
