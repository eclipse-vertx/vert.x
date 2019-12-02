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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http2.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxHttp2ConnectionHandler<C extends Http2ConnectionBase> extends Http2ConnectionHandler implements Http2FrameListener, Http2Connection.Listener {

  private final Function<VertxHttp2ConnectionHandler<C>, C> connectionFactory;
  private C connection;
  private ChannelHandlerContext chctx;
  private Handler<C> addHandler;
  private Handler<C> removeHandler;
  private final boolean useDecompressor;

  public VertxHttp2ConnectionHandler(
      Function<VertxHttp2ConnectionHandler<C>, C> connectionFactory,
      boolean useDecompressor,
      Http2ConnectionDecoder decoder,
      Http2ConnectionEncoder encoder,
      Http2Settings initialSettings) {
    super(decoder, encoder, initialSettings);
    this.connectionFactory = connectionFactory;
    this.useDecompressor = useDecompressor;
    encoder().flowController().listener(s -> {
      if (connection != null) {
        connection.onStreamWritabilityChanged(s);
      }
    });
    connection().addListener(this);
  }

  public ChannelHandlerContext context() {
    return chctx;
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
    this.removeHandler = handler;
    return this;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    super.handlerAdded(ctx);
    chctx = ctx;
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
    Http2Settings serverUpgradeSettings,
    HttpRequest request) throws Exception {
    onHttpServerUpgrade(serverUpgradeSettings);
    onSettingsRead(ctx, serverUpgradeSettings);
    // Http2ServerConnection c = (Http2ServerConnection) connection;
    // return c.createUpgradeRequest(request);
  }

  public void clientUpgrade(ChannelHandlerContext ctx) throws Exception {
    onHttpClientUpgrade();
    // super call writes the connection preface
    // we need to flush to send it
    // this is called only on the client
    ctx.flush();
  }

  @Override
  public void channelInactive(ChannelHandlerContext chctx) throws Exception {
    super.channelInactive(chctx);
    if (connection != null) {
      connection.handleClosed();
      if (removeHandler != null) {
        removeHandler.handle(connection);
      }
    }
  }

  @Override
  protected void onConnectionError(ChannelHandlerContext ctx, boolean outbound, Throwable cause, Http2Exception http2Ex) {
    connection.onConnectionError(cause);
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
      if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.ALL_IDLE) {
        connection.handleIdle();
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
    connection.onGoAwaySent(lastStreamId, errorCode, debugData);
  }

  @Override
  public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    connection.onGoAwayReceived(lastStreamId, errorCode, debugData);
  }

  //

  void writeHeaders(Http2Stream stream, Http2Headers headers, boolean end, int streamDependency, short weight, boolean exclusive, FutureListener<Void> listener) {
    ChannelPromise promise = listener == null ? chctx.voidPromise() : chctx.newPromise().addListener(listener);
    encoder().writeHeaders(chctx, stream.id(), headers, streamDependency, weight, exclusive, 0, end, promise);
  }

  void writeData(Http2Stream stream, ByteBuf chunk, boolean end, FutureListener<Void> listener) {
    ChannelPromise promise = listener == null ? chctx.voidPromise() : chctx.newPromise().addListener(listener);
    encoder().writeData(chctx, stream.id(), chunk, 0, end, promise);
    Http2RemoteFlowController controller = encoder().flowController();
    if (!controller.isWritable(stream) || end) {
      try {
        encoder().flowController().writePendingBytes();
      } catch (Http2Exception e) {
        onError(chctx, true, e);
      }
    }
    chctx.channel().flush();
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
    chctx.channel().flush();
  }

  /**
   * Consume {@code numBytes} for {@code stream}  in the flow controller, this must be called from event loop.
   */
  void consume(Http2Stream stream, int numBytes) {
    try {
      boolean windowUpdateSent = decoder().flowController().consumeBytes(stream, numBytes);
      if (windowUpdateSent) {
        chctx.channel().flush();
      }
    } catch (Http2Exception e) {
      onError(chctx, true, e);
    }
  }

  void writeFrame(Http2Stream stream, byte type, short flags, ByteBuf payload) {
    encoder().writeFrame(chctx, type, stream.id(), new Http2Flags(flags), payload, chctx.newPromise());
    chctx.flush();
  }

  void writeReset(int streamId, long code) {
    encoder().writeRstStream(chctx, streamId, code, chctx.newPromise());
    chctx.flush();
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
    chctx.flush();
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
    chctx.flush();
  }

  void writePushPromise(int streamId, Http2Headers headers, Handler<AsyncResult<Integer>> completionHandler) {
    int promisedStreamId = connection().local().incrementAndGetNextStreamId();
    ChannelPromise promise = chctx.newPromise();
    promise.addListener(fut -> {
      if (fut.isSuccess()) {
        completionHandler.handle(Future.succeededFuture(promisedStreamId));
      } else {
        completionHandler.handle(Future.failedFuture(fut.cause()));
      }
    });
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writePushPromise(streamId, promisedStreamId, headers, promise);
    } else {
      executor.execute(() -> {
        _writePushPromise(streamId, promisedStreamId, headers, promise);
      });
    }
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
    assert connection != null;
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
    connection = connectionFactory.apply(this);
    if (useDecompressor) {
      decoder().frameListener(new DelegatingDecompressorFrameListener(decoder().connection(), connection));
    } else {
      decoder().frameListener(connection);
    }
    connection.onSettingsRead(ctx, settings);
    if (addHandler != null) {
      addHandler.handle(connection);
    }
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof Http2StreamFrame) {
      // Handle HTTP/2 clear text upgrade request
      if (msg instanceof Http2HeadersFrame) {
        Http2HeadersFrame frame = (Http2HeadersFrame) msg;
        connection.onHeadersRead(ctx, 1, frame.headers(), frame.padding(), frame.isEndStream());
      } else if (msg instanceof Http2DataFrame) {
        Http2DataFrame frame = (Http2DataFrame) msg;
        Http2LocalFlowController controller = decoder().flowController();
        Http2Stream stream = decoder().connection().stream(1);
        controller.receiveFlowControlledFrame(stream, frame.content(), frame.padding(), frame.isEndStream());
        connection.onDataRead(ctx, 1, frame.content(), frame.padding(), frame.isEndStream());
      }
    } else {
      super.channelRead(ctx, msg);
    }
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

  private void _writePriority(Http2Stream stream, int streamDependency, short weight, boolean exclusive) {
      encoder().writePriority(chctx, stream.id(), streamDependency, weight, exclusive, chctx.newPromise());
  }

  void writePriority(Http2Stream stream, int streamDependency, short weight, boolean exclusive) {
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writePriority(stream, streamDependency, weight, exclusive);
    } else {
      executor.execute(() -> {
        _writePriority(stream, streamDependency, weight, exclusive);
      });
    }
  }
}
