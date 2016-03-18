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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.NetworkMetrics;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Http2ConnectionBase extends ConnectionBase implements Http2FrameListener, HttpConnection, Http2Connection.Listener {

  protected final IntObjectMap<VertxHttp2Stream> streams = new IntObjectHashMap<>();
  protected ChannelHandlerContext handlerContext;
  protected final Channel channel;
  protected final VertxHttp2ConnectionHandler connHandler;
  private boolean shuttingdown;
  private boolean shutdown;
  private Handler<io.vertx.core.http.Http2Settings> clientSettingsHandler;
  private final ArrayDeque<Runnable> updateSettingsHandler = new ArrayDeque<>(4);
  private Http2Settings serverSettings = new Http2Settings();
  private Handler<GoAway> goAwayHandler;
  private Handler<Void> shutdownHandler;
  private Handler<Throwable> exceptionHandler;

  public Http2ConnectionBase(Channel channel, ContextImpl context, VertxHttp2ConnectionHandler connHandler) {
    super((VertxInternal) context.owner(), channel, context, (NetworkMetrics) null);

    connHandler.connection().addListener(this);

    connHandler.encoder().flowController().listener(stream -> {
      VertxHttp2Stream resp = streams.get(stream.id());
      if (resp != null) {
        resp.handleInterestedOpsChanged();
      }
    });

    this.channel = channel;
    this.handlerContext = channel.pipeline().context(connHandler);
    this.connHandler = connHandler;
  }

  void setHandlerContext(ChannelHandlerContext handlerContext) {
    this.handlerContext = handlerContext;
  }

  NetSocket toNetSocket(VertxHttp2Stream stream) {
    VertxHttp2NetSocket rempl = new VertxHttp2NetSocket(stream.vertx, stream.context, stream.handlerContext, this, stream.encoder, stream.decoder, stream.stream);
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
  protected Object metric() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void handleInterestedOpsChanged() {
    throw new UnsupportedOperationException();
  }

  void handleConnectionError(Throwable cause) {
    for (VertxHttp2Stream stream : streams.values()) {
      context.executeFromIO(() -> {
        stream.handleException(cause);
      });
    }
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      context.executeFromIO(() -> {
        handler.handle(cause);
      });
    }
  }

  void handleStreamError(int streamId, Throwable cause) {
    VertxHttp2Stream stream = streams.get(streamId);
    if (stream != null) {
      stream.handleException(cause);
    }
  }
  // Http2Connection.Listener

  @Override
  public void onStreamClosed(Http2Stream stream) {
    checkShutdownHandler();
    VertxHttp2Stream removed = streams.remove(stream.id());
    if (removed != null) {
      context.executeFromIO(() -> {
        removed.handleClose();
      });
    }
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
  public void onPriorityTreeParentChanged(Http2Stream stream, Http2Stream oldParent) {
  }

  @Override
  public void onPriorityTreeParentChanging(Http2Stream stream, Http2Stream newParent) {
  }

  @Override
  public void onWeightChanged(Http2Stream stream, short oldWeight) {
  }

  @Override
  public void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
  }

  @Override
  public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
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
  public void onSettingsAckRead(ChannelHandlerContext ctx) {
    Runnable handler = updateSettingsHandler.poll();
    if (handler != null) {
      // No need to run on a particular context it shall be done by the handler already
      handler.run();
    }
  }

  @Override
  public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
    Handler<io.vertx.core.http.Http2Settings> handler = clientSettingsHandler;
    if (handler != null) {
      context.executeFromIO(() -> {
        handler.handle(HttpUtils.toVertxSettings(settings));
      });
    }
  }

  @Override
  public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) {
  }

  @Override
  public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) {
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
    VertxHttp2Stream req = streams.get(streamId);
    req.handleUnknownFrame(frameType, flags.value(), Buffer.buffer(payload.copy()));
  }

  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    VertxHttp2Stream req = streams.get(streamId);
    req.handleReset(errorCode);
  }

  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
    VertxHttp2Stream req = streams.get(streamId);
    context.executeFromIO(() -> {
      req.handleData(Buffer.buffer(data.copy()));
    });
    if (endOfStream) {
      context.executeFromIO(req::handleEnd);
    }
    return padding;
  }

  // Http2Connection overrides

  // HttpConnection implementation

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    if (errorCode < 0) {
      throw new IllegalArgumentException();
    }
    if (lastStreamId < 0) {
      throw new IllegalArgumentException();
    }
    connHandler.encoder().writeGoAway(handlerContext, lastStreamId, errorCode, debugData != null ? debugData.getByteBuf() : Unpooled.EMPTY_BUFFER, handlerContext.newPromise());
    channel.flush();
    return this;
  }

  @Override
  public HttpConnection goAwayHandler(Handler<GoAway> handler) {
    goAwayHandler = handler;
    return this;
  }

  @Override
  public HttpConnection shutdownHandler(Handler<Void> handler) {
    shutdownHandler = handler;
    return this;
  }

  @Override
  public HttpConnection shutdown(long timeout) {
    if (timeout <= 0) {
      throw new IllegalArgumentException("Invalid timeout value " + timeout);
    }
    return shutdown((Long)timeout);
  }

  @Override
  public HttpConnection shutdown() {
    return shutdown(null);
  }

  private HttpConnection shutdown(Long timeout) {
    if (!shuttingdown) {
      shuttingdown = true;
      if (timeout != null) {
        connHandler.gracefulShutdownTimeoutMillis(timeout);
      }
      channel.close();
    }
    return this;
  }

  @Override
  public HttpConnection closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    return this;
  }

  @Override
  public void close() {
    shutdown((Long)0L);
  }

  @Override
  public HttpConnection remoteSettingsHandler(Handler<io.vertx.core.http.Http2Settings> handler) {
    clientSettingsHandler = handler;
    return this;
  }

  @Override
  public Handler<io.vertx.core.http.Http2Settings> remoteSettingsHandler() {
    return clientSettingsHandler;
  }

  @Override
  public io.vertx.core.http.Http2Settings remoteSettings() {
    io.vertx.core.http.Http2Settings a = new io.vertx.core.http.Http2Settings();
    a.setPushEnabled(connHandler.connection().remote().allowPushTo());
    a.setMaxConcurrentStreams((long)connHandler.connection().local().maxActiveStreams());
    a.setMaxHeaderListSize(connHandler.encoder().configuration().headerTable().maxHeaderListSize());
    a.setHeaderTableSize(connHandler.encoder().configuration().headerTable().maxHeaderTableSize());
    a.setMaxFrameSize(connHandler.encoder().configuration().frameSizePolicy().maxFrameSize());
    a.setInitialWindowSize(connHandler.encoder().flowController().initialWindowSize());
    return a;
  }

  @Override
  public io.vertx.core.http.Http2Settings settings() {
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

  protected void updateSettings(Http2Settings settingsUpdate, Handler<AsyncResult<Void>> completionHandler) {
    Context completionContext = completionHandler != null ? context.owner().getOrCreateContext() : null;
    Http2Settings current = connHandler.decoder().localSettings();
    for (Map.Entry<Character, Long> entry : current.entrySet()) {
      Character key = entry.getKey();
      if (Objects.equals(settingsUpdate.get(key), entry.getValue())) {
        settingsUpdate.remove(key);
      }
    }

    connHandler.encoder().writeSettings(handlerContext, settingsUpdate, handlerContext.newPromise()).addListener(fut -> {
      if (fut.isSuccess()) {
        updateSettingsHandler.add(() -> {
          serverSettings.putAll(settingsUpdate);
          if (completionHandler != null) {
            completionContext.runOnContext(v -> {
              completionHandler.handle(Future.succeededFuture());
            });
          }
        });
      } else {
        if (completionHandler != null) {
          completionContext.runOnContext(v -> {
            completionHandler.handle(Future.failedFuture(fut.cause()));
          });
        }
      }
    });
    channel.flush();
  }

  @Override
  public HttpConnection exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  // Private

  private void checkShutdownHandler() {
    if (!shutdown) {
      Http2Connection conn = connHandler.connection();
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
