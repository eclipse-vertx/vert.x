/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Http2ConnectionBase extends ConnectionBase implements Http2FrameListener, HttpConnection {

  /**
   * Return a buffer from HTTP/2 codec that Vert.x can use:
   *
   * - if it's a direct buffer (coming likely from OpenSSL) : we get a heap buffer version
   * - if it's a composite buffer we do the same
   * - otherwise we increase the ref count
   */
  static ByteBuf safeBuffer(ByteBuf buf, ByteBufAllocator allocator) {
    if (buf == Unpooled.EMPTY_BUFFER) {
      return buf;
    }
    if (buf.isDirect() || buf instanceof CompositeByteBuf) {
      if (buf.isReadable()) {
        ByteBuf buffer =  allocator.heapBuffer(buf.readableBytes());
        buffer.writeBytes(buf);
        return buffer;
      } else {
        return Unpooled.EMPTY_BUFFER;
      }
    }
    return buf.retain();
  }

  protected final IntObjectMap<VertxHttp2Stream> streams = new IntObjectHashMap<>();
  protected final ChannelHandlerContext handlerContext;
  protected final VertxHttp2ConnectionHandler handler;
  private boolean shutdown;
  private Handler<io.vertx.core.http.Http2Settings> remoteSettingsHandler;
  private final ArrayDeque<Handler<Void>> updateSettingsHandlers = new ArrayDeque<>();
  private final ArrayDeque<Handler<AsyncResult<Buffer>>> pongHandlers = new ArrayDeque<>();
  private Http2Settings localSettings = new Http2Settings();
  private Http2Settings remoteSettings;
  private Handler<GoAway> goAwayHandler;
  private Handler<Void> shutdownHandler;
  private Handler<Buffer> pingHandler;
  private boolean closed;
  private boolean goneAway;
  private int windowSize;
  private long maxConcurrentStreams;

  public Http2ConnectionBase(ContextInternal context, VertxHttp2ConnectionHandler handler) {
    super(context.owner(), handler.context(), context);
    this.handler = handler;
    this.handlerContext = chctx;
    this.windowSize = handler.connection().local().flowController().windowSize(handler.connection().connectionStream());
    this.maxConcurrentStreams = io.vertx.core.http.Http2Settings.DEFAULT_MAX_CONCURRENT_STREAMS;
  }

  VertxInternal vertx() {
    return vertx;
  }

  NetSocket toNetSocket(VertxHttp2Stream stream) {
    VertxHttp2NetSocket<Http2ConnectionBase> rempl = new VertxHttp2NetSocket<>(this, stream.context, stream.stream, !stream.isNotWritable());
    streams.put(stream.stream.id(), rempl);
    return rempl;
  }

  @Override
  public void handleClosed() {
    synchronized (this) {
      closed = true;
    }
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

  synchronized boolean isClosed() {
    return closed;
  }

  synchronized void onConnectionError(Throwable cause) {
    ArrayList<VertxHttp2Stream> copy;
    synchronized (this) {
      copy = new ArrayList<>(streams.values());
    }
    for (VertxHttp2Stream stream : copy) {
      stream.handleException(cause);
    }
    handleException(cause);
  }

  void onStreamError(int streamId, Throwable cause) {
    VertxHttp2Stream stream;
    synchronized (this) {
      stream = streams.get(streamId);
    }
    if (stream != null) {
      stream.handleException(cause);
    }
  }

  void onStreamWritabilityChanged(Http2Stream s) {
    VertxHttp2Stream stream;
    synchronized (this) {
      stream = streams.get(s.id());
    }
    if (stream != null) {
      context.schedule(null, v -> stream.onWritabilityChanged());
    }
  }

  void onStreamClosed(Http2Stream stream) {
    VertxHttp2Stream removed;
    synchronized (this) {
      removed = streams.remove(stream.id());
      if (removed == null) {
        return;
      }
    }
    removed.handleClose();
    checkShutdownHandler();
  }

  boolean onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    synchronized (this) {
      if (goneAway) {
        return false;
      }
      goneAway = true;
    }
    checkShutdownHandler();
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
      context.dispatch(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(buffer), handler);
    }
    checkShutdownHandler();
    return true;
  }

  // Http2FrameListener

  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) {
      VertxHttp2Stream stream;
      synchronized (this) {
        stream = streams.get(streamId);
      }
      if (stream != null) {
        StreamPriority streamPriority = new StreamPriority()
          .setDependency(streamDependency)
          .setWeight(weight)
          .setExclusive(exclusive);
        stream.handlePriorityChange(streamPriority);
      }
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
    onHeadersRead(ctx, streamId, headers, padding, endOfStream);
  }

  @Override
  public void onSettingsAckRead(ChannelHandlerContext ctx) {
    Handler<Void> handler;
    synchronized (this) {
      handler = updateSettingsHandlers.poll();
    }
    if (handler != null) {
      // No need to run on a particular context it shall be done by the handler instead
      context.dispatch(handler);
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
      context.dispatch(buff, handler);
    }
  }

  @Override
  public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
    Handler<AsyncResult<Buffer>> handler = pongHandlers.poll();
    if (handler != null) {
      Buffer buff = Buffer.buffer().appendLong(data);
      context.dispatch(Future.succeededFuture(buff), handler);
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
    VertxHttp2Stream req;
    synchronized (this) {
      req = streams.get(streamId);
    }
    if (req != null) {
      Buffer buff = Buffer.buffer(safeBuffer(payload, ctx.alloc()));
      req.handleCustomFrame(frameType, flags.value(), buff);
    }
  }

  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    VertxHttp2Stream req;
    synchronized (this) {
      req = streams.get(streamId);
      if (req == null) {
        return;
      }
    }
    req.onResetRead(errorCode);
  }

  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
    int[] consumed = { padding };
    VertxHttp2Stream req;
    synchronized (this) {
      req = streams.get(streamId);
    }
    if (req != null) {
      data = safeBuffer(data, ctx.alloc());
      Buffer buff = Buffer.buffer(data);
      int len = buff.length();
      if (req.onDataRead(buff)) {
        consumed[0] += len;
      }
      if (endOfStream) {
        req.onEnd();
      }
    }
    return consumed[0];
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
  public HttpConnection shutdown(long timeout) {
    if (timeout < 0) {
      throw new IllegalArgumentException("Invalid timeout value " + timeout);
    }
    handler.gracefulShutdownTimeoutMillis(timeout);
    channel().close();
    return this;
  }

  @Override
  public HttpConnection shutdown() {
    return shutdown(30000);
  }

  @Override
  public Http2ConnectionBase closeHandler(Handler<Void> handler) {
    return (Http2ConnectionBase) super.closeHandler(handler);
  }

  @Override
  public void close() {
    ChannelPromise promise = chctx.newPromise();
    flush(promise);
    promise.addListener((ChannelFutureListener) future -> shutdown(0L));
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
  public HttpConnection updateSettings(io.vertx.core.http.Http2Settings settings) {
    return updateSettings(settings, null);
  }

  @Override
  public HttpConnection updateSettings(io.vertx.core.http.Http2Settings settings, @Nullable Handler<AsyncResult<Void>> completionHandler) {
    Http2Settings settingsUpdate = HttpUtils.fromVertxSettings(settings);
    updateSettings(settingsUpdate, completionHandler);
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
  public HttpConnection ping(Buffer data, Handler<AsyncResult<Buffer>> pongHandler) {
    if (data.length() != 8) {
      throw new IllegalArgumentException("Ping data must be exactly 8 bytes");
    }
    handler.writePing(data.getLong(0)).addListener(fut -> {
      if (fut.isSuccess()) {
        synchronized (Http2ConnectionBase.this) {
          pongHandlers.add(pongHandler);
        }
      } else {
        pongHandler.handle(Future.failedFuture(fut.cause()));
      }
    });
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

  // Private

  private void checkShutdownHandler() {
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
    if (shutdownHandler != null) {
      context.dispatch(shutdownHandler);
    }
  }
}
