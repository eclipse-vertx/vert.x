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
import io.vertx.core.http.*;
import io.vertx.core.http.impl.http1x.Http1xServerConnection;
import io.vertx.core.http.impl.http1x.Http1xServerRequestHandler;
import io.vertx.core.http.impl.http2.Http2ServerConnection;
import io.vertx.core.internal.ContextInternal;

import java.util.ArrayList;

/**
 * HTTP server connection handler.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpServerConnectionHandler implements Handler<HttpServerConnection> {

  public final HttpServerImpl server;
  public final String serverOrigin;
  public final Handler<HttpServerRequest> requestHandler;
  public final Handler<HttpServerRequest> invalidRequestHandler;
  public final Handler<ServerWebSocket> webSocketHandler;
  public final Handler<ServerWebSocketHandshake> webSocketHandshakeHandler;
  public final Handler<HttpConnection> connectionHandler;
  public final Handler<Throwable> exceptionHandler;
  public final int connectionWindowSize;

  public HttpServerConnectionHandler(
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
        HttpOverTcpServerConfig options = server.options;
        HttpServerRequestImpl request = new HttpServerRequestImpl(requestHandler, stream, stream.context(),
          options.isHandle100ContinueAutomatically(), options.getMaxFormAttributeSize(), options.getMaxFormFields(),
          options.getMaxFormBufferedBytes(), serverOrigin);
        request.init();
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
    if (server.options.getWebSocketConfig().getPerFrameCompressionSupported()) {
      extensionHandshakers.add(new DeflateFrameServerExtensionHandshaker(server.options.getWebSocketConfig().getCompressionLevel()));
    }
    if (server.options.getWebSocketConfig().getPerMessageCompressionSupported()) {
      extensionHandshakers.add(new PerMessageDeflateServerExtensionHandshaker(server.options.getWebSocketConfig().getCompressionLevel(),
        ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE,
        server.options.getWebSocketConfig().getAllowServerNoContext(), server.options.getWebSocketConfig().getPreferredClientNoContext()));
    }
    if (!extensionHandshakers.isEmpty()) {
      WebSocketServerExtensionHandler extensionHandler = new WebSocketServerExtensionHandler(
        extensionHandshakers.toArray(new WebSocketServerExtensionHandshaker[0]));
      pipeline.addBefore("handler", "webSocketExtensionHandler", extensionHandler);
    }
  }
}
