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
package io.vertx.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;

import java.util.Map;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class VertxHandler<C extends ConnectionBase> extends ChannelDuplexHandler {

  protected final VertxInternal vertx;
  protected final Map<Channel, C> connectionMap;

  protected VertxHandler(VertxInternal vertx, Map<Channel, C> connectionMap) {
    this.vertx = vertx;
    this.connectionMap = connectionMap;
  }

  protected ContextImpl getContext(C connection) {
    return connection.getContext();
  }

  protected static ByteBuf safeBuffer(ByteBuf buf, ByteBufAllocator allocator) {
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

  @Override
  public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
    Channel ch = ctx.channel();
    C conn = connectionMap.get(ch);
    if (conn != null) {
      ContextImpl context = getContext(conn);
      context.executeFromIO(conn::handleInterestedOpsChanged);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext chctx, final Throwable t) throws Exception {
    Channel ch = chctx.channel();
    // Don't remove the connection at this point, or the handleClosed won't be called when channelInactive is called!
    C connection = connectionMap.get(ch);
    if (connection != null) {
      ContextImpl context = getContext(connection);
      context.executeFromIO(() -> {
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
    Channel ch = chctx.channel();
    C connection = connectionMap.remove(ch);
    if (connection != null) {
      ContextImpl context = getContext(connection);
      context.executeFromIO(connection::handleClosed);
    }
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    C conn = connectionMap.get(ctx.channel());
    if (conn != null) {
      ContextImpl context = getContext(conn);
      context.executeFromIO(conn::endReadAndFlush);
    }
  }

  @Override
  public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
    Object message = safeObject(msg, chctx.alloc());
    C connection = connectionMap.get(chctx.channel());

    ContextImpl context;
    if (connection != null) {
      context = getContext(connection);
      context.executeFromIO(connection::startRead);
    } else {
      context = null;
    }
    channelRead(connection, context, chctx, message);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.ALL_IDLE) {
      ctx.close();
    }
  }

  protected abstract void channelRead(C connection, ContextImpl context, ChannelHandlerContext chctx, Object msg) throws Exception;

  protected abstract Object safeObject(Object msg, ByteBufAllocator allocator) throws Exception;
}
