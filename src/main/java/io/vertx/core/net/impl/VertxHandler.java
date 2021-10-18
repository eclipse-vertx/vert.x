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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.Handler;
import io.vertx.core.buffer.impl.VertxByteBufAllocator;

import java.util.function.Function;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class VertxHandler<C extends ConnectionBase> extends ChannelDuplexHandler {

  public static <C extends ConnectionBase> VertxHandler<C> create(Function<ChannelHandlerContext, C> connectionFactory) {
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
   * Copy and release the {@code buf} when necessary.
   *
   * <p> This methods assuming the has full ownership of the buffer.
   *
   * <p> This method assumes that pooled buffers are allocated by {@code PooledByteBufAllocator}
   *
   * <p> The returned buffer will not need to be released and can be wrapped by a {@link io.vertx.core.buffer.Buffer}.
   *
   * @param buf the buffer
   * @return a safe buffer to use
   */
  public static ByteBuf safeBuffer(ByteBuf buf) {
    if (buf != Unpooled.EMPTY_BUFFER && (buf.alloc() instanceof PooledByteBufAllocator || buf instanceof CompositeByteBuf)) {
      try {
        if (buf.isReadable()) {
          ByteBuf buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(buf.readableBytes());
          buffer.writeBytes(buf, buf.readerIndex(), buf.readableBytes());
          return buffer;
        } else {
          return Unpooled.EMPTY_BUFFER;
        }
      } finally {
        buf.release();
      }
    }
    return buf;
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
    conn.handleInterestedOpsChanged();
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
    conn.close(promise);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent) {
      conn.handleIdle((IdleStateEvent) evt);
    }
    conn.handleEvent(evt);
  }
}
