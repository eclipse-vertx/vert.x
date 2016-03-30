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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Http2ConnectionBase extends ConnectionBase implements Http2FrameListener, HttpConnection {

  protected final IntObjectMap<VertxHttp2Stream> streams = new IntObjectHashMap<>();
  protected ChannelHandlerContext handlerContext;
  protected final Channel channel;
  protected final VertxHttp2ConnectionHandler handler;
  private boolean shutdown;
  private Handler<io.vertx.core.http.Http2Settings> clientSettingsHandler;
  private final ArrayDeque<Runnable> updateSettingsHandlers = new ArrayDeque<>(4);
  private final ArrayDeque<Handler<AsyncResult<Buffer>>> pongHandlers = new ArrayDeque<>();
  private Http2Settings serverSettings = new Http2Settings();
  private Handler<GoAway> goAwayHandler;
  private Handler<Void> shutdownHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Buffer> pingHandler;
  private boolean closed;

  public Http2ConnectionBase(Channel channel, ContextImpl context, VertxHttp2ConnectionHandler handler, TCPMetrics metrics) {
    super((VertxInternal) context.owner(), channel, context, metrics);
    this.channel = channel;
    this.handlerContext = channel.pipeline().context(handler);
    this.handler = handler;
  }

  void setHandlerContext(ChannelHandlerContext handlerContext) {
    this.handlerContext = handlerContext;
  }

  VertxInternal vertx() {
    return vertx;
  }

  NetSocket toNetSocket(VertxHttp2Stream stream) {
    VertxHttp2NetSocket<Http2ConnectionBase> rempl = new VertxHttp2NetSocket<>(this, stream.stream);
    streams.put(stream.stream.id(), rempl);
    return rempl;
  }

  @Override
  public synchronized void handleClosed() {
    super.handleClosed();
  }

  @Override
  public ContextImpl getContext() {
    return super.getContext();
  }

  @Override
  protected void handleInterestedOpsChanged() {
    // Handled by HTTP/2
  }

  boolean isSsl() {
    return channel.pipeline().get(SslHandler.class) != null;
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
      Handler<Throwable> handler = exceptionHandler;
      if (handler != null) {
        handler.handle(cause);
      }
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
      context.executeFromIO(stream::onWritabilityChanged);
    }
  }

  synchronized void onStreamClosed(Http2Stream stream) {
    checkShutdownHandler();
    VertxHttp2Stream removed = streams.remove(stream.id());
    if (removed != null) {
      context.executeFromIO(() -> {
        removed.handleClose();
      });
    }
  }

  void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
  }

  synchronized void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    Handler<GoAway> handler = goAwayHandler;
    if (handler != null) {
      Buffer buffer = Buffer.buffer(debugData);
      context.executeFromIO(() -> {
        handler.handle(new GoAway().setErrorCode(errorCode).setLastStreamId(lastStreamId).setDebugData(buffer));
      });
    }
    checkShutdownHandler();
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

  @Override
  public synchronized void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
    Handler<io.vertx.core.http.Http2Settings> handler = clientSettingsHandler;
    if (handler != null) {
      context.executeFromIO(() -> {
        handler.handle(HttpUtils.toVertxSettings(settings));
      });
    }
  }

  @Override
  public synchronized void onPingRead(ChannelHandlerContext ctx, ByteBuf data) {
    Handler<Buffer> handler = pingHandler;
    if (handler != null) {
      Buffer buff = Buffer.buffer(data.copy());
      context.executeFromIO(() -> {
        handler.handle(buff);
      });
    }
  }

  @Override
  public synchronized void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) {
    Handler<AsyncResult<Buffer>> handler = pongHandlers.poll();
    if (handler != null) {
      context.executeFromIO(() -> {
        Buffer buff = Buffer.buffer(data.copy());
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
      Buffer buff = Buffer.buffer(payload.copy());
      context.executeFromIO(() -> {
        req.handleUnknownFrame(frameType, flags.value(), buff);
      });
    }
  }

  @Override
  public synchronized void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    VertxHttp2Stream req = streams.get(streamId);
    if (req != null) {
      context.executeFromIO(() -> {
        req.onResetRead(errorCode);
      });
    }
  }

  @Override
  public synchronized int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
    int[] consumed = { padding };
    VertxHttp2Stream req = streams.get(streamId);
    if (req != null) {
      Buffer buff = Buffer.buffer(data.copy());
      context.executeFromIO(() -> {
        int len = buff.length();
        if (req.onDataRead(buff)) {
          consumed[0] += len;
        }
      });
      if (endOfStream) {
        context.executeFromIO(req::onEnd);
      }
    }
    return consumed[0];
  }

  @Override
  public synchronized HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    if (errorCode < 0) {
      throw new IllegalArgumentException();
    }
    if (lastStreamId < 0) {
      throw new IllegalArgumentException();
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
    channel.close();
    return this;
  }

  @Override
  public HttpConnection shutdown() {
    return shutdown(30000);
  }

  @Override
  public synchronized HttpConnection closeHandler(Handler<Void> handler) {
    closed = true;
    closeHandler = handler;
    return this;
  }

  @Override
  public void close() {
    endReadAndFlush();
    shutdown(0L);
  }

  @Override
  public synchronized HttpConnection remoteSettingsHandler(Handler<io.vertx.core.http.Http2Settings> handler) {
    clientSettingsHandler = handler;
    return this;
  }

  @Override
  public synchronized Handler<io.vertx.core.http.Http2Settings> remoteSettingsHandler() {
    return clientSettingsHandler;
  }

  @Override
  public synchronized io.vertx.core.http.Http2Settings remoteSettings() {
    io.vertx.core.http.Http2Settings a = new io.vertx.core.http.Http2Settings();
    a.setPushEnabled(handler.connection().remote().allowPushTo());
    a.setMaxConcurrentStreams((long) handler.connection().local().maxActiveStreams());
    a.setMaxHeaderListSize(handler.encoder().configuration().headerTable().maxHeaderListSize());
    a.setHeaderTableSize(handler.encoder().configuration().headerTable().maxHeaderTableSize());
    a.setMaxFrameSize(handler.encoder().configuration().frameSizePolicy().maxFrameSize());
    a.setInitialWindowSize(handler.encoder().flowController().initialWindowSize());
    return a;
  }

  @Override
  public synchronized io.vertx.core.http.Http2Settings settings() {
    return HttpUtils.toVertxSettings(serverSettings);
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
    handler.writeSettings(settingsUpdate).addListener(fut -> {
      if (fut.isSuccess()) {
        synchronized (Http2ConnectionBase.this) {
          updateSettingsHandlers.add(() -> {
            serverSettings.putAll(settingsUpdate);
            if (completionHandler != null) {
              completionContext.runOnContext(v -> {
                completionHandler.handle(Future.succeededFuture());
              });
            }
          });
        }
      } else {
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
    handler.writePing(data.getByteBuf()).addListener(fut -> {
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
  public synchronized HttpConnection exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public synchronized Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  // Private

  private void checkShutdownHandler() {
    if (!shutdown) {
      Http2Connection conn = handler.connection();
      if ((conn.goAwayReceived() || conn.goAwaySent()) && conn.numActiveStreams() == 0) {
        shutdown  = true;
        Handler<Void> handler = shutdownHandler;
        if (handler != null) {
          context.executeFromIO(() -> {
            shutdownHandler.handle(null);
          });
        }
      }
    }
  }
}
