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

import io.netty.buffer.*;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import io.vertx.core.http.impl.http2.Http2StreamBase;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Http2ConnectionImpl extends ConnectionBase implements Http2FrameListener, HttpConnection, io.vertx.core.http.impl.http2.Http2Connection {

  private static final Logger log = LoggerFactory.getLogger(Http2ConnectionImpl.class);

  private static ByteBuf safeBuffer(ByteBuf buf) {
    ByteBuf buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(buf.readableBytes());
    buffer.writeBytes(buf);
    return buffer;
  }

  protected final ChannelHandlerContext handlerContext;
  private final VertxHttp2ConnectionHandler handler;
  protected final Http2Connection.PropertyKey streamKey;
  private boolean shutdown;
  private Handler<io.vertx.core.http.Http2Settings> remoteSettingsHandler;
  private final ArrayDeque<Handler<Void>> updateSettingsHandlers = new ArrayDeque<>();
  private final ArrayDeque<Promise<Buffer>> pongHandlers = new ArrayDeque<>();
  private Http2Settings localSettings;
  private Http2Settings remoteSettings;
  private Handler<GoAway> goAwayHandler;
  private Handler<Void> shutdownHandler;
  private Handler<Buffer> pingHandler;
  private GoAway goAwayStatus;
  private int windowSize;
  private long maxConcurrentStreams;

  public Http2ConnectionImpl(ContextInternal context, VertxHttp2ConnectionHandler handler) {
    super(context, handler.context());
    this.handler = handler;
    this.handlerContext = chctx;
    this.windowSize = handler.connection().local().flowController().windowSize(handler.connection().connectionStream());
    this.maxConcurrentStreams = io.vertx.core.http.Http2Settings.DEFAULT_MAX_CONCURRENT_STREAMS;
    this.streamKey = handler.connection().newKey();
    this.localSettings = handler.initialSettings();
  }

  public Http2HeadersMultiMap newHeaders() {
    return new Http2HeadersMultiMap(new DefaultHttp2Headers());
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
    ArrayList<Http2StreamBase> streams = new ArrayList<>();
    try {
      handler.connection().forEachActiveStream(stream -> {
        streams.add(stream.getProperty(streamKey));
        return true;
      });
    } catch (Http2Exception e) {
      log.error("Could not get the list of active streams", e);
    }
    for (Http2StreamBase stream : streams) {
      stream.context().dispatch(v -> stream.handleException(cause));
    }
    handleException(cause);
  }

  public Http2StreamBase stream(int id) {
    Http2Stream s = handler.connection().stream(id);
    if (s == null) {
      return null;
    }
    return s.getProperty(streamKey);
  }

  void onStreamError(int streamId, Throwable cause) {
    Http2StreamBase stream = stream(streamId);
    if (stream != null) {
      stream.onException(cause);
    }
  }

  void onStreamWritabilityChanged(Http2Stream s) {
    Http2StreamBase stream = s.getProperty(streamKey);
    if (stream != null) {
      stream.onWritabilityChanged();
    }
  }

  void onStreamClosed(Http2Stream s) {
    Http2StreamBase stream = s.getProperty(streamKey);
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

  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) {
      Http2StreamBase stream = stream(streamId);
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
    if (goAwayStatus == null || endOfStream) {
      StreamPriority streamPriority = new StreamPriority()
        .setDependency(streamDependency)
        .setWeight(weight)
        .setExclusive(exclusive);
      onHeadersRead(streamId, headers, streamPriority, endOfStream);
    }
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
    if (goAwayStatus == null || endOfStream) {
      onHeadersRead(streamId, headers, null, endOfStream);
    }
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
    Http2StreamBase stream = stream(streamId);
    if (stream != null) {
      Buffer buff = BufferInternal.buffer(safeBuffer(payload));
      stream.onCustomFrame(frameType, flags.value(), buff);
    }
  }

  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    Http2StreamBase stream = stream(streamId);
    if (stream != null) {
      stream.onReset(errorCode);
    }
  }

  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
    Http2StreamBase stream = stream(streamId);
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
    if (windowSize <= 0) {
      throw new IllegalArgumentException("Invalid window size: " + windowSize);
    }
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
    ChannelFuture fut = channel.close();
    fut.addListener(promise);
  }

  @Override
  public Http2ConnectionImpl closeHandler(Handler<Void> handler) {
    return (Http2ConnectionImpl) super.closeHandler(handler);
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
    return updateSettings(HttpUtils.fromVertxSettings(settings));
  }

  protected Future<Void> updateSettings(Http2Settings settingsUpdate) {
    Http2Settings current = handler.decoder().localSettings();
    for (Map.Entry<Character, Long> entry : current.entrySet()) {
      Character key = entry.getKey();
      if (Objects.equals(settingsUpdate.get(key), entry.getValue())) {
        settingsUpdate.remove(key);
      }
    }
    Promise<Void> promise = context.promise();
    Handler<Void> pending = v -> {
      synchronized (Http2ConnectionImpl.this) {
        localSettings.putAll(settingsUpdate);
      }
      promise.complete();
    };
    updateSettingsHandlers.add(pending);
    handler.writeSettings(settingsUpdate).addListener(fut -> {
      if (!fut.isSuccess()) {
        synchronized (Http2ConnectionImpl.this) {
          updateSettingsHandlers.remove(pending);
        }
        promise.fail(fut.cause());
      }
    });
    return promise.future();
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    if (data.length() != 8) {
      throw new IllegalArgumentException("Ping data must be exactly 8 bytes");
    }
    Promise<Buffer> promise = context.promise();
    handler.writePing(data.getLong(0)).addListener(fut -> {
      if (fut.isSuccess()) {
        synchronized (Http2ConnectionImpl.this) {
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
  public Http2ConnectionImpl exceptionHandler(Handler<Throwable> handler) {
    return (Http2ConnectionImpl) super.exceptionHandler(handler);
  }

  @Override
  public void consumeCredits(int streamId, int amountOfBytes) {
    Http2Stream s = handler.connection().stream(streamId);
    if (s != null && s.state().remoteSideOpen()) {
      // Handle the HTTP upgrade case
      // buffers are received by HTTP/1 and not accounted by HTTP/2
      this.handler.consume(s, amountOfBytes);
    }
  }

  @Override
  public boolean supportsSendFile() {
    return false;
  }

  @Override
  public void sendFile(int streamId, ChunkedInput<ByteBuf> file, Promise<Void> promise) {
    promise.fail("Send file not supported");
  }

  public boolean isWritable(int streamId) {
    Http2Stream s = handler.connection().stream(streamId);
    return this.handler.encoder().flowController().isWritable(s);
  }

  public boolean isWritable(Http2StreamBase stream) {
    Http2Stream s = handler.connection().stream(stream.id());
    return this.handler.encoder().flowController().isWritable(s);
  }

  @Override
  public void writeFrame(int streamId, int type, int flags, ByteBuf payload, Promise<Void> promise) {
    Http2Stream s = handler.connection().stream(streamId);
    handler.writeFrame(s, (byte) type, (short) flags, payload, (FutureListener<Void>) promise);
  }

  @Override
  public void writePriorityFrame(int streamId, StreamPriority priority) {
    Http2Stream s = handler.connection().stream(streamId);
    handler.writePriority(s, priority.getDependency(), priority.getWeight(), priority.isExclusive());
  }

  @Override
  public void writeHeaders(int streamId, Http2HeadersMultiMap headers, StreamPriority priority, boolean end, boolean checkFlush, Promise<Void> promise) {
    Http2Stream s = handler.connection().stream(streamId);
    handler.writeHeaders(s, (Http2Headers) headers.prepare().unwrap(), end, priority.getDependency(), priority.getWeight(), priority.isExclusive(), checkFlush, (FutureListener<Void>) promise);
  }

  @Override
  public void writeData(int streamId, ByteBuf buf, boolean end, Promise<Void> promise) {
    Http2Stream s = handler.connection().stream(streamId);
    handler.writeData(s, buf, end, (FutureListener<Void>) promise);
  }

  @Override
  public void writeReset(int streamId, long code, Promise<Void> promise) {
    handler.writeReset(streamId, code, null);
  }
}
