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

package io.vertx.core.net.impl;

import io.netty.buffer.*;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.Handler;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;

import java.util.function.Function;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class VertxHandler<C extends VertxConnection> extends ChannelDuplexHandler {

  public static <C extends VertxConnection> VertxHandler<C> create(Function<ChannelHandlerContext, C> connectionFactory) {
    return new VertxHandler<>(connectionFactory);
  }

  private final Function<ChannelHandlerContext, C> connectionFactory;
  private C conn;
  private Handler<C> addHandler;
  private Handler<C> removeHandler;

  private VertxHandler(Function<ChannelHandlerContext, C> connectionFactory) {
    this.connectionFactory = connectionFactory;
  }

  /**
   * Pooled {@code byteBuf} are copied and released, otherwise it is returned as is.
   *
   * @param byteBuf the buffer
   * @return a buffer safe
   */
  public static ByteBuf safeBuffer(ByteBuf byteBuf) {
    Class<?> allocClass;
    if (byteBuf != Unpooled.EMPTY_BUFFER &&
            ((allocClass = byteBuf.alloc().getClass()) == AdaptiveByteBufAllocator.class
          || allocClass == PooledByteBufAllocator.class
          || byteBuf instanceof CompositeByteBuf)) {
      try {
        if (byteBuf.isReadable()) {
          ByteBuf buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(byteBuf.readableBytes());
          buffer.writeBytes(byteBuf, byteBuf.readerIndex(), byteBuf.readableBytes());
          return buffer;
        } else {
          return Unpooled.EMPTY_BUFFER;
        }
      } finally {
        byteBuf.release();
      }
    }
    return byteBuf;
  }

  /**
   * Set the connection, this is called when the channel is added to the pipeline.
   *
   * @param connection the connection
   */
  private void setConnection(C connection) {
    conn = connection;
    if (addHandler != null) {
      addHandler.handle(connection);
    }
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    setConnection(connectionFactory.apply(ctx));
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    if (removeHandler != null) {
      Handler<C> handler = removeHandler;
      removeHandler = null;
      handler.handle(conn);
    }
  }

  /**
   * Set an handler to be called when the connection is set on this handler.
   *
   * @param handler the handler to be notified
   * @return this
   */
  public VertxHandler<C> addHandler(Handler<C> handler) {
    this.addHandler = handler;
    return this;
  }

  /**
   * Set an handler to be called when the connection is unset from this handler.
   *
   * @param handler the handler to be notified
   * @return this
   */
  public VertxHandler<C> removeHandler(Handler<C> handler) {
    this.removeHandler = handler;
    return this;
  }

  public C getConnection() {
    return conn;
  }

  @Override
  public void channelWritabilityChanged(ChannelHandlerContext ctx) {
    C conn = getConnection();
    conn.channelWritabilityChanged();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext chctx, final Throwable t) {
    C connection = getConnection();
    if (connection != null) {
      connection.handleException(t);
    }
    chctx.close();
  }

  @Override
  public void channelInactive(ChannelHandlerContext chctx) {
    conn.handleClosed();
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    conn.endReadAndFlush();
  }

  @Override
  public void channelRead(ChannelHandlerContext chctx, Object msg) {
    conn.read(msg);
  }

  @Override
  public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    conn.handleClose(promise);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent) {
      conn.handleIdle((IdleStateEvent) evt);
    }
    conn.handleEvent(evt);
  }
}
