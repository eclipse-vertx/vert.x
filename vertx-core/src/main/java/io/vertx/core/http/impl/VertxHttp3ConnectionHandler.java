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
import io.netty.handler.codec.http.*;
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
import io.vertx.core.http.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.function.Function;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
class VertxHttp3ConnectionHandler<C extends Http3ConnectionBase> extends ChannelInboundHandlerAdapter {
  private static final InternalLogger logger = InternalLoggerFactory.getInstance(VertxHttp3ConnectionHandler.class);

  private final Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory;
  private C connection;
  private ChannelHandlerContext chctx;
  private final Promise<C> connectFuture;
  private boolean settingsRead;
  private Handler<C> addHandler;
  private Handler<C> removeHandler;
  private final HttpSettings httpSettings;

  private boolean read;
  private Http3ConnectionHandler connectionHandlerInternal;
  private ChannelHandler streamHandlerInternal;
  private ChannelHandler userEventHandlerInternal;

  public static final AttributeKey<Http3ClientStream> HTTP3_MY_STREAM_KEY = AttributeKey.valueOf(Http3ClientStream.class
    , "HTTP3MyStream");

  public VertxHttp3ConnectionHandler(
    Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory,
    ContextInternal context,
    HttpSettings httpSettings, boolean isServer) {
    this.connectionFactory = connectionFactory;
    this.httpSettings = httpSettings;
    connectFuture = new DefaultPromise<>(context.nettyEventLoop());
    createStreamHandler();
    createUserEventHandler();
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
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
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
      logger.debug("Received event QuicDatagramExtensionEvent: {}", evt);
      ctx.fireUserEventTriggered(evt);
    } else if (evt instanceof QuicStreamLimitChangedEvent) {
      logger.debug("Received event QuicStreamLimitChangedEvent: {}", evt);
      ctx.fireUserEventTriggered(evt);
    } else if (evt instanceof QuicConnectionCloseEvent) {
      logger.debug("Received event QuicConnectionCloseEvent: {}", evt);
      ctx.fireUserEventTriggered(evt);
    } else if (evt == ChannelInputShutdownEvent.INSTANCE) {
      logger.debug("Received event ChannelInputShutdownEvent: {}", evt);
      channelInputClosed(ctx);
    } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
      logger.debug("Received event ChannelInputShutdownReadComplete: {}", evt);
      channelInputClosed(ctx);
    } else {
      ctx.fireUserEventTriggered(evt);
    }
  }

  public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    connection.onGoAwayReceived(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(BufferInternal.buffer(debugData)));
  }

  public void writeHeaders(QuicStreamChannel stream, VertxHttpHeaders headers, boolean end, StreamPriorityBase priority,
                           boolean checkFlush, FutureListener<Void> listener) {
    stream.updatePriority(new QuicStreamPriority(priority.urgency(), priority.isIncremental()));

    DefaultFullHttpRequest request = new DefaultFullHttpRequest(
      HttpUtils.toNettyHttpVersion(HttpVersion.HTTP_1_1),
      HttpMethod.valueOf(String.valueOf(headers.method())).toNetty(),
      String.valueOf(headers.path())
    );

    HttpHeaders httpHeaders = headers.toHttpHeaders();
    httpHeaders.add(HttpHeaderNames.HOST, headers.authority());

    request.headers().setAll(httpHeaders);

    ChannelFuture future = stream.write(request);
    if (listener != null) {
      future.addListener(listener);
    }

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
  public void channelRead(ChannelHandlerContext ctx, Object frame) throws Exception {
    read = true;

    Http3ClientStream stream = getHttp3ClientStream(ctx);

    if (frame instanceof HttpResponse) {
      HttpResponse httpResp = (HttpResponse) frame;
      Http3Headers headers = new DefaultHttp3Headers();
      httpResp.headers().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));
      headers.status(httpResp.status().codeAsText());
      connection.onHeadersRead(ctx, stream, headers, false);
    } else if (frame instanceof LastHttpContent) {
      LastHttpContent last = (LastHttpContent) frame;
      connection.onDataRead(ctx, stream, last.content(), 0, true);
    } else if (frame instanceof HttpContent) {
      HttpContent respBody = (HttpContent) frame;
      connection.onDataRead(ctx, stream, respBody.content(), 0, false);
    } else {
      super.channelRead(ctx, frame);
    }
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    read = false;
    // Super will flush
    super.channelReadComplete(ctx);
  }

  private static Http3ClientStream getHttp3ClientStream(ChannelHandlerContext ctx) {
    return Http3.getLocalControlStream(ctx.channel().parent()).attr(HTTP3_MY_STREAM_KEY).get();
  }

  static void setHttp3ClientStream(QuicStreamChannel quicStreamChannel, Http3ClientStream stream) {
    Http3.getLocalControlStream(quicStreamChannel.parent()).attr(HTTP3_MY_STREAM_KEY).set(stream);
  }

  //  @Override
  protected void channelInputClosed(ChannelHandlerContext ctx) {
    ctx.close();
  }

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
          logger.debug("Received frame http3SettingsFrame: {} ", http3SettingsFrame);
          onSettingsRead(ctx, new HttpSettings(http3SettingsFrame));
          VertxHttp3ConnectionHandler.this.connection.updateHttpSettings(new HttpSettings(http3SettingsFrame));
//          Thread.sleep(70000);
          super.channelRead(ctx, msg);
        } else if (msg instanceof DefaultHttp3GoAwayFrame) {
          super.channelRead(ctx, msg);
          DefaultHttp3GoAwayFrame http3GoAwayFrame = (DefaultHttp3GoAwayFrame) msg;
          logger.debug("Received frame http3GoAwayFrame: {}", http3GoAwayFrame);
          onGoAwayReceived((int) http3GoAwayFrame.id(), -1, Unpooled.EMPTY_BUFFER);
        } else if (msg instanceof DefaultHttp3UnknownFrame) {
          DefaultHttp3UnknownFrame http3UnknownFrame = (DefaultHttp3UnknownFrame) msg;
          logger.debug("Received frame http3UnknownFrame: {}", http3UnknownFrame);
          super.channelRead(ctx, msg);
        } else {
          super.channelRead(ctx, msg);
        }
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        VertxHttp3ConnectionHandler.this.exceptionCaught(ctx, cause);
      }
    };
  }

  private Http3ServerConnectionHandler createHttp3ServerConnectionHandler() {
    return new Http3ServerConnectionHandler(new ChannelInitializer<QuicStreamChannel>() {
      @Override
      protected void initChannel(QuicStreamChannel ch) throws Exception {
      }
    }, null, null, httpSettings.toNettyHttp3Settings(), false);
  }

  private Http3ClientConnectionHandler createHttp3ClientConnectionHandler() {
    assert this.streamHandlerInternal != null;
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

  private void createUserEventHandler() {
    this.userEventHandlerInternal = new ChannelInboundHandlerAdapter() {
      @Override
      public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        VertxHttp3ConnectionHandler.this.userEventTriggered(ctx, evt);
      }
    };
  }

  public ChannelHandler getUserEventHandler() {
    return this.userEventHandlerInternal;
  }
}
