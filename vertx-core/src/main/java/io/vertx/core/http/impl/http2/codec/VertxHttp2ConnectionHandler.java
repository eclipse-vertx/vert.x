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

package io.vertx.core.http.impl.http2.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.GoAway;
import io.vertx.core.net.impl.ShutdownEvent;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxHttp2ConnectionHandler<C extends Http2ConnectionImpl> extends Http2ConnectionHandler implements Http2FrameListener, io.netty.handler.codec.http2.Http2Connection.Listener {

  private final Function<VertxHttp2ConnectionHandler<C>, C> connectionFactory;
  private C connection;
  private ChannelHandlerContext chctx;
  private Promise<C> connectFuture;
  private boolean settingsRead;
  private Handler<C> addHandler;
  private Handler<C> removeHandler;
  private final boolean useDecompressor;
  private final Http2Settings initialSettings;
  public boolean upgraded;

  public VertxHttp2ConnectionHandler(
      Function<VertxHttp2ConnectionHandler<C>, C> connectionFactory,
      boolean useDecompressor,
      Http2ConnectionDecoder decoder,
      Http2ConnectionEncoder encoder,
      Http2Settings initialSettings) {
    super(decoder, encoder, initialSettings);
    this.connectionFactory = connectionFactory;
    this.useDecompressor = useDecompressor;
    this.initialSettings = initialSettings;
    encoder().flowController().listener(s -> {
      if (connection != null) {
        connection.onStreamWritabilityChanged(s);
      }
    });
    connection().addListener(this);
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

  public Http2Settings initialSettings() {
    return initialSettings;
  }

  /**
   * Set an handler to be called when the connection is set on this handler.
   *
   * @param handler the handler to be notified
   * @return this
   */
  public VertxHttp2ConnectionHandler<C> addHandler(Handler<C> handler) {
    this.addHandler = handler;
    return this;
  }

  /**
   * Set an handler to be called when the connection is unset from this handler.
   *
   * @param handler the handler to be notified
   * @return this
   */
  public VertxHttp2ConnectionHandler<C> removeHandler(Handler<C> handler) {
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
    Http2Exception http2Cause = Http2CodecUtil.getEmbeddedHttp2Exception(cause);
    if (http2Cause != null) {
      // Super will only handle Http2Exception otherwise it will be reach the end of the pipeline
      super.exceptionCaught(ctx, http2Cause);
    }
    ctx.close();
  }

  public void serverUpgrade(
    ChannelHandlerContext ctx,
    Http2Settings serverUpgradeSettings) throws Exception {
    upgraded = true;
    onHttpServerUpgrade(serverUpgradeSettings);
    onSettingsRead(ctx, serverUpgradeSettings);
  }

  public void clientUpgrade(ChannelHandlerContext ctx) throws Exception {
    upgraded = true;
    onHttpClientUpgrade();
    // super call writes the connection preface
    // we need to flush to send it
    // this is called only on the client
    checkFlush();
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
  protected void onConnectionError(ChannelHandlerContext ctx, boolean outbound, Throwable cause, Http2Exception http2Ex) {
    connection.onConnectionError(cause);
    if (!settingsRead) {
      connectFuture.setFailure(http2Ex);
    }
    // Default behavior send go away
    super.onConnectionError(ctx, outbound, cause, http2Ex);
  }

  @Override
  protected void onStreamError(ChannelHandlerContext ctx, boolean outbound, Throwable cause, Http2Exception.StreamException http2Ex) {
    connection.onStreamError(http2Ex.streamId(), http2Ex);
    // Default behavior reset stream
    super.onStreamError(ctx, outbound, cause, http2Ex);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    try {
      super.userEventTriggered(ctx, evt);
    } finally {
      if (evt instanceof IdleStateEvent) {
        connection.handleIdle((IdleStateEvent) evt);
      } else if (evt instanceof ShutdownEvent) {
        ShutdownEvent shutdownEvt = (ShutdownEvent) evt;
        connection.shutdown(shutdownEvt.timeout(), shutdownEvt.timeUnit());
      }
    }
  }

  //

  @Override
  public void onStreamClosed(Http2Stream stream) {
    connection.onStreamClosed(stream);
  }

  @Override
  public void onStreamAdded(Http2Stream stream) {
  }

  @Override
  public void onStreamActive(Http2Stream stream) {
  }

  @Override
  public void onStreamHalfClosed(Http2Stream stream) {
  }

  @Override
  public void onStreamRemoved(Http2Stream stream) {
  }

  @Override
  public void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    connection.onGoAwaySent(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(BufferInternal.buffer(debugData)));
  }

  @Override
  public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    connection.onGoAwayReceived(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(BufferInternal.buffer(debugData)));
  }

  //

  void writeHeaders(Http2Stream stream, VertxHttpHeaders headers, boolean end, int streamDependency, short weight, boolean exclusive, boolean checkFlush, FutureListener<Void> listener) {
    ChannelPromise promise = listener == null ? chctx.voidPromise() : chctx.newPromise().addListener(listener);
    encoder().writeHeaders(chctx, stream.id(), headers.getHeaders(), streamDependency, weight, exclusive, 0, end, promise);
    if (checkFlush) {
      checkFlush();
    }
  }

  void writeData(Http2Stream stream, ByteBuf chunk, boolean end, FutureListener<Void> listener) {
    ChannelPromise promise = listener == null ? chctx.voidPromise() : chctx.newPromise().addListener(listener);
    Http2ConnectionEncoder encoder = encoder();
    encoder.writeData(chctx, stream.id(), chunk, 0, end, promise);
    Http2RemoteFlowController controller = encoder.flowController();
    if (!controller.isWritable(stream) || end) {
      try {
        encoder.flowController().writePendingBytes();
      } catch (Http2Exception e) {
        onError(chctx, true, e);
      }
    }
    checkFlush();
  }

  private void checkFlush() {
    if (!read) {
      chctx.channel().flush();
    }
  }

  ChannelFuture writePing(long data) {
    ChannelPromise promise = chctx.newPromise();
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writePing(data, promise);
    } else {
      executor.execute(() -> {
        _writePing(data, promise);
      });
    }
    return promise;
  }

  private void _writePing(long data, ChannelPromise promise) {
    encoder().writePing(chctx, false, data, promise);
    checkFlush();
  }

  /**
   * Consume {@code numBytes} for {@code stream}  in the flow controller, this must be called from event loop.
   */
  void consume(Http2Stream stream, int numBytes) {
    try {
      boolean windowUpdateSent = decoder().flowController().consumeBytes(stream, numBytes);
      if (windowUpdateSent) {
        checkFlush();
      }
    } catch (Http2Exception e) {
      onError(chctx, true, e);
    }
  }

  void writeFrame(Http2Stream stream, byte type, short flags, ByteBuf payload, FutureListener<Void> listener) {
    ChannelPromise promise = listener == null ? chctx.voidPromise() : chctx.newPromise().addListener(listener);
    encoder().writeFrame(chctx, type, stream.id(), new Http2Flags(flags), payload, promise);
    checkFlush();
  }

  void writeReset(int streamId, long code, FutureListener<Void> listener) {
    ChannelPromise promise = listener == null ? chctx.voidPromise() : chctx.newPromise().addListener(listener);
    encoder().writeRstStream(chctx, streamId, code, promise);
    checkFlush();
  }

  void writeGoAway(long errorCode, int lastStreamId, ByteBuf debugData) {
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writeGoAway(errorCode, lastStreamId, debugData);
    } else {
      executor.execute(() -> {
        _writeGoAway(errorCode, lastStreamId, debugData);
      });
    }
  }

  private void _writeGoAway(long errorCode, int lastStreamId, ByteBuf debugData) {
    encoder().writeGoAway(chctx, lastStreamId, errorCode, debugData, chctx.newPromise());
    checkFlush();
  }

  ChannelFuture writeSettings(Http2Settings settingsUpdate) {
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

  private void _writeSettings(Http2Settings settingsUpdate, ChannelPromise promise) {
    encoder().writeSettings(chctx, settingsUpdate, promise);
    checkFlush();
  }

  io.netty.util.concurrent.Future<Integer> writePushPromise(int streamId, Http2Headers headers) {
    int promisedStreamId = connection().local().incrementAndGetNextStreamId();
    DefaultPromise<Integer> future = new DefaultPromise<>(chctx.executor());
    ChannelPromise promise = chctx.newPromise();
    promise.addListener(fut -> {
      if (fut.isSuccess()) {
        future.setSuccess(promisedStreamId);
      } else {
        future.setFailure(fut.cause());
      }
    });
    _writePushPromise(streamId, promisedStreamId, headers, promise);
    checkFlush();
    return future;
  }

  int maxConcurrentStreams() {
    return connection().local().maxActiveStreams();
  }

  private void _writePushPromise(int streamId, int promisedStreamId, Http2Headers headers, ChannelPromise promise) {
    encoder().writePushPromise(chctx, streamId, promisedStreamId, headers, 0, promise);
  }

  // Http2FrameListener

  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
    connection.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream);
  }

  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
    if (useDecompressor) {
      decoder().frameListener(new DelegatingDecompressorFrameListener(decoder().connection(), connection));
    } else {
      decoder().frameListener(connection);
    }
    connection.onSettingsRead(ctx, settings);
    settingsRead = true;
    if (addHandler != null) {
      addHandler.handle(connection);
    }
    connectFuture.setSuccess(connection);
  }

  private boolean read;

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    read = true;
    if (msg instanceof Http2StreamFrame) {
      // Handle HTTP/2 clear text upgrade request
      if (msg instanceof Http2HeadersFrame) {
        Http2HeadersFrame frame = (Http2HeadersFrame) msg;
        connection.onHeadersRead(ctx, 1, frame.headers(), frame.padding(), frame.isEndStream());
      } else if (msg instanceof Http2DataFrame) {
        Http2DataFrame frame = (Http2DataFrame) msg;
        connection.onDataRead(ctx, 1, frame.content(), frame.padding(), frame.isEndStream());
      }
    } else {
      super.channelRead(ctx, msg);
    }
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    read = false;
    // Super will flush
    super.channelReadComplete(ctx);
  }

  @Override
  public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) throws Http2Exception {
    throw new UnsupportedOperationException();
  }

  private void _writePriority(Http2Stream stream, StreamPriorityBase priority) {
      encoder().writePriority(chctx, stream.id(), priority.getDependency(), priority.getWeight(), priority.isExclusive(), chctx.newPromise());
  }

  void writePriority(Http2Stream stream, StreamPriorityBase priority) {
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writePriority(stream, priority);
    } else {
      executor.execute(() -> {
        _writePriority(stream, priority);
      });
    }
  }
}
