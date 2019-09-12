/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.net.impl.ConnectionBase;

import java.util.ArrayList;
import java.util.Objects;

/**
 * HTTP server handlers.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpHandlers implements Handler<HttpServerConnection> {

  final HttpServerImpl server;
  final Handler<HttpServerRequest> requestHandler;
  final Handler<ServerWebSocket> wsHandler;
  final Handler<HttpConnection> connectionHandler;
  final Handler<Throwable> exceptionHandler;

  public HttpHandlers(
    HttpServerImpl server,
    Handler<HttpServerRequest> requestHandler,
    Handler<ServerWebSocket> wsHandler,
    Handler<HttpConnection> connectionHandler,
    Handler<Throwable> exceptionHandler) {
    this.server = server;
    this.requestHandler = requestHandler;
    this.wsHandler = wsHandler;
    this.connectionHandler = connectionHandler;
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public void handle(HttpServerConnection conn) {
    server.connectionMap.put(conn.channel(), (ConnectionBase) conn);
    conn.channel().closeFuture().addListener(fut -> {
      server.connectionMap.remove(conn.channel());
    });
    Handler<HttpServerRequest> requestHandler = this.requestHandler;
    if (HttpServerImpl.DISABLE_WEBSOCKETS) {
      // As a performance optimisation you can set a system property to disable websockets altogether which avoids
      // some casting and a header check
    } else {
      if (conn instanceof Http1xServerConnection) {
        requestHandler =  new WebSocketRequestHandler(server.metrics, this);
        Http1xServerConnection c = (Http1xServerConnection) conn;
        initializeWebsocketExtensions(c.channelHandlerContext().pipeline());
      }
    }
    conn.exceptionHandler(exceptionHandler);
    conn.handler(requestHandler);
    if (connectionHandler != null) {
      connectionHandler.handle(conn);
    }
  }

  private void initializeWebsocketExtensions(ChannelPipeline pipeline) {
    ArrayList<WebSocketServerExtensionHandshaker> extensionHandshakers = new ArrayList<>();
    if (server.options.getPerFrameWebsocketCompressionSupported()) {
      extensionHandshakers.add(new DeflateFrameServerExtensionHandshaker(server.options.getWebsocketCompressionLevel()));
    }
    if (server.options.getPerMessageWebsocketCompressionSupported()) {
      extensionHandshakers.add(new PerMessageDeflateServerExtensionHandshaker(server.options.getWebsocketCompressionLevel(),
        ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE,
        server.options.getWebsocketAllowServerNoContext(), server.options.getWebsocketPreferredClientNoContext()));
    }
    if (!extensionHandshakers.isEmpty()) {
      WebSocketServerExtensionHandler extensionHandler = new WebSocketServerExtensionHandler(
        extensionHandshakers.toArray(new WebSocketServerExtensionHandshaker[extensionHandshakers.size()]));
      pipeline.addLast("websocketExtensionHandler", extensionHandler);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HttpHandlers that = (HttpHandlers) o;

    if (!Objects.equals(requestHandler, that.requestHandler)) return false;
    if (!Objects.equals(wsHandler, that.wsHandler)) return false;
    if (!Objects.equals(connectionHandler, that.connectionHandler)) return false;
    if (!Objects.equals(exceptionHandler, that.exceptionHandler)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = 0;
    if (requestHandler != null) {
      result = 31 * result + requestHandler.hashCode();
    }
    if (wsHandler != null) {
      result = 31 * result + wsHandler.hashCode();
    }
    if (connectionHandler != null) {
      result = 31 * result + connectionHandler.hashCode();
    }
    if (exceptionHandler != null) {
      result = 31 * result + exceptionHandler.hashCode();
    }
    return result;
  }
}
