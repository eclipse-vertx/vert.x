/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.http2.multiplex;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.DefaultHttp2UnknownFrame;
import io.netty.handler.codec.http2.Http2DataChunkedInput;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.netty.handler.stream.ChunkedInput;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.concurrent.FutureListener;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import io.vertx.core.http.impl.http2.Http2StreamBase;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public abstract class Http2MultiplexConnection<S extends Http2StreamBase> extends ConnectionBase implements HttpConnection {

  protected final Http2MultiplexHandler handler;
  private final IntObjectMap<StreamChannel> channels;
  private final TCPMetrics<?> tcpMetrics;
  private final Deque<Promise<Buffer>> pendingPingAcks;
  private boolean initialSettingsReceived;
  private int windowSize;
  private Handler<Http2Settings> remoteSettingsHandler;
  private Handler<Void> shutdownHandler;
  private Handler<GoAway> goAwayHandler;
  private Handler<Buffer> pingHandler;

  public Http2MultiplexConnection(Http2MultiplexHandler handler, TCPMetrics<?> tcpMetrics, ChannelHandlerContext chctx, ContextInternal context) {
    super(context, chctx);
    this.handler = handler;
    this.tcpMetrics = tcpMetrics;
    this.channels = new IntObjectHashMap<>();
    this.initialSettingsReceived = false;
    this.pendingPingAcks = new ArrayDeque<>();
    this.windowSize = handler.windowsSize();
  }

  @Override
  public NetworkMetrics<?> metrics() {
    return tcpMetrics;
  }

  final S stream(int id) {
    StreamChannel streamChannel = channels.get(id);
    return streamChannel != null ? streamChannel.stream : null;
  }

  final int numberOfChannels() {
    return channels.size();
  }

  final void registerChannel(S stream, Http2FrameStream frameStream, ChannelHandlerContext chctx) {
    StreamChannel channel = new StreamChannel(stream, frameStream, chctx);
    channels.put(stream.id(), channel);
  }

  abstract boolean isServer();

  abstract void receiveHeaders(ChannelHandlerContext chctx, Http2FrameStream frameStream, Http2Headers headers, boolean ended);

  void receiveData(ChannelHandlerContext chctx, int streamId, ByteBuf content, boolean ended, int initialWindowSize) {
    StreamChannel channel = channels.get(streamId);
    ByteBuf buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(content.readableBytes());
    buffer.writeBytes(content, content.readerIndex(), content.readableBytes());
    Buffer buff = BufferInternal.buffer(buffer);
    channel.bytesConsumed += buff.length();
    if (channel.bytesConsumed > initialWindowSize) {
      chctx.channel().config().setAutoRead(false);
    }
    Http2StreamBase stream = channel.stream;
    stream.onData(buff);
    if (ended) {
      stream.onTrailers();
    }
  }

  void receiveUnknownFrame(int streamId, int type, int flags, ByteBuf content) {
    StreamChannel channel = channels.get(streamId);
    ByteBuf buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(content.readableBytes());
    buffer.writeBytes(content, content.readerIndex(), content.readableBytes());
    Buffer buff = BufferInternal.buffer(buffer);
    channel.stream.onCustomFrame(type, flags, buff);
  }

  void receiveResetFrame(int streamId, long code) {
    StreamChannel channel = channels.get(streamId);
    channel.stream.onReset(code);
  }

  void onWritabilityChanged(int streamId) {
    StreamChannel channel = channels.get(streamId);
    channel.stream.onWritabilityChanged();
  }

  public void consumeCredits(int streamId, int amountOfBytes) {
    StreamChannel channel = channels.get(streamId);
    if (channel != null) {
      channel.bytesConsumed -= amountOfBytes;
      if (channel.bytesConsumed == 0) {
        channel.channelContext.channel().config().setAutoRead(true);
      }
    }
  }

  public boolean supportsSendFile() {
    return true;
  }

  public void sendFile(int streamId, ChunkedInput<ByteBuf> file, Promise<Void> promise) {
    StreamChannel channel = channels.get(streamId);
    if (channel != null) {
      Http2DataChunkedInput chunkedFile = new Http2DataChunkedInput(file, channel.frameStream);
      ChannelFuture channelFuture = channel.channelContext.writeAndFlush(chunkedFile);
      channelFuture.addListener((PromiseInternal<Void>) promise);
    } else {
      promise.fail(ConnectionBase.CLOSED_EXCEPTION);
    }
  }

  public void writeFrame(int streamId, int type, int flags, ByteBuf payload, Promise<Void> promise) {
    writeStreamFrame(streamId, new DefaultHttp2UnknownFrame((byte)type, new Http2Flags((short) flags), payload), promise);
  }

  public void writeData(int streamId, ByteBuf buf, boolean end, Promise<Void> promise) {
    writeStreamFrame(streamId, new DefaultHttp2DataFrame(buf, end), promise);
  }

  public void writeReset(int streamId, long code, Promise<Void> promise) {
    writeStreamFrame(streamId, new DefaultHttp2ResetFrame(code), promise);
  }

  void writeStreamFrame(int streamId, Http2StreamFrame frame, Promise<Void> promise) {
    StreamChannel channel = channels.get(streamId);
    if (channel == null) {
      promise.fail(ConnectionBase.CLOSED_EXCEPTION);
    } else {
      writeStreamFrame(frame, channel.channelContext, (PromiseInternal<Void>)promise);
      if (streamId < 0) {
        int newId = channel.frameStream.id();
        channels.remove(streamId);
        channels.put(newId, channel);
        channel.stream.init(newId, channel.channelContext.channel().isWritable());
      }
    }
  }

  void writeStreamFrame(Http2Frame frame, FutureListener<Void> listener) {
    writeStreamFrame(frame, chctx, listener);
  }

  void writeStreamFrame(Http2Frame frame, ChannelHandlerContext chctx, FutureListener<Void> listener) {
    ChannelPromise p = chctx.newPromise();
    chctx.writeAndFlush(frame, p);
    if (listener != null) {
      p.addListener(listener);
    }
  }

  public final Http2HeadersMultiMap newHeaders() {
    return new Http2HeadersMultiMap(new DefaultHttp2Headers());
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    handler.writeGoAway(errorCode, debugData != null ? ((BufferInternal)debugData).getByteBuf() : Unpooled.EMPTY_BUFFER, context.promise());
    return this;
  }

  @Override
  public HttpConnection goAwayHandler(@Nullable Handler<GoAway> handler) {
    this.goAwayHandler = handler;
    return this;
  }

  @Override
  public HttpConnection shutdownHandler(Handler<Void> handler) {
    this.shutdownHandler = handler;
    return this;
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    PromiseInternal<Void> promise = context.promise();
    Http2FrameCodec frameCodec = channel.pipeline().get(Http2FrameCodec.class);
    frameCodec.gracefulShutdownTimeoutMillis(unit.toMillis(timeout));
    ChannelFuture closeFuture = channel.close();
    closeFuture.addListener(promise);
    return promise.future();
  }

  @Override
  public Http2MultiplexConnection closeHandler(Handler<Void> handler) {
    return (Http2MultiplexConnection) super.closeHandler(handler);
  }

  @Override
  public Http2Settings settings() {
    return HttpUtils.toVertxSettings(handler.localSettings());
  }

  @Override
  public int getWindowSize() {
    return windowSize;
  }

  @Override
  public HttpConnection setWindowSize(int windowSize) {
    if (windowSize <= 0) {
      throw new IllegalArgumentException("Invalid window size: " + windowSize);
    }
    try {
      int windowSizeIncrement = windowSize - this.windowSize;
      handler.incrementWindowsSize(windowSizeIncrement);
      this.windowSize = windowSize;
      return this;
    } catch (Http2Exception e) {
      throw new VertxException(e);
    }
  }

  @Override
  public Future<Void> updateSettings(Http2Settings settings) {
    PromiseInternal<Void> promise = context.promise();
    handler.writeSettings(HttpUtils.fromVertxSettings(settings), promise);
    return promise.future();
  }

  @Override
  public Http2Settings remoteSettings() {
    io.netty.handler.codec.http2.Http2Settings remoteSettings = handler.remoteSettings();
    return remoteSettings != null ? HttpUtils.toVertxSettings(remoteSettings) : null;
  }

  @Override
  public HttpConnection remoteSettingsHandler(Handler<Http2Settings> handler) {
    this.remoteSettingsHandler = handler;
    return this;
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    if (data.length() != 8) {
      throw new IllegalArgumentException("Ping data must be exactly 8 bytes");
    }
    long content = data.getLong(0);
    Promise<Buffer> promise = context.promise();
    Http2PingFrame pingFrame = new DefaultHttp2PingFrame(content, false);
    writeStreamFrame(pingFrame, future -> {
      if (future.isSuccess()) {
        pendingPingAcks.add(promise);
      } else {
        promise.fail(future.cause());
      }
    });
    return promise.future();
  }

  @Override
  public HttpConnection pingHandler(@Nullable Handler<Buffer> handler) {
    this.pingHandler = handler;
    return this;
  }

  @Override
  public Http2MultiplexConnection exceptionHandler(Handler<Throwable> handler) {
    return (Http2MultiplexConnection) super.exceptionHandler(handler);
  }

  void receiveSettings(ChannelHandlerContext chctx, io.netty.handler.codec.http2.Http2Settings settings_) {
    Http2Settings settings = HttpUtils.toVertxSettings(settings_);
    if (!initialSettingsReceived) {
      initialSettingsReceived = true;
      onInitialSettingsReceived(settings);
    } else {
      onSettings(settings);
    }
  }

  void receiveGoAway(long errorCode, int lastStreamId, Buffer debugData) {
    Handler<GoAway> handler = goAwayHandler;
    if (handler != null) {
      context.emit(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(debugData), goAwayHandler);
    }
  }

  void receivePingAck(long content, boolean ack) {
    Buffer msg = Buffer.buffer().appendLong(content);
    if (ack) {
      Promise<Buffer> pendingPongHandler = pendingPingAcks.poll();
      pendingPongHandler.complete(msg);
    } else {
      Handler<Buffer> handler = pingHandler;
      if (handler != null) {
        context.emit(msg, handler);
      }
    }
  }

  void onInitialSettingsReceived(Http2Settings settings) {
  }

  void onSettings(Http2Settings settings) {
    Handler<Http2Settings> handler = remoteSettingsHandler;
    if (handler != null) {
      context.emit(settings, handler);
    }
  }

  void onStreamClose(int streamId) {
    StreamChannel streamChannel = channels.remove(streamId);
    GoAway goAway = handler.goAwayStatus();
    if (streamChannel != null) {
      Http2StreamBase stream = streamChannel.stream;
      if (goAway != null) {
        stream.onException(new HttpClosedException(goAway));
      }
      stream.onClose();
    }
  }

  void onGoAway(long errorCode, int lastStreamId, Buffer debugData) {
    Handler<Void> handler = shutdownHandler;
    if (handler != null) {
      context.emit(null, handler);
    }
  }

  void onIdle() {
    close();
  }

  void onClose() {
    handleClosed();
  }

  void onException(Throwable err) {
    handleException(err);
    if (err instanceof Http2Exception) {
      Http2Exception http2Err = (Http2Exception) err;
      switch (http2Err.error()) {
        case PROTOCOL_ERROR:
          close();
          break;
      }
    }
  }

  void onException(int streamId, Throwable err) {
    StreamChannel channel = channels.get(streamId);
    if (channel != null) {
      channel.stream.onException(err);
    }
  }

  private class StreamChannel {

    private final S stream;
    private final Http2FrameStream frameStream;
    private final ChannelHandlerContext channelContext;
    private int bytesConsumed;

    private StreamChannel(S stream, Http2FrameStream frameStream, ChannelHandlerContext channelContext) {
      this.stream = Objects.requireNonNull(stream);
      this.frameStream = Objects.requireNonNull(frameStream);
      this.channelContext = Objects.requireNonNull(channelContext);
    }
  }
}
