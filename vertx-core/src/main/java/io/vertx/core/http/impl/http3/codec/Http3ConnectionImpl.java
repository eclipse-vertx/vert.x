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

package io.vertx.core.http.impl.http3.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http2.*;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.http3.Http3HeadersMultiMap;
import io.vertx.core.http.impl.http3.Http3StreamBase;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Http3ConnectionImpl extends ConnectionBase implements HttpConnection, io.vertx.core.http.impl.http3.Http3Connection {

  private static final Logger log = LoggerFactory.getLogger(Http3ConnectionImpl.class);

  protected final LongObjectMap<QuicStreamChannel> quicStreamChannels = new LongObjectHashMap<>();

  private static ByteBuf safeBuffer(ByteBuf buf) {
    ByteBuf buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(buf.readableBytes());
    buffer.writeBytes(buf);
    return buffer;
  }
  protected abstract void onHeadersRead(Http3StreamBase stream, QuicStreamChannel streamChannel, Http3Headers headers, StreamPriorityBase streamPriority, boolean endOfStream);

  protected final ChannelHandlerContext handlerContext;
  private final VertxHttp3ConnectionHandler handler;
  private boolean shutdown;
  private Handler<HttpSettings> remoteSettingsHandler;
  private final ArrayDeque<Handler<Void>> updateSettingsHandlers = new ArrayDeque<>();
  private final ArrayDeque<Promise<Buffer>> pongHandlers = new ArrayDeque<>();
  private Http3SettingsFrame localSettings;
  private Http3SettingsFrame remoteSettings;
  private Handler<GoAway> goAwayHandler;
  private Handler<Void> shutdownHandler;
  private GoAway goAwayStatus;
  private int windowSize;
  private long maxConcurrentStreams;

  public Http3ConnectionImpl(ContextInternal context, VertxHttp3ConnectionHandler handler) {
    super(context, handler.context());
    this.handler = handler;
    this.handlerContext = chctx;

    this.windowSize = -1;  //TODO: old code: handler.connection().local().flowController().windowSize(handler
    // .connection().connectionStream());
    this.maxConcurrentStreams = 0xFFFFFFFFL;  //TODO: old code: io.vertx.core.http.Http2Settings
    // .DEFAULT_MAX_CONCURRENT_STREAMS;
//    this.streamKey = handler.connection().newKey();
//    this.windowSize = handler.connection().local().flowController().windowSize(handler.connection().connectionStream());
//    this.maxConcurrentStreams = io.vertx.core.http.Http3Settings.DEFAULT_MAX_CONCURRENT_STREAMS;
//    this.streamKey = handler.connection().newKey();
//    this.localSettings = handler.initialSettings();
  }

  public Http3HeadersMultiMap newHeaders() {
    return new Http3HeadersMultiMap(new DefaultHttp3Headers());
  }

  @Override
  public void handleClosed() {
    super.handleClosed();
  }

  protected void handleIdle(IdleStateEvent event) {
    log.debug("The connection will be closed due to timeout");
    chctx.close();
  }

  synchronized void onConnectionError(Throwable cause) {
    ArrayList<Http3StreamBase> vertxHttpStreams = new ArrayList<>();
    getActiveQuicStreamChannels().forEach(quicStreamChannel -> {
      vertxHttpStreams.add(stream(quicStreamChannel));
    });
    for (Http3StreamBase stream : vertxHttpStreams) {
      stream.context().dispatch(v -> stream.handleException(cause));
    }
    handleException(cause);
  }

  protected List<QuicStreamChannel> getActiveQuicStreamChannels() {
    return quicStreamChannels.values().stream().filter(Channel::isActive).collect(Collectors.toList());
  }

  public Http3StreamBase stream(QuicStreamChannel streamChannel) {
    if (streamChannel == null) {
      return null;
    }
    return VertxHttp3ConnectionHandler.getVertxStreamFromStreamChannel(streamChannel);
  }

  void onStreamError(Http3StreamBase stream, Throwable cause) {
    if (stream != null) {
      stream.onException(cause);
    }
  }

  void onStreamWritabilityChanged(Http3StreamBase stream) {
//    this.handler.getHttp3ConnectionHandler().channelWritabilityChanged();
    if (stream != null) {
      stream.onWritabilityChanged();
    }
  }

  void onStreamClosed(Http3StreamBase stream) {
    if (stream != null) {
      boolean active = chctx.channel().isActive();
      if (goAwayStatus != null) {
        stream.onException(new HttpClosedException(goAwayStatus));
      } else if (!active) {
        stream.onException(HttpUtils.STREAM_CLOSED_EXCEPTION);
      }
      stream.onClose();
    }
  }

  boolean onGoAwaySent(GoAway goAway) {
    Handler<Void> shutdownHandler;
    synchronized (this) {
      if (this.goAwayStatus != null) {
        return false;
      }
      this.goAwayStatus = goAway;
      shutdownHandler = this.shutdownHandler;
    }
    if (shutdownHandler != null) {
      context.dispatch(shutdownHandler);
    }
    return true;
  }

  boolean onGoAwayReceived(GoAway goAway) {
    Handler<GoAway> goAwayHandler;
    Handler<Void> shutdownHandler;
    synchronized (this) {
      if (this.goAwayStatus != null) {
        return false;
      }
      this.goAwayStatus = goAway;
      goAwayHandler = this.goAwayHandler;
      shutdownHandler = this.shutdownHandler;
    }
    if (goAwayHandler != null) {
      context.dispatch(new GoAway(goAway), goAwayHandler);
    }
    if (shutdownHandler != null) {
      context.dispatch(shutdownHandler);
    }
    return true;
  }

  // Http2FrameListener

//  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, Http3StreamBase stream, int streamDependency, short weight, boolean exclusive) {
      if (stream != null) {
        StreamPriorityBase streamPriority = new Http2StreamPriority()
          .setDependency(streamDependency)
          .setWeight(weight)
          .setExclusive(exclusive);
        stream.onPriorityChange(streamPriority);
      }
  }

  //  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, Http3StreamBase stream,
                            Http3Headers headers, boolean endOfStream, QuicStreamChannel streamChannel) throws Http2Exception {
    if (stream != null && stream.isHeadersReceived()) {
      stream.onTrailers(new Http3HeadersMultiMap(headers));
    } else {
      onHeadersRead(stream, streamChannel, headers, null, endOfStream);
    }
  }

  //  @Override
  public void onSettingsAckRead(ChannelHandlerContext ctx) {
    Handler<Void> handler;
    synchronized (this) {
      handler = updateSettingsHandlers.poll();
    }
    if (handler != null) {
      // No need to run on a particular context it shall be done by the handler instead
      context.emit(handler);
    }
  }

  protected void concurrencyChanged(long concurrency) {
  }

  //  @Override
  public void onSettingsRead(Http3SettingsFrame settings) {
    Handler<HttpSettings> handler;
    synchronized (this) {
      remoteSettings = settings;
      handler = remoteSettingsHandler;
    }
    if (handler != null) {
      context.dispatch(HttpUtils.toVertxSettings(settings), handler);
    }
  }

  //  @Override
  public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                Http2Headers headers, int padding) throws Http2Exception {
  }

  //  @Override
  public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
  }

  //  @Override
  public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
  }

//  @Override
  public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, Http3StreamBase stream,
                             ByteBuf payload) {
    if (stream != null) {
      Buffer buff = BufferInternal.buffer(safeBuffer(payload));
      stream.onCustomFrame(frameType, 0, buff);
    }
  }

  //  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, Http3StreamBase stream, long errorCode) {
//    Http3StreamBase<?, ?, Http2Headers> stream = stream(streamId);
    if (stream != null) {
      stream.onReset(errorCode);
    }
  }

  //  @Override
  public int onDataRead(ChannelHandlerContext ctx, Http3StreamBase stream,
                        ByteBuf data, int padding, boolean endOfStream) {
    if (stream != null) {
      data = safeBuffer(data);
      Buffer buff = BufferInternal.buffer(data);
      stream.onData(buff);
      if (endOfStream) {
        stream.onTrailers();
      }
    }
    return padding;
  }

  @Override
  public int getWindowSize() {
    return windowSize;
  }

  @Override
  public HttpConnection setWindowSize(int windowSize) {
//    try {
//      Http2Stream stream = handler.encoder().connection().connectionStream();
//      int delta = windowSize - this.windowSize;
//      handler.decoder().flowController().incrementWindowSize(stream, delta);
//      this.windowSize = windowSize;
//      return this;
//    } catch (Http2Exception e) {
//      throw new VertxException(e);
//    }
      return this;
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId_, Buffer debugData) {
    long lastStreamId = lastStreamId_;
    if (lastStreamId < 0) {
      lastStreamId = handler.getLastStreamIdOnConnection();
    }
    handler.writeGoAway(errorCode, lastStreamId, debugData != null ? ((BufferInternal)debugData).getByteBuf() : Unpooled.EMPTY_BUFFER);
    return this;
  }

  @Override
  public synchronized HttpConnection goAwayHandler(Handler<GoAway> handler) {
    goAwayHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpConnection shutdownHandler(Handler<Void> handler) {
    shutdownHandler = handler;
    return this;
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    PromiseInternal<Void> promise = vertx.promise();
    shutdown(timeout, unit, promise);
    return promise.future();
  }

  private void shutdown(long timeout, TimeUnit unit, PromiseInternal<Void> promise) {
    if (unit == null) {
      promise.fail("Null time unit");
      return;
    }
    if (timeout < 0) {
      promise.fail("Invalid timeout value " + timeout);
      return;
    }
    handler.gracefulShutdownTimeoutMillis(unit.toMillis(timeout));
    ChannelFuture fut = channel().close();
    fut.addListener(promise);
  }

  @Override
  public Http3ConnectionImpl closeHandler(Handler<Void> handler) {
    return (Http3ConnectionImpl) super.closeHandler(handler);
  }

  @Override
  protected void handleClose(Object reason, ChannelPromise promise) {
    throw new UnsupportedOperationException();
  }

  protected void handleClose(Object reason, PromiseInternal<Void> promise) {
    ChannelPromise pr = chctx.newPromise();
    ChannelPromise channelPromise = pr.addListener(promise); // TRY IMPROVE ?????
    handlerContext.writeAndFlush(Unpooled.EMPTY_BUFFER, pr);
    channelPromise.addListener((ChannelFutureListener) future -> shutdown(0L, TimeUnit.SECONDS));
  }

//  @Override
//  public Future<Void> close() {
//    PromiseInternal<Void> promise = context.promise();
//    ChannelPromise pr = chctx.newPromise();
//    ChannelPromise channelPromise = pr.addListener(promise);
//    handlerContext.writeAndFlush(Unpooled.EMPTY_BUFFER, pr);
//    channelPromise.addListener((ChannelFutureListener) future -> shutdown(0L));
//    return promise.future();
//  }

  @Override
  public HttpConnection remoteHttpSettingsHandler(Handler<HttpSettings> handler) {
    remoteSettingsHandler = handler;
    return this;
  }

  @Override
  public Http3Settings remoteHttpSettings() {
    return HttpUtils.toVertxSettings(remoteSettings);
  }

  @Override
  public Http3Settings httpSettings() {
    return HttpUtils.toVertxSettings(localSettings);
  }
//  @Override
//  public Future<Void> updateSettings(Http2Settings settings) {
//    Promise<Void> promise = context.promise();
//    io.netty.handler.codec.http2.Http2Settings settingsUpdate = HttpUtils.fromVertxSettings(settings);
//    updateSettings(settingsUpdate, promise);
//    return promise.future();
//  }

  @Override
  public Future<Void> updateHttpSettings(HttpSettings settingsUpdate0) {
    Http3SettingsFrame settingsUpdate = HttpUtils.fromVertxSettings((Http3Settings) settingsUpdate0);

    Http3SettingsFrame settingsNew = new DefaultHttp3SettingsFrame();

    Http3SettingsFrame current = handler.initialSettings();

    current.iterator().forEachRemaining(entry -> {
      Long key = entry.getKey();
      if (!Objects.equals(settingsUpdate.get(key), entry.getValue())) {
        settingsNew.put(key, entry.getValue());
  }
    });

    Promise<Void> promise = context.promise();
/*
    Handler<Void> pending = v -> {
      synchronized (Http3ConnectionBase.this) {
        settingsNew.iterator().forEachRemaining(entry -> {
          localSettings.put(entry.getKey(), entry.getValue());
        });
      }
      promise.complete();
    };
    updateSettingsHandlers.add(pending);
*/

    handler.writeSettings(settingsUpdate).addListener(fut -> {
      if (!fut.isSuccess()) {
        synchronized (Http3ConnectionImpl.this) {
//          updateSettingsHandlers.remove(pending);
        }
        promise.fail(fut.cause());
      } else {
        promise.complete();
      }
    });
    return promise.future();
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    throw new UnsupportedOperationException("Ping is not supported in HTTP/3.");
  }

  @Override
  public HttpConnection pingHandler(Handler<Buffer> handler) {
    throw new UnsupportedOperationException("Ping is not supported in HTTP/3.");
  }

  // Necessary to set the covariant return type
  @Override
  public Http3ConnectionImpl exceptionHandler(Handler<Throwable> handler) {
    return (Http3ConnectionImpl) super.exceptionHandler(handler);
  }

  public void consumeCredits(QuicStreamChannel stream, int numBytes) {
//    throw new RuntimeException("Method not implemented");
  }

  @Override
  public boolean supportsSendFile() {
    return false;
  }

  @Override
  public void sendFile(QuicStreamChannel streamChannel, ChunkedInput<ByteBuf> file, Promise<Void> promise) {
    promise.fail("Send file not supported");
  }

  public boolean isWritable(int streamId) {
//    Http2Stream s = handler.connection().stream(streamId);
//    return this.handler.encoder().flowController().isWritable(s);
    //TODO: implement this method
    return true;
  }

  public boolean isWritable(QuicStreamChannel streamChannel) {
    return streamChannel.isWritable();
  }

  @Override
  public void writeFrame(QuicStreamChannel streamChannel, int type, int flags, ByteBuf payload, Promise<Void> promise) {
    handler.writeFrame(streamChannel, (byte) type, (short) flags, payload, (FutureListener<Void>) promise);
  }

  @Override
  public void writePriorityFrame(QuicStreamChannel streamChannel, StreamPriorityBase priority) {
    handler.writePriority(streamChannel, priority);
  }

  @Override
  public void writeHeaders(QuicStreamChannel streamChannel, Http3HeadersMultiMap headers, StreamPriorityBase priority, boolean end, boolean checkFlush, Promise<Void> promise) {
    handler.writeHeaders(streamChannel, headers.prepare(), end, priority, checkFlush, (FutureListener<Void>) promise);
  }


  @Override
  public void writeData(QuicStreamChannel streamChannel, ByteBuf buf, boolean end, Promise<Void> promise) {
    handler.writeData(streamChannel, buf, end, (FutureListener<Void>) promise);
  }

  @Override
  public void writeReset(QuicStreamChannel streamChannel, long code, Promise<Void> promise) {
    handler.writeReset(streamChannel, code, null);
  }

  protected void init_(Http3StreamBase vertxStream, QuicStreamChannel streamChannel) {
    VertxHttp3ConnectionHandler.setVertxStreamOnStreamChannel(streamChannel, vertxStream);
    VertxHttp3ConnectionHandler.setLastStreamIdOnConnection(streamChannel.parent(), streamChannel.streamId());
    this.quicStreamChannels.put(streamChannel.streamId(), streamChannel);
  }
}
