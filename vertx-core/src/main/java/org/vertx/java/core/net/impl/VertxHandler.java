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
package org.vertx.java.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class VertxHandler<C extends ConnectionBase> extends ChannelDuplexHandler {

  public static final AttributeKey<ConnectionBase> KEY = new AttributeKey<>("connection");

  protected final VertxInternal vertx;
  protected VertxHandler(VertxInternal vertx) {
    this.vertx = vertx;
  }

  protected DefaultContext getContext(C connection) {
    return connection.getContext();
  }

  protected static ByteBuf safeBuffer(ByteBuf buf) {
    if (buf == Unpooled.EMPTY_BUFFER) {
      return buf;
    }
    if (buf.isDirect() || buf instanceof CompositeByteBuf) {
      try {
        if (buf.isReadable()) {
          ByteBuf buffer =  buf.alloc().heapBuffer(buf.readableBytes());
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

  @SuppressWarnings("unchecked")
  @Override
  public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
    final Channel ch = ctx.channel();
    final C conn = (C) ch.attr(KEY).get();
    if (conn != null) {
      conn.setWritable(ctx.channel().isWritable());
      DefaultContext context = getContext(conn);
      if (context.isOnCorrectWorker(ch.eventLoop())) {
        try {
          vertx.setContext(context);
          conn.handleInterestedOpsChanged();
        } catch (Throwable t) {
          context.reportException(t);
        }
      } else {
        context.execute(new Runnable() {
          public void run() {
            conn.handleInterestedOpsChanged();
          }
        });
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void exceptionCaught(ChannelHandlerContext chctx, final Throwable t) throws Exception {
    final Channel ch = chctx.channel();
    // Don't remove the connection at this point, or the handleClosed won't be called when channelInactive is called!
    final C connection = (C) ch.attr(KEY).get();
    if (connection != null) {
      DefaultContext context = getContext(connection);
      context.execute(ch.eventLoop(), new Runnable() {
        public void run() {
          try {
            if (ch.isOpen()) {
              ch.close();
            }
          } catch (Throwable ignore) {
          }
          connection.handleException(t);
        }
      });
    } else {
      // Ignore - any exceptions before a channel exists will be passed manually via the failed(...) method
      // Any exceptions after a channel is closed can be ignored
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void channelInactive(ChannelHandlerContext chctx) throws Exception {
    final Channel ch = chctx.channel();
    final C connection = (C) ch.attr(KEY).getAndRemove();
    if (connection != null) {
      DefaultContext context = getContext(connection);
      context.execute(ch.eventLoop(), new Runnable() {
        public void run() {
          connection.handleClosed();
        }
      });
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    final C conn = (C) ctx.channel().attr(KEY).get();
    if (conn != null) {
      DefaultContext context = getContext(conn);
      // Only mark end read if its not a WorkerVerticle
      if (context.isOnCorrectWorker(ctx.channel().eventLoop())) {
        conn.endReadAndFlush();
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
    final Object message = safeObject(msg);
    final C connection = (C) chctx.channel().attr(KEY).get();

    DefaultContext context;
    if (connection != null) {
      context = getContext(connection);
      // Only mark start read if we are on the correct worker. This is needed as while we are in read this may will
      // delay flushes, which is a problem when we are no on the correct worker. This is mainly a problem as
      // WorkerVerticle may block.
      if (context.isOnCorrectWorker(chctx.channel().eventLoop())) {
        connection.startRead();
      }
    } else {
      context = null;
    }
    channelRead(connection, context, chctx, message);
  }

  protected abstract void channelRead(C connection, DefaultContext context, ChannelHandlerContext chctx, Object msg) throws Exception;

  protected abstract Object safeObject(Object msg) throws Exception;
}
