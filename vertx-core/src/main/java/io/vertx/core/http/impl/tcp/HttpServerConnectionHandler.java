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

package io.vertx.core.http.impl.tcp;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpServerRequestImpl;
import io.vertx.core.http.impl.http1.Http1ServerConnection;
import io.vertx.core.http.impl.http1.Http1ServerRequestHandler;
import io.vertx.core.http.impl.http2.Http2ServerConnection;
import io.vertx.core.internal.ContextInternal;

import java.util.ArrayList;

/**
 * HTTP server connection handler.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpServerConnectionHandler implements Handler<HttpServerConnection> {

  public final TcpHttpServer server;
  public final String serverOrigin;
  public final Handler<HttpServerRequest> requestHandler;
  public final Handler<HttpServerRequest> invalidRequestHandler;
  public final Handler<ServerWebSocket> webSocketHandler;
  public final Handler<ServerWebSocketHandshake> webSocketHandshakeHandler;
  public final Handler<HttpConnection> connectionHandler;
  public final Handler<Throwable> exceptionHandler;
  public final int connectionWindowSize;

  public HttpServerConnectionHandler(
    TcpHttpServer server,
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
    if (TcpHttpServer.DISABLE_WEBSOCKETS) {
      // As a performance optimisation you can set a system property to disable WebSockets altogether which avoids
      // some casting and a header check
    } else {
      if (conn instanceof Http1ServerConnection) {
        requestHandler =  new Http1ServerRequestHandler(this);
        Http1ServerConnection c = (Http1ServerConnection) conn;
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

    if (conn instanceof Http1ServerConnection) {
      Http1ServerConnection http1Conn = (Http1ServerConnection) conn;
      http1Conn.handler(requestHandler);
      http1Conn.invalidRequestHandler(invalidRequestHandler);
    } else {
      Http2ServerConnection http2Conn = (Http2ServerConnection) conn;
      http2Conn.streamHandler(stream -> {
        HttpServerConfig config = server.config;
        HttpServerRequestImpl request = new HttpServerRequestImpl(requestHandler, stream, stream.context(),
          config.isHandle100ContinueAutomatically(), config.getMaxFormAttributeSize(), config.getMaxFormFields(),
          config.getMaxFormBufferedBytes(), serverOrigin);
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
    if (server.config.getWebSocketConfig().getUsePerFrameCompression()) {
      extensionHandshakers.add(new DeflateFrameServerExtensionHandshaker(server.config.getWebSocketConfig().getCompressionLevel()));
    }
    if (server.config.getWebSocketConfig().getUsePerMessageCompression()) {
      extensionHandshakers.add(new PerMessageDeflateServerExtensionHandshaker(server.config.getWebSocketConfig().getCompressionLevel(),
        ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE,
        server.config.getWebSocketConfig().getUseServerNoContext(), server.config.getWebSocketConfig().getUseClientNoContext()));
    }
    if (!extensionHandshakers.isEmpty()) {
      WebSocketServerExtensionHandler extensionHandler = new WebSocketServerExtensionHandler(
        extensionHandshakers.toArray(new WebSocketServerExtensionHandshaker[0]));
      pipeline.addBefore("handler", "webSocketExtensionHandler", extensionHandler);
    }
  }
}
