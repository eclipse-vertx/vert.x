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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public abstract class Http3ConnectionBase extends ConnectionBase implements HttpConnection {

  private static final Logger log = LoggerFactory.getLogger(Http3ConnectionBase.class);

  public static final AttributeKey<VertxHttpStreamBase> QUIC_CHANNEL_STREAM_KEY =
    AttributeKey.valueOf(VertxHttpStreamBase.class, "QUIC_CHANNEL_STREAM");

  protected final LongObjectMap<QuicStreamChannel> quicStreamChannels = new LongObjectHashMap<>();

  private static ByteBuf safeBuffer(ByteBuf buf) {
    ByteBuf buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(buf.readableBytes());
    buffer.writeBytes(buf);
    return buffer;
  }

  protected abstract void onHeadersRead(VertxHttpStreamBase<?, ?> stream, Http3Headers headers,
                                        StreamPriorityBase streamPriority, boolean endOfStream,
                                        QuicStreamChannel streamChannel);

  protected final ChannelHandlerContext handlerContext;
  protected VertxHttp3ConnectionHandler<? extends Http3ConnectionBase> handler;
//  protected final Http2Connection.PropertyKey streamKey;
  private boolean shutdown;
  private Handler<HttpSettings> remoteSettingsHandler;
  private final ArrayDeque<Handler<Void>> updateSettingsHandlers = new ArrayDeque<>();
  private final ArrayDeque<Promise<Buffer>> pongHandlers = new ArrayDeque<>();
  private HttpSettings localSettings;
  private HttpSettings remoteSettings;
  private Handler<GoAway> goAwayHandler;
  private Handler<Void> shutdownHandler;
  private Handler<Buffer> pingHandler;
  private GoAway goAwayStatus;
  private int windowSize;
  private long maxConcurrentStreams;

  public Http3ConnectionBase(ContextInternal context, VertxHttp3ConnectionHandler<? extends Http3ConnectionBase> handler) {
    super(context, handler.context());
    this.handler = handler;
    this.handlerContext = chctx;
    this.windowSize = -1;  //TODO: old code: handler.connection().local().flowController().windowSize(handler.connection().connectionStream());
    this.maxConcurrentStreams = 0xFFFFFFFFL;  //TODO: old code: io.vertx.core.http.Http2Settings.DEFAULT_MAX_CONCURRENT_STREAMS;
//    this.streamKey = handler.connection().newKey();
    this.localSettings = handler.initialSettings();
  }

  public VertxInternal vertx() {
    return vertx;
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
    ArrayList<VertxHttpStreamBase> vertxHttpStreams = new ArrayList<>();
    getActiveQuicStreamChannels().forEach(quicStreamChannel -> {
      vertxHttpStreams.add(VertxHttp3ConnectionHandler.getStreamOfQuicStreamChannel(quicStreamChannel));
    });
    for (VertxHttpStreamBase stream : vertxHttpStreams) {
      stream.context.dispatch(v -> stream.handleException(cause));
    }
    handleException(cause);
  }

  protected List<QuicStreamChannel> getActiveQuicStreamChannels() {
    return quicStreamChannels.values().stream().filter(Channel::isActive).collect(Collectors.toList());
  }

//  VertxHttpStreamBase<?, ?> stream(int id) {
//    VertxHttpStreamBase<?, ?> s = handler.connection().stream(id);
//    if (s == null) {
//      return null;
//    }
//    return s.getProperty(streamKey);
//  }

  void onStreamError(VertxHttpStreamBase<?, ?> stream, Throwable cause) {
    if (stream != null) {
      stream.onException(cause);
    }
  }

  void onStreamWritabilityChanged(VertxHttpStreamBase<?, ?> stream) {
//    this.handler.getHttp3ConnectionHandler().channelWritabilityChanged();
    if (stream != null) {
      stream.onWritabilityChanged();
    }
  }

  void onStreamClosed(VertxHttpStreamBase<?, ?> stream) {
    if (stream != null) {
      boolean active = chctx.channel().isActive();
      if (goAwayStatus != null) {
        stream.onException(new HttpClosedException(goAwayStatus));
      } else if (!active) {
        stream.onException(HttpUtils.STREAM_CLOSED_EXCEPTION);
      }
      stream.onClose();
    }
    checkShutdown();
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
    Handler<GoAway> handler;
    synchronized (this) {
      if (this.goAwayStatus != null) {
        return false;
      }
      this.goAwayStatus = goAway;
      handler = goAwayHandler;
    }
    if (handler != null) {
      context.dispatch(new GoAway(goAway), handler);
    }
    checkShutdown();
    return true;
  }

  // Http3FrameListener

//  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, VertxHttpStreamBase<?, ?> stream, int streamDependency,
                             short weight, boolean exclusive) {
    if (stream != null) {
      StreamPriorityBase streamPriority = new Http2StreamPriority()
        .setDependency(streamDependency)
        .setWeight(weight)
        .setExclusive(exclusive);
      stream.onPriorityChange(streamPriority);
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
  public void onSettingsRead(ChannelHandlerContext ctx, HttpSettings settings) {
    boolean changed;
    Handler<HttpSettings> handler;
    synchronized (this) {
//      Long val = settings.maxConcurrentStreams();  //TODO:
      Long val = 5L;
      if (val != null) {
        if (remoteSettings != null) {
          changed = val != maxConcurrentStreams;
        } else {
          changed = false;
        }
        maxConcurrentStreams = val;
      } else {
        changed = false;
      }
      remoteSettings = settings;
      handler = remoteSettingsHandler;
    }
    if (handler != null) {
      context.dispatch(settings, handler);
    }
    if (changed) {
      concurrencyChanged(maxConcurrentStreams);
    }
  }

//  @Override
  public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
    Handler<Buffer> handler = pingHandler;
    if (handler != null) {
      Buffer buff = Buffer.buffer().appendLong(data);
      context.dispatch(v -> handler.handle(buff));
    }
  }

//  @Override
  public void onPingAckRead(ChannelHandlerContext ctx, long data) {
    Promise<Buffer> handler = pongHandlers.poll();
    if (handler != null) {
      Buffer buff = Buffer.buffer().appendLong(data);
      handler.complete(buff);
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
//  public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, VertxHttpStreamBase<?, ?> stream,
//                             Http2Flags flags, ByteBuf payload) {
//    VertxHttpStreamBase<?, ?, Http2Headers> stream = stream(streamId);
//    if (stream != null) {
//      Buffer buff = Buffer.buffer(safeBuffer(payload));
//      stream.onCustomFrame(new HttpFrameImpl(frameType, flags.value(), buff));
//    }
//  }

//  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, VertxHttpStreamBase<?, ?> stream, long errorCode) {
//    VertxHttpStreamBase<?, ?, Http2Headers> stream = stream(streamId);
    if (stream != null) {
      stream.onReset(errorCode);
    }
  }

//  @Override
  public int onDataRead(ChannelHandlerContext ctx, VertxHttpStreamBase<?, ?> stream,
    ByteBuf data, int padding, boolean endOfStream) {
    if (stream != null) {
      data = safeBuffer(data);
      Buffer buff = BufferInternal.buffer(data);
      stream.onData(buff);
      if (endOfStream) {
        stream.onEnd();
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
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
//    if (errorCode < 0) {
//      throw new IllegalArgumentException();
//    }
//    if (lastStreamId < 0) {
//      lastStreamId = handler.connection().remote().lastStreamCreated();
//    }
//    handler.writeGoAway(errorCode, lastStreamId, debugData != null ? debugData.getByteBuf() : Unpooled.EMPTY_BUFFER);
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
  public Http3ConnectionBase closeHandler(Handler<Void> handler) {
    return (Http3ConnectionBase) super.closeHandler(handler);
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
  public HttpConnection remoteSettingsHandler(Handler<Http2Settings> handler) {
    throw new UnsupportedOperationException("This method is not implemented for HTTP/3 and should not be used. Please" +
      " use remoteHttpSettingsHandler() instead.");
  }

  @Override
  public HttpConnection remoteHttpSettingsHandler(Handler<HttpSettings> handler) {
    remoteSettingsHandler = handler;
    return this;
  }

  @Override
  public Http2Settings remoteSettings() {
    throw new UnsupportedOperationException("This method is not implemented for HTTP/3 and should not be used. Please" +
      " use remoteHttpSettings() instead.");
  }

  @Override
  public HttpSettings remoteHttpSettings() {
    return remoteSettings;
  }

  @Override
  public Http2Settings settings() {
    throw new UnsupportedOperationException("This method is not implemented for HTTP/3 and should not be used. Please" +
      " use httpSettings() instead.");
  }

  @Override
  public HttpSettings httpSettings() {
    return localSettings;
  }
//  @Override
//  public Future<Void> updateSettings(Http2Settings settings) {
//    Promise<Void> promise = context.promise();
//    io.netty.handler.codec.http2.Http2Settings settingsUpdate = HttpUtils.fromVertxSettings(settings);
//    updateSettings(settingsUpdate, promise);
//    return promise.future();
//  }

  @Override
  public Future<Void> updateSettings(io.vertx.core.http.Http2Settings settings) {
    throw new UnsupportedOperationException("This method is not implemented for HTTP/3 and should not be used. Please" +
      " use updateHttpSettings() instead.");
  }

  @Override
  public Future<Void> updateHttpSettings(HttpSettings settings) {
//    Http2Settings current = handler.decoder().localSettings();
//    for (Map.Entry<Character, Long> entry : current.entrySet()) {
//      Character key = entry.getKey();
//      if (Objects.equals(settingsUpdate.get(key), entry.getValue())) {
//        settingsUpdate.remove(key);
//      }
//    }
//    Handler<Void> pending = v -> {
//      synchronized (Http2ConnectionBase.this) {
//        localSettings.putAll(settingsUpdate);
//      }
//      if (completionHandler != null) {
//        completionHandler.handle(Future.succeededFuture());
//      }
//    };
//    updateSettingsHandlers.add(pending);
//    handler.writeSettings(settingsUpdate).addListener(fut -> {
//      if (!fut.isSuccess()) {
//        synchronized (Http2ConnectionBase.this) {
//          updateSettingsHandlers.remove(pending);
//        }
//        if (completionHandler != null) {
//          completionHandler.handle(Future.failedFuture(fut.cause()));
//        }
//      }
//    });

    //TODO: impl this method
    PromiseInternal<Void> promise = context.promise();
    promise.tryComplete();
    return promise;
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    if (data.length() != 8) {
      throw new IllegalArgumentException("Ping data must be exactly 8 bytes");
    }
    Promise<Buffer> promise = context.promise();
    handler.writePing(data.getLong(0)).addListener(fut -> {
      if (fut.isSuccess()) {
        synchronized (Http3ConnectionBase.this) {
          pongHandlers.add(promise);
        }
      } else {
        promise.fail(fut.cause());
      }
    });
    return promise.future();
  }

  @Override
  public synchronized HttpConnection pingHandler(Handler<Buffer> handler) {
    pingHandler = handler;
    return this;
  }

  // Necessary to set the covariant return type
  @Override
  public Http3ConnectionBase exceptionHandler(Handler<Throwable> handler) {
    return (Http3ConnectionBase) super.exceptionHandler(handler);
  }

  public void consumeCredits(QuicStreamChannel stream, int numBytes) {
//    throw new RuntimeException("Method not implemented");
  }


  //  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, VertxHttpStreamBase<?, ?> stream,
                            Http3Headers headers, boolean endOfStream, QuicStreamChannel streamChannel) throws Http2Exception {
    onHeadersRead(stream, headers, null, endOfStream, streamChannel);
  }

  // Private

  private void checkShutdown() {
    Handler<Void> shutdownHandler;
    synchronized (this) {
      if (shutdown) {
        return;
      }
//      Http3ConnectionBase conn = handler.connection();
      if ((!handler.goAwayReceived() /*&& !conn.goAwaySent()*/) /*|| conn.numActiveStreams() > 0*/) {
        // TODO: correct these
        return;
      }
      shutdown  = true;
      shutdownHandler = this.shutdownHandler;
    }
    doShutdown(shutdownHandler);
  }

  protected void doShutdown(Handler<Void> shutdownHandler) {
    if (shutdownHandler != null) {
      context.dispatch(shutdownHandler);
    }
  }

}
