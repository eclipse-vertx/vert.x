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
import io.netty.channel.ChannelHandlerContext;
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
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.ArrayDeque;
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
  private final ArrayDeque<Runnable> updateSettingsHandlers = new ArrayDeque<>(4);
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
    VertxHttp2NetSocket<Http2ConnectionBase> rempl = new VertxHttp2NetSocket<>(this, stream.stream, !stream.isNotWritable());
    streams.put(stream.stream.id(), rempl);
    return rempl;
  }

  @Override
  public synchronized void handleClosed() {
    closed = true;
    super.handleClosed();
  }

  @Override
  protected void handleInterestedOpsChanged() {
    // Handled by HTTP/2
  }

  synchronized boolean isClosed() {
    return closed;
  }

  synchronized void onConnectionError(Throwable cause) {
    synchronized (this) {
      for (VertxHttp2Stream stream : streams.values()) {
        context.runOnContext(v -> {
          synchronized (Http2ConnectionBase.this) {
            stream.handleException(cause);
          }
        });
      }
      handleException(cause);
    }
  }

  synchronized void onStreamError(int streamId, Throwable cause) {
    VertxHttp2Stream stream = streams.get(streamId);
    if (stream != null) {
      stream.handleException(cause);
    }
  }

  synchronized void onStreamwritabilityChanged(Http2Stream s) {
    VertxHttp2Stream stream = streams.get(s.id());
    if (stream != null) {
      context.executeFromIO(v -> stream.onWritabilityChanged());
    }
  }

  synchronized void onStreamClosed(Http2Stream stream) {
    checkShutdownHandler();
    VertxHttp2Stream removed = streams.remove(stream.id());
    if (removed != null) {
      context.executeFromIO(v -> removed.handleClose());
    }
  }

  synchronized void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    if (!goneAway) {
      goneAway = true;
      checkShutdownHandler();
    }
  }

  synchronized void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    if (!goneAway) {
      goneAway = true;
      Handler<GoAway> handler = goAwayHandler;
      if (handler != null) {
        Buffer buffer = Buffer.buffer(debugData);
        context.executeFromIO(v -> handler.handle(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(buffer)));
      }
      checkShutdownHandler();
    }
  }

  // Http2FrameListener

  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                             short weight, boolean exclusive) {
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
    onHeadersRead(ctx, streamId, headers, padding, endOfStream);
  }

  @Override
  public synchronized void onSettingsAckRead(ChannelHandlerContext ctx) {
    Runnable handler = updateSettingsHandlers.poll();
    if (handler != null) {
      // No need to run on a particular context it shall be done by the handler instead
      handler.run();
    }
  }

  protected void concurrencyChanged(long concurrency) {
  }

  @Override
  public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
    boolean changed;
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
    synchronized (this) {
      Handler<io.vertx.core.http.Http2Settings> handler = remoteSettingsHandler;
      if (handler != null) {
        context.executeFromIO(v -> handler.handle(HttpUtils.toVertxSettings(settings)));
      }
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
      context.executeFromIO(v -> handler.handle(buff));
    }
  }

  @Override
  public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
    Handler<AsyncResult<Buffer>> handler = pongHandlers.poll();
    if (handler != null) {
      context.executeFromIO(v -> {
        Buffer buff = Buffer.buffer().appendLong(data);
        handler.handle(Future.succeededFuture(buff));
      });
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
  public synchronized void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                             Http2Flags flags, ByteBuf payload) {
    VertxHttp2Stream req = streams.get(streamId);
    if (req != null) {
      Buffer buff = Buffer.buffer(safeBuffer(payload, ctx.alloc()));
      context.executeFromIO(v -> req.handleCustomFrame(frameType, flags.value(), buff));
    }
  }

  @Override
  public synchronized void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    VertxHttp2Stream req = streams.get(streamId);
    if (req != null) {
      context.executeFromIO(v -> req.onResetRead(errorCode));
    }
  }

  @Override
  public synchronized int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
    int[] consumed = { padding };
    VertxHttp2Stream req = streams.get(streamId);
    if (req != null) {
      data = safeBuffer(data, ctx.alloc());
      Buffer buff = Buffer.buffer(data);
      context.executeFromIO(v -> {
        int len = buff.length();
        if (req.onDataRead(buff)) {
          consumed[0] += len;
        }
      });
      if (endOfStream) {
        context.executeFromIO(v -> req.onEnd());
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
  public synchronized HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
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
  public synchronized HttpConnection shutdown(long timeout) {
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
    endReadAndFlush();
    shutdown(0L);
  }

  @Override
  public synchronized HttpConnection remoteSettingsHandler(Handler<io.vertx.core.http.Http2Settings> handler) {
    remoteSettingsHandler = handler;
    return this;
  }

  @Override
  public synchronized io.vertx.core.http.Http2Settings remoteSettings() {
    /*
    io.vertx.core.http.Http2Settings a = new io.vertx.core.http.Http2Settings();
    a.setPushEnabled(handler.connection().remote().allowPushTo());
    a.setMaxConcurrentStreams((long) handler.connection().local().maxActiveStreams());
    a.setMaxHeaderListSize(handler.encoder().configuration().headersConfiguration().maxHeaderListSize());
    a.setHeaderTableSize(handler.encoder().configuration().headersConfiguration().maxHeaderTableSize());
    a.setMaxFrameSize(handler.encoder().configuration().frameSizePolicy().maxFrameSize());
    a.setInitialWindowSize(handler.encoder().flowController().initialWindowSize());
    return a;
    */
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

  protected synchronized void updateSettings(Http2Settings settingsUpdate, Handler<AsyncResult<Void>> completionHandler) {
    Context completionContext = completionHandler != null ? context.owner().getOrCreateContext() : null;
    Http2Settings current = handler.decoder().localSettings();
    for (Map.Entry<Character, Long> entry : current.entrySet()) {
      Character key = entry.getKey();
      if (Objects.equals(settingsUpdate.get(key), entry.getValue())) {
        settingsUpdate.remove(key);
      }
    }
    Runnable pending = () -> {
      localSettings.putAll(settingsUpdate);
      if (completionHandler != null) {
        completionContext.runOnContext(v -> {
          completionHandler.handle(Future.succeededFuture());
        });
      }
    };
    updateSettingsHandlers.add(pending);
    handler.writeSettings(settingsUpdate).addListener(fut -> {
      if (!fut.isSuccess()) {
        synchronized (Http2ConnectionBase.this) {
          updateSettingsHandlers.remove(pending);
        }
        if (completionHandler != null) {
          completionContext.runOnContext(v -> {
            completionHandler.handle(Future.failedFuture(fut.cause()));
          });
        }
      }
    });
  }

  @Override
  public synchronized HttpConnection ping(Buffer data, Handler<AsyncResult<Buffer>> pongHandler) {
    if (data.length() != 8) {
      throw new IllegalArgumentException("Ping data must be exactly 8 bytes");
    }
    handler.writePing(data.getLong(0)).addListener(fut -> {
      if (fut.isSuccess()) {
        pongHandlers.add(pongHandler);
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

  @Override
  public synchronized Http2ConnectionBase exceptionHandler(Handler<Throwable> handler) {
    return (Http2ConnectionBase) super.exceptionHandler(handler);
  }

  // Private

  private void checkShutdownHandler() {
    if (!shutdown) {
      Http2Connection conn = handler.connection();
      if ((conn.goAwayReceived() || conn.goAwaySent()) && conn.numActiveStreams() == 0) {
        shutdown  = true;
        Handler<Void> handler = shutdownHandler;
        if (handler != null) {
          context.executeFromIO(handler);
        }
      }
    }
  }
}
