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

package io.vertx.core.net.impl;

import io.netty.buffer.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class VertxHandler<C extends ConnectionBase> extends ChannelDuplexHandler {

  public static ByteBuf safeBuffer(ByteBufHolder holder, ByteBufAllocator allocator) {
    return safeBuffer(holder.content(), allocator);
  }

  public static ByteBuf safeBuffer(ByteBuf buf, ByteBufAllocator allocator) {
    if (buf == Unpooled.EMPTY_BUFFER) {
      return buf;
    }
    if (buf.isDirect() || buf instanceof CompositeByteBuf) {
      try {
        if (buf.isReadable()) {
          ByteBuf buffer =  allocator.heapBuffer(buf.readableBytes());
          buffer.writeBytes(buf);
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

  private static final Handler<Object> NULL_HANDLER = m -> { };

  private C conn;
  private Handler<Void> endReadAndFlush;
  private Handler<C> addHandler;
  private Handler<C> removeHandler;
  private Handler<Object> messageHandler;

  /**
   * Set the connection, this is usually called by subclasses when the channel is added to the pipeline.
   *
   * @param connection the connection
   */
  protected void setConnection(C connection) {
    conn = connection;
    endReadAndFlush = v -> conn.endReadAndFlush();
    messageHandler = ((ConnectionBase)conn)::handleRead; // Dubious cast to make compiler happy
    if (addHandler != null) {
      addHandler.handle(connection);
    }
  }

  /**
   * Fail the connection, the {@code error} will be sent to the pipeline and the connection will
   * stop processing any further message.
   *
   * @param error the {@code Throwable} to propagate
   */
  void fail(Throwable error) {
    messageHandler = NULL_HANDLER;
    conn.chctx.pipeline().fireExceptionCaught(error);
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
  public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
    C conn = getConnection();
    ContextInternal context = conn.getContext();
    context.executeFromIO(v -> conn.handleInterestedOpsChanged());
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext chctx, final Throwable t) throws Exception {
    Channel ch = chctx.channel();
    // Don't remove the connection at this point, or the handleClosed won't be called when channelInactive is called!
    C connection = getConnection();
    if (connection != null) {
      ContextInternal context = conn.getContext();
      context.executeFromIO(v -> {
        try {
          if (ch.isOpen()) {
            ch.close();
          }
        } catch (Throwable ignore) {
        }
        connection.handleException(t);
      });
    } else {
      ch.close();
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext chctx) throws Exception {
    if (removeHandler != null) {
      removeHandler.handle(conn);
    }
    ContextInternal context = conn.getContext();
    context.executeFromIO(v -> conn.handleClosed());
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    ContextInternal context = conn.getContext();
    context.executeFromIO(endReadAndFlush);
  }

  @Override
  public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
    ContextInternal ctx = conn.getContext();
    ctx.executeFromIO(msg, messageHandler);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.ALL_IDLE) {
      ctx.close();
    }
    ctx.fireUserEventTriggered(evt);
  }
}
