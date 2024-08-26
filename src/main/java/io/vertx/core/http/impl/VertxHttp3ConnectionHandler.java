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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.incubator.codec.http3.DefaultHttp3GoAwayFrame;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler;
import io.netty.incubator.codec.http3.Http3ConnectionHandler;
import io.netty.incubator.codec.http3.Http3Headers;
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
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.Objects;
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
  private final Http3SettingsFrame http3InitialSettings;

  private boolean read;
  private Http3ConnectionHandler connectionHandlerInternal;
  private ChannelHandler streamHandlerInternal;

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
    createStreamHandler();
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

  private void onSettingsRead(ChannelHandlerContext ctx, Http3SettingsFrame settings) {
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

  public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    connection.onGoAwayReceived(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(Buffer.buffer(debugData)));
  }

  public void writeHeaders(QuicStreamChannel stream, Http3Headers headers, boolean end, StreamPriorityBase priority,
                           boolean checkFlush, FutureListener<Void> listener) {
    stream.updatePriority(new QuicStreamPriority(priority.urgency(), priority.isIncremental()));

    DefaultFullHttpRequest request = new DefaultFullHttpRequest(
      HttpUtils.toNettyHttpVersion(HttpVersion.HTTP_1_1),
      HttpMethod.valueOf(String.valueOf(headers.method())).toNetty(),
      String.valueOf(headers.path())
    );

    request.headers().setAll(convertToHttpHeaders(headers));

    ChannelFuture future = stream.write(request);
    if (listener != null) {
      future.addListener(listener);
    }

    if (checkFlush) {
      checkFlush();
    }
  }

  private HttpHeaders convertToHttpHeaders(Http3Headers http3Headers) {
    HttpHeaders headers = new HeadersMultiMap();
    http3Headers.iterator().forEachRemaining(entry -> {
      String name = Objects.requireNonNull(Http3Headers.PseudoHeaderName.getPseudoHeader(entry.getKey())).name();
      headers.add(name, String.valueOf(entry.getValue()));
    });
    headers.add(HttpHeaderNames.HOST, http3Headers.authority());
    return headers;
  }

  public void writeData(QuicStreamChannel stream, ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    stream.write(chunk).addListener(promise);
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

  private static Http3ClientStream getHttp3ClientStream(ChannelHandlerContext ctx) {
    return Http3.getLocalControlStream(ctx.channel().parent()).attr(HTTP3_MY_STREAM_KEY).get();
  }

  static void setHttp3ClientStream(QuicStreamChannel quicStreamChannel, Http3ClientStream stream) {
    Http3.getLocalControlStream(quicStreamChannel.parent()).attr(HTTP3_MY_STREAM_KEY).set(stream);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    read = false;
    // Super will flush
    super.channelReadComplete(ctx);
  }

//  @Override
//  protected void channelInputClosed(ChannelHandlerContext ctx) {
//    ctx.close();
//  }

  private void checkFlush() {
    if (!read) {
      chctx.channel().flush();
    }
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
          logger.debug("Received http3SettingsFrame: {} ", http3SettingsFrame);
//          VertxHttp3ConnectionHandler.this.connection.updateSettings(http3SettingsFrame);
          onSettingsRead(ctx, http3SettingsFrame);
//          Thread.sleep(10000);
        } else if (msg instanceof DefaultHttp3GoAwayFrame) {
          DefaultHttp3GoAwayFrame http3GoAwayFrame = (DefaultHttp3GoAwayFrame) msg;
          logger.debug("Received http3GoAwayFrame: {}", http3GoAwayFrame);
          onGoAwayReceived((int) http3GoAwayFrame.id(), -1, Unpooled.EMPTY_BUFFER);
        }
        super.channelRead(ctx, msg);
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
    }, null, null,
      http3InitialSettings, false);
  }

  private Http3ClientConnectionHandler createHttp3ClientConnectionHandler() {
    return new Http3ClientConnectionHandler(null, null, null,
      http3InitialSettings, false);
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

  public ChannelHandler getStreamHandler() {
    return streamHandlerInternal;
  }

  public Http3SettingsFrame initialSettings() {
    return http3InitialSettings;
  }

  public void gracefulShutdownTimeoutMillis(long timeout) {
    //TODO: implement
  }

  public ChannelFuture writePing(long aLong) {
    ChannelPromise promise = chctx.newPromise();
    //TODO: implement
    return promise;
  }
}
