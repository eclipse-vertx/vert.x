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

import io.netty.buffer.*;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Http2ConnectionBase extends ConnectionBase implements Http2FrameListener, HttpConnection {

  private static final Logger log = LoggerFactory.getLogger(Http2ConnectionBase.class);

  /**
   * Return a buffer from HTTP/2 codec that Vert.x can use:
   *
   * - if it's a direct buffer (coming likely from OpenSSL) : we get a heap buffer version
   * - if it's a composite buffer we do the same
   * - otherwise we increase the ref count
   */
  static ByteBuf safeBuffer(ByteBuf buf, ByteBufAllocator allocator) {
    ByteBuf buffer = allocator.heapBuffer(buf.readableBytes());
    buffer.writeBytes(buf);
    return buffer;
  }

  protected final ChannelHandlerContext handlerContext;
  protected final VertxHttp2ConnectionHandler handler;
  protected final Http2Connection.PropertyKey streamKey;
  private boolean shutdown;
  private Handler<io.vertx.core.http.Http2Settings> remoteSettingsHandler;
  private final ArrayDeque<Handler<Void>> updateSettingsHandlers = new ArrayDeque<>();
  private final ArrayDeque<Promise<Buffer>> pongHandlers = new ArrayDeque<>();
  private Http2Settings localSettings = new Http2Settings();
  private Http2Settings remoteSettings;
  private Handler<GoAway> goAwayHandler;
  private Handler<Void> shutdownHandler;
  private Handler<Buffer> pingHandler;
  private boolean goneAway;
  private int windowSize;
  private long maxConcurrentStreams;

  public Http2ConnectionBase(ContextInternal context, VertxHttp2ConnectionHandler handler) {
    super(context, handler.context());
    this.handler = handler;
    this.handlerContext = chctx;
    this.windowSize = handler.connection().local().flowController().windowSize(handler.connection().connectionStream());
    this.maxConcurrentStreams = io.vertx.core.http.Http2Settings.DEFAULT_MAX_CONCURRENT_STREAMS;
    this.streamKey = handler.connection().newKey();
  }

  VertxInternal vertx() {
    return vertx;
  }

  @Override
  public void handleClosed() {
    super.handleClosed();
  }

  @Override
  protected void handleInterestedOpsChanged() {
    // Handled by HTTP/2
  }

  @Override
  protected void handleIdle() {
    super.handleIdle();
  }

  synchronized void onConnectionError(Throwable cause) {
    ArrayList<VertxHttp2Stream> streams = new ArrayList<>();
    try {
      handler.connection().forEachActiveStream(stream -> {
        streams.add(stream.getProperty(streamKey));
        return true;
      });
    } catch (Http2Exception e) {
      log.error("Could not get the list of active streams", e);
    }
    for (VertxHttp2Stream stream : streams) {
      stream.context.dispatch(v -> stream.handleException(cause));
    }
    handleException(cause);
  }

  VertxHttp2Stream<?> stream(int id) {
    Http2Stream s = handler.connection().stream(id);
    if (s == null) {
      return null;
    }
    return s.getProperty(streamKey);
  }

  void onStreamError(int streamId, Throwable cause) {
    VertxHttp2Stream stream = stream(streamId);
    if (stream != null) {
      stream.onError(cause);
    }
  }

  void onStreamWritabilityChanged(Http2Stream s) {
    VertxHttp2Stream stream = s.getProperty(streamKey);
    if (stream != null) {
      stream.onWritabilityChanged();
    }
  }

  void onStreamClosed(Http2Stream s) {
    VertxHttp2Stream stream = s.getProperty(streamKey);
    if (stream != null) {
      stream.onClose();
    }
    checkShutdown();
  }

  boolean onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    synchronized (this) {
      if (goneAway) {
        return false;
      }
      goneAway = true;
    }
    checkShutdown();
    return true;
  }

  boolean onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    Handler<GoAway> handler;
    synchronized (this) {
      if (goneAway) {
        return false;
      }
      goneAway = true;
      handler = goAwayHandler;
    }
    if (handler != null) {
      Buffer buffer = Buffer.buffer(debugData);
      context.dispatch(v -> handler.handle(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(buffer)));
    }
    checkShutdown();
    return true;
  }

  // Http2FrameListener

  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) {
      VertxHttp2Stream stream = stream(streamId);
      if (stream != null) {
        StreamPriority streamPriority = new StreamPriority()
          .setDependency(streamDependency)
          .setWeight(weight)
          .setExclusive(exclusive);
        stream.onPriorityChange(streamPriority);
      }
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
    StreamPriority streamPriority = new StreamPriority()
      .setDependency(streamDependency)
      .setWeight(weight)
      .setExclusive(exclusive);
    onHeadersRead(streamId, headers, streamPriority, endOfStream);
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
    onHeadersRead(streamId, headers, null, endOfStream);
  }

  protected abstract void onHeadersRead(int streamId, Http2Headers headers, StreamPriority streamPriority, boolean endOfStream);

  @Override
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

  @Override
  public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
    boolean changed;
    Handler<io.vertx.core.http.Http2Settings> handler;
    synchronized (this) {
      Long val = settings.maxConcurrentStreams();
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
      context.dispatch(HttpUtils.toVertxSettings(settings), handler);
    }
    if (changed) {
      concurrencyChanged(maxConcurrentStreams);
    }
  }

  @Override
  public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
    Handler<Buffer> handler = pingHandler;
    if (handler != null) {
      Buffer buff = Buffer.buffer().appendLong(data);
      context.dispatch(v -> handler.handle(buff));
    }
  }

  @Override
  public void onPingAckRead(ChannelHandlerContext ctx, long data) {
    Promise<Buffer> handler = pongHandlers.poll();
    if (handler != null) {
      Buffer buff = Buffer.buffer().appendLong(data);
      handler.complete(buff);
    }
  }

  @Override
  public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                Http2Headers headers, int padding) throws Http2Exception {
  }

  @Override
  public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
  }

  @Override
  public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
  }

  @Override
  public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                             Http2Flags flags, ByteBuf payload) {
    VertxHttp2Stream stream = stream(streamId);
    if (stream != null) {
      Buffer buff = Buffer.buffer(safeBuffer(payload, ctx.alloc()));
      stream.onCustomFrame(new HttpFrameImpl(frameType, flags.value(), buff));
    }
  }

  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    VertxHttp2Stream stream = stream(streamId);
    if (stream != null) {
      stream.onReset(errorCode);
    }
  }

  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
    VertxHttp2Stream stream = stream(streamId);
    if (stream != null) {
      data = safeBuffer(data, ctx.alloc());
      Buffer buff = Buffer.buffer(data);
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
    try {
      Http2Stream stream = handler.encoder().connection().connectionStream();
      int delta = windowSize - this.windowSize;
      handler.decoder().flowController().incrementWindowSize(stream, delta);
      this.windowSize = windowSize;
      return this;
    } catch (Http2Exception e) {
      throw new VertxException(e);
    }
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    if (errorCode < 0) {
      throw new IllegalArgumentException();
    }
    if (lastStreamId < 0) {
      lastStreamId = handler.connection().remote().lastStreamCreated();
    }
    handler.writeGoAway(errorCode, lastStreamId, debugData != null ? debugData.getByteBuf() : Unpooled.EMPTY_BUFFER);
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
  public void shutdown(long timeout, Handler<AsyncResult<Void>> handler) {
    shutdown(timeout, vertx.promise(handler));
  }

  @Override
  public Future<Void> shutdown(long timeoutMs) {
    PromiseInternal<Void> promise = vertx.promise();
    shutdown(timeoutMs, promise);
    return promise.future();
  }

  private void shutdown(long timeout, PromiseInternal<Void> promise) {
    if (timeout < 0) {
      promise.fail("Invalid timeout value " + timeout);
      return;
    }
    handler.gracefulShutdownTimeoutMillis(timeout);
    ChannelFuture fut = channel().close();
    fut.addListener(promise);
  }

  @Override
  public Http2ConnectionBase closeHandler(Handler<Void> handler) {
    return (Http2ConnectionBase) super.closeHandler(handler);
  }

  @Override
  public Future<Void> close() {
    PromiseInternal<Void> promise = context.promise();
    ChannelPromise pr = chctx.newPromise();
    ChannelPromise channelPromise = pr.addListener(promise);
    handlerContext.writeAndFlush(Unpooled.EMPTY_BUFFER, pr);
    channelPromise.addListener((ChannelFutureListener) future -> shutdown(0L));
    return promise.future();
  }

  @Override
  public synchronized HttpConnection remoteSettingsHandler(Handler<io.vertx.core.http.Http2Settings> handler) {
    remoteSettingsHandler = handler;
    return this;
  }

  @Override
  public synchronized io.vertx.core.http.Http2Settings remoteSettings() {
    return HttpUtils.toVertxSettings(remoteSettings);
  }

  @Override
  public synchronized io.vertx.core.http.Http2Settings settings() {
    return HttpUtils.toVertxSettings(localSettings);
  }

  @Override
  public Future<Void> updateSettings(io.vertx.core.http.Http2Settings settings) {
    Promise<Void> promise = context.promise();
    Http2Settings settingsUpdate = HttpUtils.fromVertxSettings(settings);
    updateSettings(settingsUpdate, promise);
    return promise.future();
  }

  @Override
  public HttpConnection updateSettings(io.vertx.core.http.Http2Settings settings, @Nullable Handler<AsyncResult<Void>> completionHandler) {
    updateSettings(settings).onComplete(completionHandler);
    return this;
  }

  protected void updateSettings(Http2Settings settingsUpdate, Handler<AsyncResult<Void>> completionHandler) {
    Http2Settings current = handler.decoder().localSettings();
    for (Map.Entry<Character, Long> entry : current.entrySet()) {
      Character key = entry.getKey();
      if (Objects.equals(settingsUpdate.get(key), entry.getValue())) {
        settingsUpdate.remove(key);
      }
    }
    Handler<Void> pending = v -> {
      synchronized (Http2ConnectionBase.this) {
        localSettings.putAll(settingsUpdate);
      }
      if (completionHandler != null) {
        completionHandler.handle(Future.succeededFuture());
      }
    };
    updateSettingsHandlers.add(pending);
    handler.writeSettings(settingsUpdate).addListener(fut -> {
      if (!fut.isSuccess()) {
        synchronized (Http2ConnectionBase.this) {
          updateSettingsHandlers.remove(pending);
        }
        if (completionHandler != null) {
          completionHandler.handle(Future.failedFuture(fut.cause()));
        }
      }
    });
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    if (data.length() != 8) {
      throw new IllegalArgumentException("Ping data must be exactly 8 bytes");
    }
    Promise<Buffer> promise = context.promise();
    handler.writePing(data.getLong(0)).addListener(fut -> {
      if (fut.isSuccess()) {
        synchronized (Http2ConnectionBase.this) {
          pongHandlers.add(promise);
        }
      } else {
        promise.fail(fut.cause());
      }
    });
    return promise.future();
  }

  @Override
  public HttpConnection ping(Buffer data, Handler<AsyncResult<Buffer>> pongHandler) {
    Future<Buffer> fut = ping(data);
    if (pongHandler != null) {
      fut.onComplete(pongHandler);
    }
    return this;
  }

  @Override
  public synchronized HttpConnection pingHandler(Handler<Buffer> handler) {
    pingHandler = handler;
    return this;
  }

  // Necessary to set the covariant return type
  @Override
  public Http2ConnectionBase exceptionHandler(Handler<Throwable> handler) {
    return (Http2ConnectionBase) super.exceptionHandler(handler);
  }

  void consumeCredits(Http2Stream stream, int numBytes) {
    this.handler.consume(stream, numBytes);
  }

  // Private

  private void checkShutdown() {
    Handler<Void> shutdownHandler;
    synchronized (this) {
      if (shutdown) {
        return;
      }
      Http2Connection conn = handler.connection();
      if ((!conn.goAwayReceived() && !conn.goAwaySent()) || conn.numActiveStreams() > 0) {
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
