/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxHttp2ConnectionHandler<C extends Http2ConnectionBase> extends Http2ConnectionHandler implements Http2Connection.Listener {

  private final Map<Channel, ? super C> connectionMap;
  C connection;
  private ChannelHandlerContext chctx;

  public VertxHttp2ConnectionHandler(
      Map<Channel, ? super C> connectionMap,
      Http2ConnectionDecoder decoder,
      Http2ConnectionEncoder encoder,
      Http2Settings initialSettings) {
    super(decoder, encoder, initialSettings);
    this.connectionMap = connectionMap;
    encoder().flowController().listener(s -> {
      if (connection != null) {
        connection.onStreamwritabilityChanged(s);
      }
    });
    connection().addListener(this);
  }

  public ChannelHandlerContext context() {
    return chctx;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    super.handlerAdded(ctx);
    chctx = ctx;
  }

  void init(C conn) {
    connection = conn;
    connectionMap.put(chctx.channel(), connection);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    super.exceptionCaught(ctx, cause);
    ctx.close();
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    super.channelActive(ctx);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    super.channelInactive(ctx);
    connectionMap.remove(ctx.channel());
    connection.getContext().executeFromIO(connection::handleClosed);
  }

  @Override
  protected void onConnectionError(ChannelHandlerContext ctx, Throwable cause, Http2Exception http2Ex) {
    connection.getContext().executeFromIO(() -> {
      connection.onConnectionError(cause);
    });
    // Default behavior send go away
    super.onConnectionError(ctx, cause, http2Ex);
  }

  @Override
  protected void onStreamError(ChannelHandlerContext ctx, Throwable cause, Http2Exception.StreamException http2Ex) {
    connection.getContext().executeFromIO(() -> {
      connection.onStreamError(http2Ex.streamId(), http2Ex);
    });
    // Default behavior reset stream
    super.onStreamError(ctx, cause, http2Ex);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    try {
      super.userEventTriggered(ctx, evt);
    } finally {
      if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.ALL_IDLE) {
        ctx.close();
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

  void writeHeaders(Http2Stream stream, Http2Headers headers, boolean end) {
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writeHeaders(stream, headers, end);
    } else {
      executor.execute(() -> {
        _writeHeaders(stream, headers, end);
      });
    }
  }

  private void _writeHeaders(Http2Stream stream, Http2Headers headers, boolean end) {
    encoder().writeHeaders(chctx, stream.id(), headers, 0, end, chctx.newPromise());;
  }

  void writeData(Http2Stream stream, ByteBuf chunk, boolean end) {
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writeData(stream, chunk, end);
    } else {
      executor.execute(() -> {
        _writeData(stream, chunk, end);
      });
    }
  }

  private void _writeData(Http2Stream stream, ByteBuf chunk, boolean end) {
    encoder().writeData(chctx, stream.id(), chunk, 0, end, chctx.newPromise());
    Http2RemoteFlowController controller = encoder().flowController();
    if (!controller.isWritable(stream) || end) {
      try {
        encoder().flowController().writePendingBytes();
      } catch (Http2Exception e) {
        onError(chctx, e);
      }
    }
    chctx.channel().flush();
  }

  ChannelFuture writePing(ByteBuf data) {
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

  private void _writePing(ByteBuf data, ChannelPromise promise) {
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
      onError(chctx, e);
    }
  }

  void writeFrame(Http2Stream stream, byte type, short flags, ByteBuf payload) {
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writeFrame(stream, type, flags, payload);
    } else {
      executor.execute(() -> {
        _writeFrame(stream, type, flags, payload);
      });
    }
  }

  private void _writeFrame(Http2Stream stream, byte type, short flags, ByteBuf payload) {
    encoder().writeFrame(chctx, type, stream.id(), new Http2Flags(flags), payload, chctx.newPromise());
    chctx.flush();
  }

  void writeReset(int streamId, long code) {
    EventExecutor executor = chctx.executor();
    if (executor.inEventLoop()) {
      _writeReset(streamId, code);
    } else {
      executor.execute(() -> {
        _writeReset(streamId, code);
      });
    }
  }

  private void _writeReset(int streamId, long code) {
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

  private void _writePushPromise(int streamId, int promisedStreamId, Http2Headers headers, ChannelPromise promise) {
    encoder().writePushPromise(chctx, streamId, promisedStreamId, headers, 0, promise);
  }
}
