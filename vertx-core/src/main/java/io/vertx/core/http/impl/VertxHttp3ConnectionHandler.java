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
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.incubator.codec.http3.*;
import io.netty.incubator.codec.quic.QuicConnectionCloseEvent;
import io.netty.incubator.codec.quic.QuicDatagramExtensionEvent;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamLimitChangedEvent;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.vertx.core.Handler;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpSettings;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.ShutdownEvent;

import java.util.function.Function;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
class VertxHttp3ConnectionHandler<C extends Http3ConnectionBase> extends Http3RequestStreamInboundHandler {
  private static final InternalLogger logger = InternalLoggerFactory.getInstance(VertxHttp3ConnectionHandler.class);

  private final Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory;
  private C connection;
  private ChannelHandlerContext chctx;
  private final Promise<C> connectFuture;
  private boolean settingsRead;
  private Handler<C> addHandler;
  private Handler<C> removeHandler;
  private final HttpSettings httpSettings;
  private final boolean isServer;

  private boolean read;
  private Http3ConnectionHandler connectionHandlerInternal;
  private ChannelHandler streamHandlerInternal;

  public static final AttributeKey<VertxHttpStreamBase> HTTP3_MY_STREAM_KEY =
    AttributeKey.valueOf(VertxHttpStreamBase.class
    , "HTTP3MyStream");

  public VertxHttp3ConnectionHandler(
    Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory,
    ContextInternal context,
    HttpSettings httpSettings, boolean isServer) {
    this.connectionFactory = connectionFactory;
    this.httpSettings = httpSettings;
    connectFuture = new DefaultPromise<>(context.nettyEventLoop());
    this.isServer = isServer;
    createStreamHandler();
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

  private void onSettingsRead(ChannelHandlerContext ctx, HttpSettings settings) {
    this.chctx = ctx;
    this.connection = connectionFactory.apply(this);
    this.connection.onSettingsRead(ctx, settings);
    this.settingsRead = true;
    if (addHandler != null) {
      addHandler.handle(connection);
    }
    this.connectFuture.trySuccess(connection);
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
    logger.error("VertxHttp3ConnectionHandler caught exception!", cause);
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

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof SslHandshakeCompletionEvent) {
      SslHandshakeCompletionEvent completion = (SslHandshakeCompletionEvent) evt;
      if (!completion.isSuccess()) {
        connectFuture.tryFailure(completion.cause());
      }
    } else if (evt instanceof IdleStateEvent) {
      connection.handleIdle((IdleStateEvent) evt);
    } else if (evt instanceof QuicDatagramExtensionEvent) {
      logger.debug("Received event QuicDatagramExtensionEvent");
      ctx.fireUserEventTriggered(evt);
    } else if (evt instanceof QuicStreamLimitChangedEvent) {
      logger.debug("Received event QuicStreamLimitChangedEvent");
      ctx.fireUserEventTriggered(evt);
    } else if (evt instanceof QuicConnectionCloseEvent) {
      logger.debug("Received event QuicConnectionCloseEvent");
      ctx.fireUserEventTriggered(evt);
    } else if (evt == ChannelInputShutdownEvent.INSTANCE) {
      logger.debug("Received event ChannelInputShutdownEvent");
      channelInputShutdown(ctx);
      ctx.fireUserEventTriggered(evt);
    } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
      logger.debug("Received event ChannelInputShutdownReadComplete");
      ctx.fireUserEventTriggered(evt);
    } else if (evt instanceof ShutdownEvent) {
      logger.debug("Received event ShutdownEvent");
      ctx.fireUserEventTriggered(evt);
    } else {
      logger.debug("Received unhandled event: {}", evt);
      ctx.fireUserEventTriggered(evt);
    }
  }

  public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    connection.onGoAwayReceived(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(BufferInternal.buffer(debugData)));
  }

  public void writeHeaders(QuicStreamChannel stream, VertxHttpHeaders headers, boolean end, StreamPriorityBase priority,
                           boolean checkFlush, FutureListener<Void> listener) {
    stream.updatePriority(new QuicStreamPriority(priority.urgency(), priority.isIncremental()));
    Http3Headers http3Headers = headers.getHeaders();

    if (isServer) {
      http3Headers.set(HttpHeaderNames.USER_AGENT, "Vertx Http3Server");
    } else {
      http3Headers.set(HttpHeaderNames.USER_AGENT, "Vertx Http3Client");
    }
    ChannelPromise promise = listener == null ? stream.voidPromise() : stream.newPromise().addListener(listener);
    if (end) {
      promise.unvoid().addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
    }
    stream.write(new DefaultHttp3HeadersFrame(http3Headers), promise);

    if (checkFlush) {
      checkFlush();
    }
  }

  public void writeData(QuicStreamChannel stream, ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    stream.write(chunk).addListener(promise);
  }

  private void checkFlush() {
    if (!read) {
      chctx.channel().flush();
    }
  }

  @Override
  protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
    VertxHttpStreamBase stream = getLocalControlVertxHttpStream(ctx);
    logger.debug("Received Http3HeadersFrame frame.");
    connection.onHeadersRead(ctx, stream, frame.headers(), 0, false);
  }

  @Override
  protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
    VertxHttpStreamBase stream = getLocalControlVertxHttpStream(ctx);
    connection.onDataRead(ctx, stream, frame.content(), 0, false);
  }

  @Override
  protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
    VertxHttpStreamBase stream = getLocalControlVertxHttpStream(ctx);
    connection.onDataRead(ctx, stream, Unpooled.buffer(), 0, true);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    read = false;
    super.channelReadComplete(ctx);
  }

  private static VertxHttpStreamBase getLocalControlVertxHttpStream(ChannelHandlerContext ctx) {
    return Http3.getLocalControlStream(ctx.channel().parent()).attr(HTTP3_MY_STREAM_KEY).get();
  }

  static void setLocalControlVertxHttpStream(QuicStreamChannel quicStreamChannel, VertxHttpStreamBase stream) {
    Http3.getLocalControlStream(quicStreamChannel.parent()).attr(HTTP3_MY_STREAM_KEY).set(stream);
  }

  //  @Override

  private void createHttp3ConnectionHandler(boolean isServer) {
    this.connectionHandlerInternal = isServer ? createHttp3ServerConnectionHandler() :
      createHttp3ClientConnectionHandler();
  }

  private void createStreamHandler() {
    this.streamHandlerInternal = new ChannelInboundHandlerAdapter() {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof DefaultHttp3SettingsFrame) {
          DefaultHttp3SettingsFrame http3SettingsFrame = (DefaultHttp3SettingsFrame) msg;
          logger.debug("Received frame http3SettingsFrame");
          onSettingsRead(ctx, new HttpSettings(http3SettingsFrame));
          VertxHttp3ConnectionHandler.this.connection.updateHttpSettings(new HttpSettings(http3SettingsFrame));
//          Thread.sleep(70000);
          super.channelRead(ctx, msg);
        } else if (msg instanceof DefaultHttp3GoAwayFrame) {
          super.channelRead(ctx, msg);
          DefaultHttp3GoAwayFrame http3GoAwayFrame = (DefaultHttp3GoAwayFrame) msg;
          logger.debug("Received frame http3GoAwayFrame.");
          onGoAwayReceived((int) http3GoAwayFrame.id(), -1, Unpooled.EMPTY_BUFFER);
        } else if (msg instanceof DefaultHttp3UnknownFrame) {
          DefaultHttp3UnknownFrame http3UnknownFrame = (DefaultHttp3UnknownFrame) msg;

          if(logger.isDebugEnabled()) {
            byte[] arr = new byte[http3UnknownFrame.content().readableBytes()];
            http3UnknownFrame.content().retain().readBytes(arr);
            logger.debug("Received frame http3UnknownFrame with content: {}", arr);
          }
          super.channelRead(ctx, msg);
        } else {
          super.channelRead(ctx, msg);
        }
      }
    };
  }

  private Http3ServerConnectionHandler createHttp3ServerConnectionHandler() {
    return new Http3ServerConnectionHandler(new ChannelInitializer<QuicStreamChannel>() {
      @Override
      protected void initChannel(QuicStreamChannel ch) throws Exception {
        logger.debug("Init QuicStreamChannel...");
        ch.pipeline().addLast(new Http3FrameToHttpObjectCodec(true));
        ch.pipeline().addLast(VertxHttp3ConnectionHandler.this);
      }
      //TODO: correct the settings and streamHandlerIssue:
    }, this.streamHandlerInternal, null, null/*httpSettings.toNettyHttp3Settings()*/, false);
  }

  private Http3ClientConnectionHandler createHttp3ClientConnectionHandler() {
    return new Http3ClientConnectionHandler(this.streamHandlerInternal, null, null,
      httpSettings.toNettyHttp3Settings(), false);
  }

  public Http3ConnectionHandler getHttp3ConnectionHandler() {
    return connectionHandlerInternal;
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

  public HttpSettings initialSettings() {
    return httpSettings;
  }

  public void gracefulShutdownTimeoutMillis(long timeout) {
    //TODO: implement
  }

  public ChannelFuture writePing(long aLong) {
    ChannelPromise promise = chctx.newPromise();
    //TODO: implement
    return promise;
  }

  public boolean goAwayReceived() {
    return getHttp3ConnectionHandler().isGoAwayReceived();
  }

  public C connection() {
    return connection;
  }

}
