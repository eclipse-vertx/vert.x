/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;

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

  @Override
  public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
    final Channel ch = ctx.channel();
    final C conn = connectionMap.get(ch);
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

  @Override
  public void exceptionCaught(ChannelHandlerContext chctx, final Throwable t) throws Exception {
    final Channel ch = chctx.channel();
    // Don't remove the connection at this point, or the handleClosed won't be called when channelInactive is called!
    final C connection = connectionMap.get(ch);
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

  @Override
  public void channelInactive(ChannelHandlerContext chctx) throws Exception {
    final Channel ch = chctx.channel();
    final C connection = connectionMap.remove(ch);
    if (connection != null) {
      DefaultContext context = getContext(connection);
      context.execute(ch.eventLoop(), new Runnable() {
        public void run() {
          connection.handleClosed();
        }
      });
    }
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    C conn = connectionMap.get(ctx.channel());
    if (conn != null) {
      conn.endReadAndFlush();
      DefaultContext context = getContext(conn);
      // Only mark end read if its not a WorkerVerticle
      if (context.isOnCorrectWorker(ctx.channel().eventLoop())) {
        conn.endReadAndFlush();
      }
    }
  }

  @Override
  public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
    final Object message = safeObject(msg);
    final C connection = connectionMap.get(chctx.channel());

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
