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

package io.vertx.core.http.impl.http2.h3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3GoAwayFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.DefaultHttp3UnknownFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3ErrorCode;
import io.netty.handler.codec.http3.Http3Exception;
import io.netty.handler.codec.http3.Http3GoAwayFrame;
import io.netty.handler.codec.http3.Http3Headers;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3HeadersValidationException;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.http3.Http3SettingsFrame;
import io.netty.handler.codec.http3.Http3UnknownFrame;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicConnectionCloseEvent;
import io.netty.handler.codec.quic.QuicException;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamPriority;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3GoAwayFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.DefaultHttp3UnknownFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3ErrorCode;
import io.netty.handler.codec.http3.Http3Exception;
import io.netty.handler.codec.http3.Http3GoAwayFrame;
import io.netty.handler.codec.http3.Http3Headers;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.http3.Http3SettingsFrame;
import io.netty.handler.codec.http3.Http3UnknownFrame;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import io.vertx.core.http.impl.http2.Http2StreamBase;
import io.vertx.core.http.impl.http2.Http3Utils;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.ShutdownEvent;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class VertxHttp3ConnectionHandler<C extends Http3ConnectionImpl> extends ChannelDuplexHandler {
  private static final Logger log = LoggerFactory.getLogger(VertxHttp3ConnectionHandler.class);

  private final Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory;
  private final GlobalTrafficShapingHandler trafficShapingHandler;
  private final ContextInternal context;
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
  private static final AttributeKey<Http2StreamBase> VERTX_STREAM_KEY =
    AttributeKey.valueOf(Http2StreamBase.class, "VERTX_CHANNEL_STREAM");

  private static final AttributeKey<ConcurrentLinkedDeque<Long>> STREAM_ID_LIST_KEY =
    AttributeKey.valueOf(ConcurrentLinkedDeque.class, "HTTP3_STREAM_ID_LIST");

  private static final AttributeKey<ConcurrentHashMap<Long, QuicStreamChannel>> STREAM_CHANNEL_MAP_KEY =
    AttributeKey.valueOf(ConcurrentHashMap.class, "HTTP3_STREAM_CHANNEL_MAP");

  public VertxHttp3ConnectionHandler(
    Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory,
    ContextInternal context,
    Http3SettingsFrame httpSettings,
    boolean isServer,
    GlobalTrafficShapingHandler trafficShapingHandler) {
    this.connectionFactory = connectionFactory;
    this.context = context;
    this.httpSettings = httpSettings;
    this.isServer = isServer;
    this.trafficShapingHandler = trafficShapingHandler;
    this.agentType = isServer ? "SERVER" : "CLIENT";
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

  void onSettingsRead(Http3SettingsFrame settings) {
    settingsRead = true;
    this.connection.onSettingsRead(settings);
    if (isServer) {
      onConnectSuccessful();
    } else {
      chctx.executor().execute(this::onConnectSuccessful);
    }
  }

  private void onConnectSuccessful() {
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
    connectFuture = new DefaultPromise<>(ctx.executor());
    connection = connectionFactory.apply(this);
    initConnectionStreams((QuicChannel) ctx.channel());
  }

  @Override
  public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    log.debug(String.format("%s - close on channelId : %s!", agentType, ctx.channel().id()));

    if (chctx.channel().isOpen() && chctx.channel().isActive()) {
      log.debug(String.format("%s - channel is active and open on close : %s!", agentType, ctx.channel().id()));
      connection.goAwayOnConnectionClose(0);  // TODO: goAway make issue for http3Proxies!
    }

    promise.addListener(future -> {
      ConcurrentLinkedDeque<Long> streamIdList = getStreamIdList((QuicChannel) ctx.channel());
      streamIdList.forEach(id -> unregisterStream(getStreamChannel(id)));
    });

    super.close(ctx, promise);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    log.debug(String.format("%s - Caught exception on channelId : %s!", agentType, ctx.channel().id(), cause));
    super.exceptionCaught(ctx, cause);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    log.debug(String.format("%s - channelInactive() called for channelId: %s", agentType, ctx.channel().id()));
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
    log.debug(String.format("%s - Received event for channelId: %s, event: %s",
      agentType, ctx.channel().id(), evt.getClass().getSimpleName()));
    try {
      super.userEventTriggered(ctx, evt);
    } finally {
      if (evt instanceof ShutdownEvent) {
        ShutdownEvent shutdownEvt = (ShutdownEvent) evt;
        connection.shutdown(shutdownEvt.timeout(), shutdownEvt.timeUnit());
      } else if (evt instanceof IdleStateEvent) {
        connection.handleIdle((IdleStateEvent) evt);
      } else if (evt instanceof QuicConnectionCloseEvent) {
        connection.onGoAwayReceived(new GoAway());
      }
    }
  }

  void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    connection.onGoAwaySent(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(BufferInternal.buffer(debugData)));
  }

  void onGoAwayReceived(Http3GoAwayFrame http3GoAwayFrame) {
    int lastStreamId = (int) http3GoAwayFrame.id();
    log.debug(String.format("%s - onGoAwayReceived() called for streamId: %s", agentType, lastStreamId));
    connection.onGoAwayReceived(new GoAway().setErrorCode(0).setLastStreamId(lastStreamId).setDebugData(BufferInternal.buffer(Unpooled.EMPTY_BUFFER)));
  }

  public void writeHeaders(QuicStreamChannel streamChannel, Http2HeadersMultiMap headers, boolean end,
                           StreamPriority priority, boolean checkFlush, FutureListener<Void> listener) {
    log.debug(String.format("%s - Write header for channelId: %s, streamId: %s",
      agentType, streamChannel.id(), streamChannel.streamId()));

    streamChannel.updatePriority(new QuicStreamPriority(priority.getHttp3Urgency(), priority.isHttp3Incremental()));
    Http3Headers http3Headers = (Http3Headers) headers.unwrap();

    ChannelPromise promise = streamChannel.newPromise();
    if (listener != null) {
      promise.addListener(listener);
    }

    if (end) {
      promise.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
    }
    streamChannel.write(new DefaultHttp3HeadersFrame(http3Headers), promise);

    if (checkFlush) {
      checkFlush();
    }
  }

  public void writeData(QuicStreamChannel streamChannel, ByteBuf chunk, boolean end, FutureListener<Void> listener) {
    log.debug(String.format("%s - Write data for channelId: %s, streamId: %s",
      agentType, streamChannel.id(), streamChannel.streamId()));
    ChannelPromise promise = streamChannel.newPromise();
    if (listener != null) {
      promise.addListener(listener);
    }

    if (end) {
      promise.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
    }
    streamChannel.write(new DefaultHttp3DataFrame(chunk), promise);

    checkFlush();
  }

  private void checkFlush() {
    if (!read) {
      chctx.channel().flush();
    }
  }

  public static void initConnectionStreams(QuicChannel quicChannel) {
    if (getStreamChannelMap(quicChannel) == null) {
      quicChannel.attr(STREAM_CHANNEL_MAP_KEY).set(new ConcurrentHashMap<>());
    }
    if (getStreamIdList(quicChannel) == null) {
      quicChannel.attr(STREAM_ID_LIST_KEY).set(new ConcurrentLinkedDeque<>());
    }
  }

  private static void registerStream(QuicStreamChannel streamChannel) {
    QuicChannel parentConnection = streamChannel.parent();
    long streamId = streamChannel.streamId();

    getStreamChannelMap(parentConnection).put(streamId, streamChannel);
    getStreamIdList(parentConnection).addLast(streamId);
  }

  public static void unregisterStream(QuicStreamChannel streamChannel) {
    QuicChannel parentConnection = streamChannel.parent();
    long streamId = streamChannel.streamId();

    getStreamChannelMap(parentConnection).remove(streamId);
    getStreamIdList(parentConnection).remove(streamId);
  }

  public static ConcurrentHashMap<Long, QuicStreamChannel> getStreamChannelMap(QuicChannel quicChannel) {
    return quicChannel.attr(STREAM_CHANNEL_MAP_KEY).get();
  }

  static Http2StreamBase getVertxStreamFromStreamChannel(ChannelHandlerContext ctx) {
    return getVertxStreamFromStreamChannel((QuicStreamChannel) ctx.channel());
  }

  static Http2StreamBase getVertxStreamFromStreamChannel(QuicStreamChannel streamChannel) {
    return streamChannel.attr(VERTX_STREAM_KEY).get();
  }

  static void setVertxStreamOnStreamChannel(QuicStreamChannel streamChannel, Http2StreamBase vertxStream) {
    streamChannel.attr(VERTX_STREAM_KEY).set(vertxStream);
  }

  public long getLastStreamId() {
    ConcurrentLinkedDeque<Long> streamIds = getStreamIdList((QuicChannel) chctx.channel());
    if (streamIds != null) {
      Long lastStreamId = streamIds.peekLast();
      if (lastStreamId != null) {
        return lastStreamId;
      }
    }
    return -1;
  }

  private static ConcurrentLinkedDeque<Long> getStreamIdList(QuicChannel quicChannel) {
    return quicChannel.attr(STREAM_ID_LIST_KEY).get();
  }

  public QuicStreamChannel getStreamChannel(long streamId) {
    return getStreamChannelMap((QuicChannel) chctx.channel()).get(streamId);
  }

  public List<QuicStreamChannel> getActiveQuicStreamChannels() {
    return getStreamChannelMap((QuicChannel) chctx.channel()).values().stream().filter(Channel::isActive).collect(Collectors.toList());
  }

  void writeFrame(QuicStreamChannel streamChannel, byte type, short flags, ByteBuf payload, FutureListener<Void> listener) {
    ChannelPromise promise = listener == null ? streamChannel.voidPromise() : streamChannel.newPromise().addListener(listener);
    streamChannel.write(new DefaultHttp3UnknownFrame(type, payload), promise);
    checkFlush();
  }

  public void writeReset(QuicStreamChannel streamChannel, long code, FutureListener<Void> listener) {
    ChannelPromise promise = streamChannel.newPromise().addListener(future -> checkFlush());
    if (listener != null) {
      promise.addListener(listener);
    }
    streamChannel.shutdownOutput((int) code, promise);
  }

  io.vertx.core.Future<Object> writeGoAway(long errorCode, long lastStreamId, ByteBuf debugData) {
    PromiseInternal<Object> promise = context.promise();
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writeGoAway(errorCode, lastStreamId, debugData, promise);
    } else {
      executor.execute(() -> {
        _writeGoAway(errorCode, lastStreamId, debugData, promise);
      });
    }
    return promise.future();
  }

  private void _writeGoAway(long errorCode, long lastStreamId, ByteBuf debugData, PromiseInternal<Object> promise) {
    QuicStreamChannel controlStreamChannel = Http3.getLocalControlStream(chctx.channel());
    assert controlStreamChannel != null;

    log.debug(String.format(
      "%s - _writeGoAway called with lastStreamId=%s, controlStreamChannel (streamId=%s, active=%s, open=%s)",
      agentType, lastStreamId, controlStreamChannel.streamId(), controlStreamChannel.isActive(), controlStreamChannel.isOpen()
    ));

    ChannelPromise promise0 = controlStreamChannel.newPromise();
    promise0.addListener(future -> {
      log.debug(String.format("%s - Writing goAway %s for channelId: %s, streamId: %s",
        agentType, future.isSuccess() ? "succeeded" : "failed", controlStreamChannel.id(),
        controlStreamChannel.streamId()));
      promise.tryComplete();
    });

    Http3GoAwayFrame goAwayFrame = new DefaultHttp3GoAwayFrame(lastStreamId);

    onGoAwaySent(Math.toIntExact(lastStreamId), errorCode, debugData);
    controlStreamChannel.write(goAwayFrame, promise0);
    checkFlush();
  }

  ChannelFuture writeSettings(Http3SettingsFrame settingsUpdate) {
    ChannelPromise promise = chctx.newPromise();
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writeSettings(settingsUpdate, promise);
    } else {
      executor.execute(() -> {
        _writeSettings(settingsUpdate, promise);
      });
    }
    return promise;
  }

  private void _writeSettings(Http3SettingsFrame settingsUpdate, ChannelPromise promise) {
    QuicStreamChannel controlStreamChannel = Http3.getLocalControlStream(chctx.channel());
    if (controlStreamChannel == null) {
      promise.tryFailure(new Http3Exception(Http3ErrorCode.H3_SETTINGS_ERROR, null));
      return;
    }

    promise.addListener(future -> log.debug(String.format("%s - Writing settings %s for channelId: %s, streamId: %s",
      agentType, future.isSuccess() ? "succeeded" : "failed", controlStreamChannel.id(),
      controlStreamChannel.streamId())));
    controlStreamChannel.write(settingsUpdate, promise);

    checkFlush();
  }

  private class StreamChannelHandler extends Http3RequestStreamInboundHandler {
    private boolean headerReceived = false;

    //TODO: commented because connection will be closed on file transfer.
    private int channelWritabilityChangedCounter = 0;

    @Override
    public synchronized void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
      log.debug(String.format("%s - ChannelWritabilityChanged called for channelId: %s, streamId: %s",
        agentType, ctx.channel().id(), ((QuicStreamChannel) ctx.channel()).streamId()));

      connection.onStreamWritabilityChanged(getVertxStreamFromStreamChannel(ctx));
      super.channelWritabilityChanged(ctx);
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
      log.debug(String.format("%s - Received Header frame for channelId: %s", agentType, ctx.channel().id()));
      read = true;
      headerReceived = true;
      Http2StreamBase vertxStream = getVertxStreamFromStreamChannel(ctx);
      connection.onHeadersRead(ctx, vertxStream, frame.headers(), false, (QuicStreamChannel) ctx.channel());
      ReferenceCountUtil.release(frame);
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
      log.debug(String.format("%s - Received Data frame for channelId: %s", agentType, ctx.channel().id()));
      read = true;
      headerReceived = false;
      if (log.isDebugEnabled()) {
        log.debug(String.format("%s - Frame data is: %s", agentType, byteBufToString(frame.content())));
      }
      connection.onDataRead(ctx, getVertxStreamFromStreamChannel(ctx), frame.content(), 0, false);
      ReferenceCountUtil.release(frame);
    }

    @Override
    protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
      log.debug(String.format("%s - ChannelInputClosed called for channelId: %s, streamId: %s", agentType, ctx.channel().id(),
        ((QuicStreamChannel) ctx.channel()).streamId()));
      Http2StreamBase vertxStream = getVertxStreamFromStreamChannel(ctx);
      if (vertxStream != null) {
        vertxStream.onTrailers();
      }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
      log.debug(String.format("%s - ChannelReadComplete called for channelId: %s, streamId: %s", agentType,
        ctx.channel().id(), ((QuicStreamChannel) ctx.channel()).streamId()));
      read = false;
      super.channelReadComplete(ctx);
    }

    @Override
    protected void handleQuicException(ChannelHandlerContext ctx, QuicException exception) {
      Exception exception_ = exception;
      if ("STREAM_RESET".equals(exception.getMessage())) {

        Http2StreamBase vertxStream = getVertxStreamFromStreamChannel(ctx);
        if (vertxStream != null) {
          vertxStream.onReset(0);
        } else {
          exception_ = new StreamResetException(0, exception);
        }
      } else {
        log.error(String.format("%s - handleQuicException() called", agentType), exception);
      }
      connection.onConnectionError(exception_);
      if (!settingsRead) {
        connectFuture.setFailure(exception_);
      }
      ctx.close();
    }

    @Override
    protected void handleHttp3Exception(ChannelHandlerContext ctx, Http3Exception exception) {
      log.error(String.format("%s - handleHttp3Exception() called", agentType), exception);
      connection.onConnectionError(exception);
      if (!settingsRead) {
        connectFuture.setFailure(exception);
      }
      ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      log.debug(String.format("%s - Received event for channelId: %s, streamId: %s, event: %s",
        agentType, ctx.channel().id(), ((QuicStreamChannel) (ctx.channel())).streamId(),
        evt.getClass().getSimpleName()));

      if (evt == ChannelInputShutdownEvent.INSTANCE) {
        Http2StreamBase vertxStream = getVertxStreamFromStreamChannel(ctx);
        if (vertxStream != null && vertxStream.isReset()) {
          connection.onStreamClosed(((QuicStreamChannel) (ctx.channel())));
          return;
        }
      }

      try {
        super.userEventTriggered(ctx, evt);
      } finally {
        if (evt instanceof IdleStateEvent) {
          connection.handleIdle((IdleStateEvent) evt);
        }
      }
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3UnknownFrame frame) {
      log.debug(String.format("%s - Received Unknown frame for channelId: %s", agentType, ctx.channel().id()));
      log.debug(String.format("%s - Received Unknown frame for channelId: %s", agentType, ctx.channel().id()));
      if (log.isDebugEnabled()) {
        log.debug(String.format("%s - Received frame http3UnknownFrame : %s", agentType, byteBufToString(frame.content())));
      }

      Http2StreamBase vertxStream = getVertxStreamFromStreamChannel(ctx);
      connection.onUnknownFrame(ctx, (byte) frame.type(), vertxStream, frame.content());
      super.channelRead(ctx, frame);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      if (cause instanceof QuicException) {
        log.error(String.format("%s - Caught QuicException on channelId : %s!", agentType, ctx.channel().id()));
        super.exceptionCaught(ctx, cause);
        return;
      }
      if (cause instanceof Http3HeadersValidationException) {
        log.error(String.format("%s - Caught Http3HeadersValidationException on channelId : %s!", agentType, ctx.channel().id()));
        return;
      }
      log.error(String.format("%s - Caught exception on channelId : %s!", agentType, ctx.channel().id()), cause);
      super.exceptionCaught(ctx, cause);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      registerStream((QuicStreamChannel) ctx.channel());
      super.channelActive(ctx);
    }
  }

  private String byteBufToString(ByteBuf content) {
    byte[] arr = new byte[content.readableBytes()];
    content.getBytes(content.readerIndex(), arr);
    return new String(arr);
  }

  public Http3ConnectionHandler getHttp3ConnectionHandler() {
    //TODO: implement settings
    if (isServer) {
      return Http3Utils
        .newServerConnectionHandlerBuilder()
        .requestStreamHandler(streamChannel -> {
          streamChannel.closeFuture().addListener(ignored -> handleOnStreamChannelClosed(streamChannel));
          streamChannel.pipeline().addLast(new StreamChannelHandler());
          if (trafficShapingHandler != null) {
            streamChannel.pipeline().addFirst("streamTrafficShaping", trafficShapingHandler);
          }
        })
        .agentType(this.agentType)
        .http3GoAwayFrameHandler(this::onGoAwayReceived)
        .http3SettingsFrameHandler(this::onSettingsRead)
        .build();
    }
    return Http3Utils
      .newClientConnectionHandlerBuilder()
      .agentType(this.agentType)
      .http3GoAwayFrameHandler(this::onGoAwayReceived)
      .http3SettingsFrameHandler(this::onSettingsRead)
      .build();
  }

  private void _writePriority(QuicStreamChannel streamChannel, StreamPriority priority) {
    streamChannel.updatePriority(new QuicStreamPriority(priority.getHttp3Urgency(), priority.isHttp3Incremental()));
  }

  public void writePriority(QuicStreamChannel streamChannel, StreamPriority priority) {
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writePriority(streamChannel, priority);
    } else {
      executor.execute(() -> {
        _writePriority(streamChannel, priority);
      });
    }
  }

  public Http3SettingsFrame initialSettings() {
    return httpSettings;
  }

  public io.vertx.core.Future<Object> gracefulShutdownTimeoutMillis(long timeout) {
    return writeGoAway(1, getLastStreamId() > 0 ? getLastStreamId() : 0, Unpooled.EMPTY_BUFFER);
  }

  public boolean goAwayReceived() {
    return chctx.pipeline().get(Http3ConnectionHandler.class).isGoAwayReceived();
  }

  public QuicChannel connection() {
    return (QuicChannel) chctx.channel();
  }


  public io.vertx.core.Future<QuicStreamChannel> createStreamChannel() {
    return Http3Utils.newRequestStream((QuicChannel) chctx.channel(), streamChannel -> {
      streamChannel.closeFuture().addListener(ignored -> handleOnStreamChannelClosed(streamChannel));
      streamChannel.pipeline().addLast(new StreamChannelHandler());
    });
  }

  private void handleOnStreamChannelClosed(QuicStreamChannel streamChannel) {
    log.debug(String.format("%s - called handleOnStreamChannelClosed for streamChannel with id: %s, streamId: %s",
      agentType, streamChannel.id(), streamChannel.streamId()));
    connection.onStreamClosed(streamChannel);
  }

  public String getAgentType() {
    return agentType;
  }
}
