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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler;
import io.netty.incubator.codec.http3.Http3ConnectionHandler;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.function.Function;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
class VertxHttp3ConnectionHandler<C extends Http3ConnectionBase> extends Http3RequestStreamInboundHandler {

  private final Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory;
  private C connection;
  private ChannelHandlerContext chctx;
  private final Promise<C> connectFuture;
  private boolean settingsRead;
  private Handler<C> addHandler;
  private Handler<C> removeHandler;
  private final Http3SettingsFrame http3InitialSettings;

  private boolean read;
  private Http3ConnectionHandler connectionHandlerInternal;

  public static final AttributeKey<Http3ClientStream> HTTP3_MY_STREAM_KEY = AttributeKey.valueOf(Http3ClientStream.class
    , "HTTP3MyStream");

  public VertxHttp3ConnectionHandler(
    Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory,
    EventLoopContext context,
    Http3SettingsFrame http3InitialSettings, boolean isServer) {
    this.connectionFactory = connectionFactory;
    this.http3InitialSettings = http3InitialSettings;
    connectFuture = new DefaultPromise<>(context.nettyEventLoop());
    createHttp3ConnectionHandler(isServer);
  }

  public Future<C> connectFuture() {
    if (connectFuture == null) {
      throw new IllegalStateException();
    }
    return connectFuture;
  }

  public ChannelHandlerContext context() {
    return chctx;
  }

  /**
   * This method will be called during channel initialization, and the onSettingsRead logic from HTTP/2 has been
   * copied here.
   */
  private void channelInitialized(ChannelHandlerContext ctx) {
    this.chctx = ctx;
    this.connection = connectionFactory.apply(this);
    this.settingsRead = true;
    if (addHandler != null) {
      addHandler.handle(connection);
    }
    this.connectFuture.setSuccess(connection);
  }


  /**
   * Set a handler to be called when the connection is set on this handler.
   *
   * @param handler the handler to be notified
   * @return this
   */
  public VertxHttp3ConnectionHandler<C> addHandler(Handler<C> handler) {
    this.addHandler = handler;
    return this;
  }

  /**
   * Set a handler to be called when the connection is unset from this handler.
   *
   * @param handler the handler to be notified
   * @return this
   */
  public VertxHttp3ConnectionHandler<C> removeHandler(Handler<C> handler) {
    removeHandler = handler;
    connection = null;
    return this;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    super.handlerAdded(ctx);
    chctx = ctx;
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    super.exceptionCaught(ctx, cause);
    ctx.close();
  }


  @Override
  public void channelInactive(ChannelHandlerContext chctx) throws Exception {
    if (connection != null) {
      if (settingsRead) {
        if (removeHandler != null) {
          removeHandler.handle(connection);
        }
      } else {
        connectFuture.tryFailure(ConnectionBase.CLOSED_EXCEPTION);
      }
      super.channelInactive(chctx);
      connection.handleClosed();
    } else {
      super.channelInactive(chctx);
    }
  }

  public void writeHeaders(QuicStreamChannel stream, Http3Headers headers, boolean end, StreamPriorityBase priority,
                           boolean checkFlush, FutureListener<Void> listener) {
//    ChannelPromise promise = listener == null ? chctx.voidPromise() : chctx.newPromise().addListener(listener);
    stream
      .write(headers)
      .addListener(listener)
      .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)  //TODO: review
    ;
    if (checkFlush) {
      checkFlush();
    }
  }

  public void writeData(QuicStreamChannel stream, ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    stream.write(chunk).addListener(promise);
  }

  @Override
  protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
    read = true;
    connection.onDataRead(ctx, controlStream(ctx).attr(HTTP3_MY_STREAM_KEY).get(), frame.content(), 0, true);
  }

  @Override
  protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
    read = true;
    connection.onHeadersRead(ctx, controlStream(ctx).attr(HTTP3_MY_STREAM_KEY).get(), frame.headers(), false);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    read = false;
    // Super will flush
    super.channelReadComplete(ctx);
  }

  @Override
  protected void channelInputClosed(ChannelHandlerContext ctx) {
    ctx.close();
  }

  private void checkFlush() {
    if (!read) {
      chctx.channel().flush();
    }
  }

  private void createHttp3ConnectionHandler(boolean isServer) {
    this.connectionHandlerInternal = isServer ? createHttp3ServerConnectionHandler() :
      createHttp3ClientConnectionHandler();
  }

  private Http3ServerConnectionHandler createHttp3ServerConnectionHandler() {
    return new Http3ServerConnectionHandler(getInboundControlStreamHandler(), null, null,
      http3InitialSettings, false);
  }

  private Http3ClientConnectionHandler createHttp3ClientConnectionHandler() {
    return new Http3ClientConnectionHandler(getInboundControlStreamHandler(), null, null,
      http3InitialSettings, false);
  }

  private ChannelInitializer<QuicStreamChannel> getInboundControlStreamHandler() {
    return new ChannelInitializer<QuicStreamChannel>() {
      @Override
      protected void initChannel(QuicStreamChannel ch) throws Exception {
      }

      @Override
      public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        channelInitialized(ctx);
        super.handlerAdded(ctx);
      }
    };

  }

  public Http3ConnectionHandler getHttp3ConnectionHandler() {
    return connectionHandlerInternal;
  }

  public C connection() {
    return connection;
  }

  private void _writePriority(QuicStreamChannel stream, int urgency, boolean incremental) {
    stream.updatePriority(new QuicStreamPriority(urgency, incremental));
  }

  public void writePriority(QuicStreamChannel stream, int urgency, boolean incremental) {
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writePriority(stream, urgency, incremental);
    } else {
      executor.execute(() -> {
        _writePriority(stream, urgency, incremental);
      });
    }
  }
}
