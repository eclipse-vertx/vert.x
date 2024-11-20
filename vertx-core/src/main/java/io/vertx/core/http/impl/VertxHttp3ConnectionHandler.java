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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.incubator.codec.http3.*;
import io.netty.incubator.codec.quic.*;
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
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.ShutdownEvent;

import java.util.function.Function;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
class VertxHttp3ConnectionHandler<C extends Http3ConnectionBase> extends ChannelInboundHandlerAdapter {
  private static final InternalLogger logger = InternalLoggerFactory.getInstance(VertxHttp3ConnectionHandler.class);

  private final Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory;
  private final long initialMaxStreamsBidirectional;
  private C connection;
  private ChannelHandlerContext chctx;
  private Promise<C> connectFuture;
  private boolean settingsRead;
  private Handler<C> addHandler;
  private Handler<C> removeHandler;
  private final Http3SettingsFrame httpSettings;
  private final boolean isServer;
  private final String agentType;

  private boolean read;
  private static final AttributeKey<VertxHttpStreamBase> VERTX_STREAM_KEY =
    AttributeKey.valueOf(VertxHttpStreamBase.class, "VERTX_CHANNEL_STREAM");

  public VertxHttp3ConnectionHandler(
    Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory,
    ContextInternal context,
    Http3SettingsFrame httpSettings,
    boolean isServer,
    long initialMaxStreamsBidirectional) {
    this.connectionFactory = connectionFactory;
    this.httpSettings = httpSettings;
    this.isServer = isServer;
    this.agentType = isServer ? "SERVER" : "CLIENT";
    this.initialMaxStreamsBidirectional = initialMaxStreamsBidirectional;
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
    this.connection.onSettingsRead(ctx, settings);
    this.settingsRead = true;

    if (isServer) {
      if (addHandler != null) {
        addHandler.handle(connection);
      }
      this.connectFuture.trySuccess(connection);
    }
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
    connectFuture = new DefaultPromise<>(ctx.executor());
    connection = connectionFactory.apply(this);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    logger.debug("{} - Caught exception on channelId : {}!", agentType, ctx.channel().id(), cause);
    super.exceptionCaught(ctx, cause);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    logger.debug("{} - Received Connection event for channelId: {}, event: {}",
      agentType, ctx.channel().id(), evt.getClass().getSimpleName());

    if (evt instanceof ShutdownEvent) {
      ShutdownEvent shutdownEvt = (ShutdownEvent) evt;
      connection.shutdown(shutdownEvt.timeout(), shutdownEvt.timeUnit());
    } else if (evt instanceof QuicConnectionCloseEvent) {
//      connection.handleClosed();
    } else {
      super.userEventTriggered(ctx, evt);
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    logger.debug("{} - channelInactive() called for channelId: {}", agentType, ctx.channel().id());
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
    logger.debug("{} - onGoAwayReceived() called for streamId: {}", agentType, lastStreamId);
    connection.onGoAwayReceived(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(BufferInternal.buffer(debugData)));
  }

  public void writeHeaders(QuicStreamChannel streamChannel, VertxHttpHeaders headers, boolean end,
                           StreamPriorityBase priority, boolean checkFlush, FutureListener<Void> listener) {
    logger.debug("{} - Write header for channelId: {}, streamId: {}",
      agentType, streamChannel.id(), streamChannel.streamId());

    streamChannel.updatePriority(new QuicStreamPriority(priority.urgency(), priority.isIncremental()));
    Http3Headers http3Headers = headers.getHeaders();

/*
    if (isServer) {
      http3Headers.set(HttpHeaderNames.USER_AGENT, "Vertx Http3Server");
    } else {
      http3Headers.set(HttpHeaderNames.USER_AGENT, "Vertx Http3Client");
    }
*/
    ChannelPromise promise = listener == null ? streamChannel.voidPromise() :
      streamChannel.newPromise().addListener(listener);
    if (end && !isServer) {
      promise.unvoid().addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
    }
    streamChannel.write(new DefaultHttp3HeadersFrame(http3Headers), promise);

    if (checkFlush) {
      checkFlush();
    }

    if (end && isServer) {
      streamChannel.close();
    }
  }

  public void writeData(QuicStreamChannel streamChannel, ByteBuf chunk, boolean end, FutureListener<Void> listener) {
    logger.debug("{} - Write data for channelId: {}, streamId: {}",
      agentType, streamChannel.id(), streamChannel.streamId());
    ChannelPromise promise = listener == null ? streamChannel.voidPromise() :
      streamChannel.newPromise().addListener(listener);
    if (end && !isServer) {
      promise.unvoid().addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
    }
    streamChannel.write(new DefaultHttp3DataFrame(chunk), promise);

    checkFlush();

    if (end && isServer) {
      streamChannel.close();
    }
  }

  private void checkFlush() {
    if (!read) {
      chctx.channel().flush();
    }
  }

  static VertxHttpStreamBase getVertxStreamFromStreamChannel(ChannelHandlerContext ctx) {
    return getVertxStreamFromStreamChannel((QuicStreamChannel) ctx.channel());
  }

  static VertxHttpStreamBase getVertxStreamFromStreamChannel(QuicStreamChannel streamChannel) {
    return streamChannel.attr(VERTX_STREAM_KEY).get();
  }

  static void setVertxStreamOnStreamChannel(QuicStreamChannel streamChannel, VertxHttpStreamBase vertxStream) {
    streamChannel.attr(VERTX_STREAM_KEY).set(vertxStream);
  }

  public ChannelFuture writeSettings(Http3SettingsFrame settingsUpdate) {
    QuicStreamChannel controlStreamChannel = Http3.getLocalControlStream(chctx.channel());
    if (controlStreamChannel == null) {
      return chctx.newFailedFuture(new Http3Exception(Http3ErrorCode.H3_SETTINGS_ERROR, null));
    }

    ChannelPromise promise = controlStreamChannel.newPromise();
    promise.addListener(future -> logger.debug("{} - Write settings {} for channelId: {}, streamId: {}",
      agentType, future.isSuccess() ? "was successful" : "failed", controlStreamChannel.id(),
      controlStreamChannel.streamId()));
    controlStreamChannel.write(settingsUpdate, promise);
    return promise;
  }

  private class ControlStreamChannelHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      logger.debug("{} - channelRead() called with msg type: {}", agentType, msg.getClass().getSimpleName());

      if (msg instanceof DefaultHttp3SettingsFrame) {
        DefaultHttp3SettingsFrame http3SettingsFrame = (DefaultHttp3SettingsFrame) msg;
        onSettingsRead(ctx, http3SettingsFrame);
//        VertxHttp3ConnectionHandler.this.connection.updateHttpSettings(HttpUtils.toVertxSettings(http3SettingsFrame));
      } else if (msg instanceof DefaultHttp3GoAwayFrame) {
        super.channelRead(ctx, msg);
        DefaultHttp3GoAwayFrame http3GoAwayFrame = (DefaultHttp3GoAwayFrame) msg;
        onGoAwayReceived((int) http3GoAwayFrame.id(), -1, Unpooled.EMPTY_BUFFER);
      } else if (msg instanceof DefaultHttp3UnknownFrame) {
        DefaultHttp3UnknownFrame http3UnknownFrame = (DefaultHttp3UnknownFrame) msg;

        if (logger.isDebugEnabled()) {
          logger.debug("{} - Received unknownFrame : {}", agentType, byteBufToString(http3UnknownFrame.content()));
        }
        super.channelRead(ctx, msg);
      } else {
        super.channelRead(ctx, msg);
      }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
      logger.debug("{} - ChannelReadComplete called for channelId: {}, streamId: {}", agentType,
        ctx.channel().id(), ((QuicStreamChannel) ctx.channel()).streamId());
      if (!isServer) {
        if (settingsRead && !connectFuture.isDone()) {
          if (addHandler != null) {
            addHandler.handle(connection);
          }
          connectFuture.trySuccess(connection);
        }
      }
      super.channelReadComplete(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      logger.debug("{} - Caught exception on channelId : {}!", agentType, ctx.channel().id(), cause);
      super.exceptionCaught(ctx, cause);
    }
  }

  private class StreamChannelHandler extends Http3RequestStreamInboundHandler {
    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
      logger.debug("{} - Received Header frame for channelId: {}", agentType, ctx.channel().id());
      read = true;
      VertxHttpStreamBase vertxStream = getVertxStreamFromStreamChannel(ctx);
      connection.onHeadersRead(ctx, vertxStream, frame.headers(), false, (QuicStreamChannel) ctx.channel());
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
      logger.debug("{} - Received Data frame for channelId: {}", agentType, ctx.channel().id());
      read = true;
      VertxHttpStreamBase vertxStream = getVertxStreamFromStreamChannel(ctx);
      if (logger.isDebugEnabled()) {
        logger.debug("{} - Frame data is: {}", agentType, byteBufToString(frame.content()));
      }
      connection.onDataRead(ctx, vertxStream, frame.content(), 0, false);
    }

    @Override
    protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
      logger.debug("{} - ChannelInputClosed called for channelId: {}, streamId: {}", agentType, ctx.channel().id(),
        ((QuicStreamChannel) ctx.channel()).streamId());
      VertxHttpStreamBase vertxStream = getVertxStreamFromStreamChannel(ctx);
      vertxStream.onEnd();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
      logger.debug("{} - ChannelReadComplete called for channelId: {}, streamId: {}", agentType,
        ctx.channel().id(), ((QuicStreamChannel) ctx.channel()).streamId());
      read = false;
      super.channelReadComplete(ctx);
    }

    @Override
    protected void handleQuicException(ChannelHandlerContext ctx, QuicException exception) {
      logger.debug("{} - handleQuicException() called", agentType);
      super.handleQuicException(ctx, exception);
      Exception exception_ = exception;
      if (exception.error() == QuicError.STREAM_RESET) {
        exception_ = new StreamResetException(0, exception);
      }
      connection.onConnectionError(exception_);
      if (!settingsRead) {
        connectFuture.setFailure(exception_);
      }
      ctx.close();
    }

    @Override
    protected void handleHttp3Exception(ChannelHandlerContext ctx, Http3Exception exception) {
      logger.debug("{} - handleHttp3Exception() called", agentType);
      super.handleHttp3Exception(ctx, exception);
      connection.onConnectionError(exception);
      if (!settingsRead) {
        connectFuture.setFailure(exception);
      }
      ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      logger.debug("{} - Received event for channelId: {}, streamId: {}, event: {}",
        agentType, ctx.channel().id(), ((QuicStreamChannel) (ctx.channel())).streamId(),
        evt.getClass().getSimpleName());

      if (evt instanceof IdleStateEvent) {
        connection.handleIdle((IdleStateEvent) evt);
      } else {
        super.userEventTriggered(ctx, evt);
      }
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3UnknownFrame frame) {
      logger.debug("{} - Received Unknown frame for channelId: {}", agentType, ctx.channel().id());
      if (logger.isDebugEnabled()) {
        logger.debug("{} - Received frame http3UnknownFrame : {}", agentType, byteBufToString(frame.content()));
      }
      super.channelRead(ctx, frame);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      logger.debug("{} - Caught exception on channelId : {}!", agentType, ctx.channel().id(), cause);
      super.exceptionCaught(ctx, cause);
    }
  }

  private String byteBufToString(ByteBuf content) {
    byte[] arr = new byte[content.readableBytes()];
    content.getBytes(content.readerIndex(), arr);
    return new String(arr);
  }

  public Http3ConnectionHandler getHttp3ConnectionHandler() {
    if (isServer) {
      return new Http3ServerConnectionHandler(new StreamChannelInitializer(), new ControlStreamChannelHandler(), null
        , httpSettings, false);
    }
    return new Http3ClientConnectionHandler(new ControlStreamChannelHandler(), null, null, httpSettings, false);
  }

  private void _writePriority(QuicStreamChannel streamChannel, int urgency, boolean incremental) {
    streamChannel.updatePriority(new QuicStreamPriority(urgency, incremental));
  }

  public void writePriority(QuicStreamChannel streamChannel, int urgency, boolean incremental) {
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writePriority(streamChannel, urgency, incremental);
    } else {
      executor.execute(() -> {
        _writePriority(streamChannel, urgency, incremental);
      });
    }
  }

  public Http3SettingsFrame initialSettings() {
    return httpSettings;
  }

  public long getInitialMaxStreamsBidirectional() {
    return initialMaxStreamsBidirectional;
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
//    return getHttp3ConnectionHandler().isGoAwayReceived();
    return false;
  }

  public QuicChannel connection() {
    return (QuicChannel) chctx.channel();
  }

  public void writeReset(QuicStreamChannel streamChannel, long code) {
    ChannelPromise promise = chctx.newPromise().addListener(future -> checkFlush());
    streamChannel.shutdownOutput((int) code, promise);
  }

  private class StreamChannelInitializer extends ChannelInitializer<QuicStreamChannel> {
    private final Handler<QuicStreamChannel> onComplete;

    public StreamChannelInitializer() {
      this(null);
    }

    public StreamChannelInitializer(Handler<QuicStreamChannel> onComplete) {
      this.onComplete = onComplete;
    }

    @Override
    protected void initChannel(QuicStreamChannel streamChannel) {
      logger.debug("{} - Initialize streamChannel with channelId: {}, streamId: ", agentType, streamChannel.id(),
        streamChannel.streamId());

      streamChannel.pipeline().addLast(new StreamChannelHandler());
      if (onComplete != null) {
        onComplete.handle(streamChannel);
      }
    }
  }

  public void createStreamChannel(Handler<QuicStreamChannel> onComplete) {
    Http3.newRequestStream((QuicChannel) chctx.channel(), new StreamChannelInitializer(onComplete));
  }
}
