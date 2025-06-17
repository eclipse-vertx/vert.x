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
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.DefaultHttp2UnknownFrame;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import io.vertx.core.http.impl.http2.Http2StreamBase;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.concurrent.TimeUnit;

public abstract class Http2MultiplexConnection extends ConnectionBase implements HttpConnection {

  protected final Http2MultiplexHandler handler;
  private Handler<Http2Settings> remoteSettingsHandler;
  private Handler<Void> shutdownHandler;
  private Handler<GoAway> goAwayHandler;
  private boolean initialSettingsReceived;

  final IntObjectMap<StreamChannel> channels;

  public Http2MultiplexConnection(Http2MultiplexHandler handler, ChannelHandlerContext chctx, ContextInternal context) {
    super(context, chctx);
    this.handler = handler;
    this.channels = new IntObjectHashMap<>();
    this.initialSettingsReceived = false;
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

  public void consumeCredits(int streamId, int amountOfBytes) {
    StreamChannel channel = channels.get(streamId);
    channel.bytesConsumed -= amountOfBytes;
    if (channel.bytesConsumed == 0) {
      channel.chctx.channel().config().setAutoRead(true);
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
    ChannelPromise p = channel.chctx.newPromise();
    channel.chctx.writeAndFlush(frame, p);
    if (promise != null) {
      p.addListener((PromiseInternal<Void>)promise);
    }
  }

  void receiveUnknownFrame(ChannelHandlerContext chctx, int streamId, int type, int flags, ByteBuf content) {
    StreamChannel channel = channels.get(streamId);
    ByteBuf buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(content.readableBytes());
    buffer.writeBytes(content, content.readerIndex(), content.readableBytes());
    Buffer buff = BufferInternal.buffer(buffer);
    channel.stream.onCustomFrame(type, flags, buff);
  }

  void receiveResetFrame(ChannelHandlerContext chctx, int streamId, long code) {
    StreamChannel channel = channels.get(streamId);
    channel.stream.onReset(code);
  }

  void onWritabilityChanged(ChannelHandlerContext chctx, int streamId) {
    StreamChannel channel = channels.get(streamId);
    channel.stream.onWritabilityChanged();
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
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpConnection pingHandler(@Nullable Handler<Buffer> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Http2MultiplexConnection exceptionHandler(Handler<Throwable> handler) {
    return (Http2MultiplexConnection) super.exceptionHandler(handler);
  }

  void receiveSettings(ChannelHandlerContext chctx, io.netty.handler.codec.http2.Http2Settings settings_) {
    Http2Settings settings = HttpUtils.toVertxSettings(settings_);
    if (!initialSettingsReceived) {
      initialSettingsReceived = true;
      onInitialSettings(settings);
    } else {
      onSettings(settings);
    }
  }

  void onInitialSettings(Http2Settings settings) {
  }

  void onSettings(Http2Settings settings) {
    Handler<Http2Settings> handler = remoteSettingsHandler;
    if (handler != null) {
      context.emit(settings, handler);
    }
  }

  void receiveGoAway(long errorCode, int lastStreamId, Buffer debugData) {
    Handler<GoAway> handler = goAwayHandler;
    if (handler != null) {
      context.emit(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(debugData), goAwayHandler);
    }
  }

  void onStreamClose(int streamId) {
  }

  void onGoAway(long errorCode, int lastStreamId, Buffer debugData) {
    Handler<Void> handler = shutdownHandler;
    if (handler != null) {
      context.emit(null, handler);
    }
  }

  void onClose() {
    handleClosed();
  }

  void onException(Throwable err) {
    handleException(err);
  }

  static class StreamChannel {
    final Http2StreamBase stream;
    final Http2FrameStream frameStream;
    final ChannelHandlerContext chctx;
    int bytesConsumed;
    StreamChannel(Http2StreamBase stream, Http2FrameStream frameStream, ChannelHandlerContext chctx) {
      this.stream = stream;
      this.frameStream = frameStream;
      this.chctx = chctx;
    }
  }
}
