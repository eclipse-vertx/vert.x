/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.*;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.net.impl.VertxNioSocketChannel;

import java.util.Map;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class VertxHttpHandler<C extends ConnectionBase> extends VertxHandler<C> {

  protected Map<Channel, C> connectionMap;
  protected VertxHttpHandler(VertxInternal vertx, Map<Channel, C> connectionMap) {
    super(vertx);
    this.connectionMap = connectionMap;
  }

  @Override
  protected C getConnection(Channel channel) {
    @SuppressWarnings("unchecked")
    VertxNioSocketChannel<C> vch = (VertxNioSocketChannel<C>)channel;
    // As an optimisation we store the connection on the channel - this prevents a lookup every time
    // an event from Netty arrives
    if (vch.conn != null) {
      return vch.conn;
    } else {
      C conn = connectionMap.get(channel);
      if (conn != null) {
        vch.conn = conn;
      }
      return conn;
    }
  }

  @Override
  protected C removeConnection(Channel channel) {
    @SuppressWarnings("unchecked")
    VertxNioSocketChannel<C> vch = (VertxNioSocketChannel<C>)channel;
    vch.conn = null;
    return connectionMap.remove(channel);
  }

  @Override
  protected void channelRead(final C connection, final ContextImpl context, final ChannelHandlerContext chctx, final Object msg) throws Exception {

    if (connection != null) {
      context.executeFromIO(() -> doMessageReceived(connection, chctx, msg));
    } else {
      // We execute this directly as we don't have a context yet, the context will have to be set manually
      // inside doMessageReceived();
      try {
        doMessageReceived(null, chctx, msg);
      } catch (Throwable t) {
        chctx.pipeline().fireExceptionCaught(t);
      }
    }
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    if (msg instanceof WebSocketFrameInternal) {
      WebSocketFrameInternal frame = (WebSocketFrameInternal) msg;
      ByteBuf buf = frame.getBinaryData();
      switch (frame.type()) {
        case BINARY:
          msg = new BinaryWebSocketFrame(frame.isFinal(), 0, buf);
          break;
        case TEXT:
          msg = new TextWebSocketFrame(frame.isFinal(), 0, buf);
          break;
        case CLOSE:
          msg = new CloseWebSocketFrame(true, 0, buf);
          break;
        case CONTINUATION:
          msg = new ContinuationWebSocketFrame(frame.isFinal(), 0, buf);
          break;
        case PONG:
          msg = new PongWebSocketFrame(buf);
          break;
        case PING:
          msg = new PingWebSocketFrame(buf);
          break;
        default:
          throw new IllegalStateException("Unsupported websocket msg " + msg);
      }
    }
    ctx.write(msg, promise);
  }

  protected abstract void doMessageReceived(C connection, ChannelHandlerContext ctx, Object msg) throws Exception;

}
