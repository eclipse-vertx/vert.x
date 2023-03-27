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

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.UpgradeRejectedException;
import io.vertx.core.http.impl.headers.HeadersAdaptor;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WebSocketHandshakeInboundHandler extends ChannelInboundHandlerAdapter {

  private final Handler<AsyncResult<HeadersAdaptor>> wsHandler;
  private final WebSocketClientHandshaker handshaker;
  private ChannelHandlerContext chctx;
  private FullHttpResponse response;
  private ChannelFuture fut;

  WebSocketHandshakeInboundHandler(WebSocketClientHandshaker handshaker, Handler<AsyncResult<HeadersAdaptor>> wsHandler) {
    this.handshaker = handshaker;
    this.wsHandler = wsHandler;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    super.handlerAdded(ctx);
    chctx = ctx;
    fut = handshaker.handshake(ctx.channel());
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    super.channelInactive(ctx);
    // if still handshaking this means we not got any response back from the server and so need to notify the client
    // about it as otherwise the client would never been notified.
    wsHandler.handle(Future.failedFuture(new WebSocketHandshakeException("Connection closed while handshake in process")));
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof HttpResponse) {
      HttpResponse resp = (HttpResponse) msg;
      response = new DefaultFullHttpResponse(resp.protocolVersion(), resp.status());
      response.headers().add(resp.headers());
    }
    if (msg instanceof HttpContent) {
      HttpContent content = (HttpContent) msg;
      try {
        if (response != null) {
          response.content().writeBytes(content.content());
          if (msg instanceof LastHttpContent) {
            response.trailingHeaders().add(((LastHttpContent) msg).trailingHeaders());
            ChannelPipeline pipeline = chctx.pipeline();
            pipeline.remove(WebSocketHandshakeInboundHandler.this);
            ChannelHandler handler = pipeline.get(HttpContentDecompressor.class);
            if (handler != null) {
              // remove decompressor as its not needed anymore once connection was upgraded to WebSocket
              ctx.pipeline().remove(handler);
            }
            handshakeComplete(response);
          }
        }
      } finally {
        content.release();
      }
    }
  }

  private void handshakeComplete(FullHttpResponse response) {
    int sc = response.status().code();
    if (sc != 101) {
      String msg = "WebSocket upgrade failure: " + sc;
      ByteBuf content = response.content();
      UpgradeRejectedException failure = new UpgradeRejectedException(
        msg,
        sc,
        new HeadersAdaptor(response.headers()),
        content != null ? Buffer.buffer(content) : null);
      wsHandler.handle(Future.failedFuture(failure));
    } else {
      fut.addListener((GenericFutureListener<io.netty.util.concurrent.Future<Void>>) future -> {
        Future<HeadersAdaptor> res;
        if (future.isSuccess()) {
          try {
            handshaker.finishHandshake(chctx.channel(), response);
            res = Future.succeededFuture(new HeadersAdaptor(response.headers()));
          } catch (WebSocketHandshakeException e) {
            res = Future.failedFuture(e);
          }
        } else {
          res = Future.failedFuture(future.cause());
        }
        wsHandler.handle(res);
      });
    }
  }
}
