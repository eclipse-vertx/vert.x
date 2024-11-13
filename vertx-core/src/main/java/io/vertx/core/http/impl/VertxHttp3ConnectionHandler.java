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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
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
import io.vertx.core.http.HttpSettings;
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
  private final HttpSettings httpSettings;
  private final boolean isServer;

  private boolean read;
  private static final AttributeKey<VertxHttpStreamBase> QUIC_CHANNEL_STREAM_KEY =
    AttributeKey.valueOf(VertxHttpStreamBase.class, "QUIC_CHANNEL_STREAM");

  public VertxHttp3ConnectionHandler(
    Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory,
    ContextInternal context,
    HttpSettings httpSettings,
    boolean isServer,
    long initialMaxStreamsBidirectional) {
    this.connectionFactory = connectionFactory;
    this.httpSettings = httpSettings;
    this.isServer = isServer;
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

  private void onSettingsRead(ChannelHandlerContext ctx, HttpSettings settings) {
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
    connection = connectionFactory.apply(VertxHttp3ConnectionHandler.this);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    logger.debug("VertxHttp3ConnectionHandler caught exception!", cause);
    super.exceptionCaught(ctx, cause);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    if (!isServer) {
      if (settingsRead && isFirstSettingsRead) {
        if (addHandler != null) {
          addHandler.handle(connection);
        }
        VertxHttp3ConnectionHandler.this.connectFuture.trySuccess(connection);
        isFirstSettingsRead = false;
      }
    }
    super.channelReadComplete(ctx);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof ShutdownEvent) {
      ShutdownEvent shutdownEvt = (ShutdownEvent) evt;
      logger.debug("Received QuicChannel event ShutdownEvent");
      connection.shutdown(shutdownEvt.timeout(), shutdownEvt.timeUnit());
    } else if (evt instanceof QuicConnectionCloseEvent) {
      QuicConnectionCloseEvent connectionCloseEvt = (QuicConnectionCloseEvent) evt;
      logger.debug("Received QuicChannel event QuicConnectionCloseEvent, error: {}, isApplicationClose: {}, isTlsError: {}, "
        , connectionCloseEvt.error(), connectionCloseEvt.isApplicationClose(), connectionCloseEvt.isTlsError());
      connection.handleClosed();
    }
    super.userEventTriggered(ctx, evt);
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
    connection.onGoAwayReceived(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(BufferInternal.buffer(debugData)));
  }

  public void writeHeaders(QuicStreamChannel stream, VertxHttpHeaders headers, boolean end, StreamPriorityBase priority,
                           boolean checkFlush, FutureListener<Void> listener) {
    logger.debug("WriteHeaders called");
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

  public void writeData(QuicStreamChannel stream, ByteBuf chunk, boolean end, FutureListener<Void> listener) {
    ChannelPromise promise = listener == null ? stream.voidPromise() : stream.newPromise().addListener(listener);
    if (end) {
      promise.unvoid().addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
    }
    stream.write(new DefaultHttp3DataFrame(chunk), promise);

    checkFlush();
  }

  private void checkFlush() {
    if (!read) {
      chctx.channel().flush();
    }
  }

  static VertxHttpStreamBase getStreamOfQuicStreamChannel(ChannelHandlerContext ctx) {
    return getStreamOfQuicStreamChannel((QuicStreamChannel) ctx.channel());
  }

  static VertxHttpStreamBase getStreamOfQuicStreamChannel(QuicStreamChannel quicStreamChannel) {
    return quicStreamChannel.attr(QUIC_CHANNEL_STREAM_KEY).get();
  }

  static void setStreamOfQuicStreamChannel(QuicStreamChannel quicStreamChannel, VertxHttpStreamBase vertxHttpStream) {
    quicStreamChannel.attr(QUIC_CHANNEL_STREAM_KEY).set(vertxHttpStream);
  }

  private ChannelInboundHandlerAdapter createInboundControlStreamHandler() {
    return new ChannelInboundHandlerAdapter() {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof DefaultHttp3SettingsFrame) {
          DefaultHttp3SettingsFrame http3SettingsFrame = (DefaultHttp3SettingsFrame) msg;
          logger.debug("Received frame http3SettingsFrame");
          onSettingsRead(ctx, new HttpSettings(http3SettingsFrame));
          VertxHttp3ConnectionHandler.this.connection.updateHttpSettings(new HttpSettings(http3SettingsFrame));
//          Thread.sleep(70000);
          if (!isServer) {
            ctx.close();
          }
          super.channelRead(ctx, msg);
        } else if (msg instanceof DefaultHttp3GoAwayFrame) {
          super.channelRead(ctx, msg);
          DefaultHttp3GoAwayFrame http3GoAwayFrame = (DefaultHttp3GoAwayFrame) msg;
          logger.debug("Received frame http3GoAwayFrame.");
          onGoAwayReceived((int) http3GoAwayFrame.id(), -1, Unpooled.EMPTY_BUFFER);
        } else if (msg instanceof DefaultHttp3UnknownFrame) {
          DefaultHttp3UnknownFrame http3UnknownFrame = (DefaultHttp3UnknownFrame) msg;

          if (logger.isDebugEnabled()) {
            logger.debug("Received frame http3UnknownFrame : {}", byteBufToString(http3UnknownFrame.content()));
          }
          super.channelRead(ctx, msg);
        } else {
          logger.debug("Received unhandled frame type: {}", msg.getClass());
          super.channelRead(ctx, msg);
        }
      }
    };
  }

  public Http3RequestStreamInboundHandler createHttp3RequestStreamInboundHandler() {
    return new Http3RequestStreamInboundHandler() {
      @Override
      protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
        logger.debug("Received Header frame for channelId: {}", ctx.channel().id());
        read = true;
        VertxHttpStreamBase stream = getStreamOfQuicStreamChannel(ctx);
        connection.onHeadersRead(ctx, stream, frame.headers(), false, (QuicStreamChannel) ctx.channel());
      }

      @Override
      protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
        logger.debug("Received Data frame for channelId: {}", ctx.channel().id());
        read = true;
        VertxHttpStreamBase stream = getStreamOfQuicStreamChannel(ctx);
        if (logger.isDebugEnabled()) {
          logger.debug("Frame data is: {}", byteBufToString(frame.content()));
        }
        connection.onDataRead(ctx, stream, frame.content(), 0, false);
      }

      @Override
      protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
        logger.debug("ChannelInputClosed called for channelId: {}, streamId: {}", ctx.channel().id(),
          ((QuicStreamChannel)ctx.channel()).streamId());
        VertxHttpStreamBase stream = getStreamOfQuicStreamChannel(ctx);
        if (stream.isHeaderOnly() && !isServer) {
          connection.onHeadersRead(ctx, stream, new DefaultHttp3Headers(), true, (QuicStreamChannel) ctx.channel());
        } else {
          connection.onDataRead(ctx, stream, Unpooled.buffer(), 0, true);
        }
        connection.onStreamClosed(getStreamOfQuicStreamChannel(ctx));
      }

      @Override
      public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.debug("ChannelReadComplete called for channelId: {}, streamId: {}", ctx.channel().id(),
          ((QuicStreamChannel)ctx.channel()).streamId());
        read = false;
        super.channelReadComplete(ctx);
      }

      @Override
      protected void handleQuicException(ChannelHandlerContext ctx, QuicException exception) {
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
        super.handleHttp3Exception(ctx, exception);
        connection.onConnectionError(exception);
        if (!settingsRead) {
          connectFuture.setFailure(exception);
        }
        ctx.close();
      }

      @Override
      public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        logger.debug("userEventTriggered called for channelId: {}", ctx.channel().id());
        if (evt instanceof SslHandshakeCompletionEvent) {
          SslHandshakeCompletionEvent completion = (SslHandshakeCompletionEvent) evt;
          if (!completion.isSuccess()) {
            connectFuture.tryFailure(completion.cause());
          }
        } else if (evt instanceof IdleStateEvent) {
          connection.handleIdle((IdleStateEvent) evt);
        } else if (evt instanceof QuicDatagramExtensionEvent) {
          logger.debug("Received QuicStreamChannel event QuicDatagramExtensionEvent");
          ctx.fireUserEventTriggered(evt);
        } else if (evt instanceof QuicStreamLimitChangedEvent) {
          logger.debug("Received QuicStreamChannel event QuicStreamLimitChangedEvent");
          ctx.fireUserEventTriggered(evt);
        } else if (evt instanceof QuicConnectionCloseEvent) {
          logger.debug("Received QuicStreamChannel event QuicConnectionCloseEvent");
          ctx.fireUserEventTriggered(evt);
        } else if (evt == ChannelInputShutdownEvent.INSTANCE) {
          logger.debug("Received QuicStreamChannel event ChannelInputShutdownEvent.");
          super.userEventTriggered(ctx, evt);
        } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
          logger.debug("Received QuicStreamChannel event ChannelInputShutdownReadComplete");
          ctx.fireUserEventTriggered(evt);
        } else if (evt instanceof ShutdownEvent) {
          logger.debug("Received QuicStreamChannel event ShutdownEvent");
          ctx.fireUserEventTriggered(evt);
        } else {
          logger.debug("Received QuicStreamChannel unhandled event: {}", evt);
          ctx.fireUserEventTriggered(evt);
        }
      }

      @Override
      protected void channelRead(ChannelHandlerContext ctx, Http3UnknownFrame frame) {
        logger.debug("Received Unknown frame for channelId: {}", ctx.channel().id());
        if (logger.isDebugEnabled()) {
          logger.debug("Received frame http3UnknownFrame : {}", byteBufToString(frame.content()));
        }
        super.channelRead(ctx, frame);
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.debug("Http3RequestStreamInboundHandler caught exception on channelId : {}!",
          ctx.channel().id(), cause);
        super.exceptionCaught(ctx, cause);
      }
    };
  }

  private String byteBufToString(ByteBuf content) {
    byte[] arr = new byte[content.readableBytes()];
    content.getBytes(content.readerIndex(), arr);
    return new String(arr);
  }

  public Http3ConnectionHandler getHttp3ConnectionHandler() {
    return isServer ? createHttp3ServerConnectionHandler() : createHttp3ClientConnectionHandler();
  }

  private Http3ServerConnectionHandler createHttp3ServerConnectionHandler() {
    return new Http3ServerConnectionHandler(new ChannelInitializer<QuicStreamChannel>() {
      @Override
      protected void initChannel(QuicStreamChannel ch) throws Exception {
        logger.debug("Init QuicStreamChannel...");
        ch.pipeline().addLast(createHttp3RequestStreamInboundHandler());
      }
    }, createInboundControlStreamHandler(), null, null, false);
    //TODO: correct the settings and streamHandlerIssue:
  }

  private Http3ClientConnectionHandler createHttp3ClientConnectionHandler() {
    return new Http3ClientConnectionHandler(createInboundControlStreamHandler(), null, null,
      null, false);
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

  private boolean isFirstSettingsRead = true;  //TODO: remove me if it is possible

  public HttpSettings initialSettings() {
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

  public void writeReset(QuicStreamChannel quicStreamChannel, long code) {
    ChannelPromise promise = chctx.newPromise().addListener(future -> checkFlush());
    quicStreamChannel.shutdownOutput((int) code, promise);
  }

  public void createHttp3RequestStream(Handler<QuicStreamChannel> onComplete) {
    Http3.newRequestStream((QuicChannel) chctx.channel(),
      new Http3RequestStreamInitializer() {
        @Override
        protected void initRequestStream(QuicStreamChannel ch) {
          ch.pipeline().addLast(createHttp3RequestStreamInboundHandler());
          onComplete.handle(ch);
        }
      });
  }
}
