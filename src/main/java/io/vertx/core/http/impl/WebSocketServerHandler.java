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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.impl.HandlerHolder;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.spi.metrics.HttpServerMetrics;

public class WebSocketServerHandler extends Http1xServerHandler {

  private FullHttpRequest wsRequest;

  WebSocketServerHandler(SSLHelper sslHelper, HttpServerOptions options, String serverOrigin, HandlerHolder<HttpHandlers> holder, HttpServerMetrics metrics) {
    super(sslHelper, options, serverOrigin, holder, metrics);
  }

  @Override
  public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
    if (msg instanceof HttpRequest) {
      final HttpRequest request = (HttpRequest) msg;
      if (HttpServerImpl.log.isTraceEnabled()) {
        HttpServerImpl.log.trace("Server received request: " + request.uri());
      }
      if (request.headers().contains(io.vertx.core.http.HttpHeaders.UPGRADE, io.vertx.core.http.HttpHeaders.WEBSOCKET, true)) {
        if (wsRequest == null) {
          if (request instanceof FullHttpRequest) {
            super.channelRead(chctx, request);
          } else {
            wsRequest = new DefaultFullHttpRequest(request.protocolVersion(), request.method(), request.uri());
            wsRequest.headers().set(request.headers());
          }
          return;
        }
      }
    } else if (msg instanceof HttpContent) {
      if (wsRequest != null) {
        wsRequest.content().writeBytes(((HttpContent) msg).content());
        if (msg instanceof LastHttpContent) {
          FullHttpRequest req = wsRequest;
          wsRequest = null;
          super.channelRead(chctx, req);
          return;
        }
      }
    }
    super.channelRead(chctx, msg);
  }
}
