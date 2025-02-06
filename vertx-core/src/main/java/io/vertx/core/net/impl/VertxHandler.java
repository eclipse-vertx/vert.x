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

import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.incubator.codec.http3.*;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.PromiseNotifier;
import io.vertx.core.Handler;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import java.util.function.Function;

import static io.vertx.core.net.impl.Http3Utils.*;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class VertxHandler<C extends VertxConnection> extends ChannelDuplexHandler {
  private static final Logger log = LoggerFactory.getLogger(VertxHandler.class);

  public static <C extends VertxConnection> VertxHandler<C> create(Function<ChannelHandlerContext, C> connectionFactory) {
    return new VertxHandler<>(connectionFactory);
  }

  public static <C extends VertxConnection> VertxHandler<C> create(Function<ChannelHandlerContext, C> connectionFactory, boolean isHttp3, boolean isServer) {
    VertxHandler<C> handler = new VertxHandler<>(connectionFactory);
    handler.isHttp3 = isHttp3;
    handler.isServer = isServer;
    return handler;
  }

  public static final String H3_SRV_CONNECTION_HANDLER_NAME = "h3ServerHandler";
  public static final String H3_CLIENT_CONNECTION_HANDLER_NAME = "h3ClientHandler";
  private final Function<ChannelHandlerContext, C> connectionFactory;
  private C conn;
  private Handler<C> addHandler;
  private Handler<C> removeHandler;
  private boolean isHttp3;
  private boolean isServer;
  private boolean clientWithHttp3Framing = false;
  private Channel streamChannel;

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
    conn.setWriteHandler(VertxHandler.this::write);
    if (addHandler != null) {
      addHandler.handle(connection);
    }
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    if (isHttp3) {
      if (isServer) {
        setConnection(connectionFactory.apply(ctx));

        if (ctx.pipeline().get(H3_SRV_CONNECTION_HANDLER_NAME) == null) {
          ctx.pipeline().addLast(H3_SRV_CONNECTION_HANDLER_NAME, new Http3ServerConnectionHandler(new Http3FramedStreamChannelHandler()));
        }
      } else {
        ctx.pipeline().addLast(H3_CLIENT_CONNECTION_HANDLER_NAME, new Http3ClientConnectionHandler());
        setConnection(connectionFactory.apply(ctx));
      }
    } else {
      setConnection(connectionFactory.apply(ctx));
    }
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) {
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
    log.error("There is error in VertxHandler.", t);
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
  public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
    if (isHttp3) {
      chctx.fireChannelRead(msg);
      return;
    }
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

  public void write(ChannelHandlerContext chctx, Object msg, boolean flush, ChannelPromise promise) {
    if (!isHttp3) {
      if (flush) {
        chctx.writeAndFlush(msg, promise);
      } else {
        chctx.write(msg, promise);
      }
      return;
    }

    if (isServer) {
      writeHttp3(streamChannel, msg, promise, flush);
    } else {
      Future<QuicStreamChannel> streamChannelFuture;
      if (clientWithHttp3Framing) {  //TODO: clientWithHttp3Framing block should be removed
        streamChannelFuture = Http3.newRequestStream(((QuicChannel) chctx.channel()), new Http3FramedStreamChannelHandler());
      } else {
        streamChannelFuture = ((QuicChannel) chctx.channel()).createStream(QuicStreamType.BIDIRECTIONAL, new ClientStreamChannelWithoutHttp3FramingHandler());
      }
      streamChannelFuture.addListener((GenericFutureListener<Future<QuicStreamChannel>>) streamChannelFuture0 -> {
        if (streamChannelFuture0.isSuccess()) {
          writeHttp3(streamChannelFuture0.get(), msg, promise, flush);
        } else {
          exceptionCaught(chctx, streamChannelFuture0.cause());
        }
      });
    }
  }

  private void writeHttp3(Channel channel, Object msg, ChannelPromise promise, boolean flush) {
    if ((isServer || clientWithHttp3Framing) && !(msg instanceof EmptyByteBuf)) {  //TODO: clientWithHttp3Framing block should be removed
      msg = new DefaultHttp3UnknownFrame(MIN_RESERVED_FRAME_TYPE, (ByteBuf) msg);
    } else {
      ByteBuf content = (ByteBuf) msg;
      ByteBuf out = channel.alloc().directBuffer();
      writeVariableLengthInteger(out, MIN_RESERVED_FRAME_TYPE);
      writeVariableLengthInteger(out, content.readableBytes());
      msg = Unpooled.wrappedUnmodifiableBuffer(out, content.retain());
    }

    if (flush) {
      channel.writeAndFlush(msg, channel.newPromise().addListener(new PromiseNotifier<>(promise)));
    } else {
      channel.write(msg, channel.newPromise().addListener(new PromiseNotifier<>(promise)));
    }
  }

  private class ClientStreamChannelWithoutHttp3FramingHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof ByteBuf) {
        ByteBuf buf = (ByteBuf) msg;
        buf.readBytes(3);
        ByteBuf buf1 = ctx.alloc().directBuffer().writeBytes(buf);
        getConnection().read(buf1);
        ReferenceCountUtil.release(buf);
      } else {
        super.channelRead(ctx, msg);
      }
    }
  }

  public class Http3FramedStreamChannelHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
      if (isServer) {
        VertxHandler.this.streamChannel = ctx.channel();
      }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      log.info((isServer ? "Server" : "Client") + ": Received msg with class type: " + msg.getClass().getSimpleName());
      if (msg instanceof ByteBufHolder) {
        getConnection().read(((ByteBufHolder) msg).content());
      } else if (msg instanceof ByteBuf){
        getConnection().read(msg);
      } else {
        super.channelRead(ctx, msg);
      }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
      getConnection().endReadAndFlush();
      super.channelReadComplete(ctx);
    }
  }

  public void setClientWithHttp3Framing(boolean clientWithHttp3Framing) {
    this.clientWithHttp3Framing = clientWithHttp3Framing;
  }
}
