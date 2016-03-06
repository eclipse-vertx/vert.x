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
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionAdapter;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
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
import io.vertx.core.http.HttpConnection;
import io.vertx.core.impl.ContextImpl;

import java.util.ArrayDeque;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class VertxHttp2ConnectionHandler extends Http2ConnectionHandler implements Http2FrameListener, HttpConnection {

  private final ChannelHandlerContext context;
  private final Channel channel;
  private final ContextImpl handlerContext;
  private final IntObjectMap<VertxHttp2Stream> streams = new IntObjectHashMap<>();
  private Handler<Void> closeHandler;
  private boolean shuttingDown;
  private Handler<io.vertx.core.http.Http2Settings> clientSettingsHandler;
  private Http2Settings clientSettings = new Http2Settings();
  private final ArrayDeque<Runnable> updateSettingsHandler = new ArrayDeque<>(4);
  private Http2Settings serverSettings = new Http2Settings();
//  private Handler<Throwable> exceptionHandler;

  public VertxHttp2ConnectionHandler(
      ChannelHandlerContext context, Channel channel, ContextImpl handlerContext,
      Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
    super(decoder, encoder, initialSettings);

    this.channel = channel;
    this.context = context;
    this.handlerContext = handlerContext;
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    super.exceptionCaught(ctx, cause);
    cause.printStackTrace();
    ctx.close();
  }

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
    clientSettings.putAll(settings);
    if (clientSettingsHandler != null) {
      handlerContext.executeFromIO(() -> {
        clientSettingsHandler.handle(remoteSettings());
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
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    super.channelInactive(ctx);
    if (closeHandler != null) {
      handlerContext.executeFromIO(() -> {
        closeHandler.handle(null);
      });
    }
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData, Handler<Void> completionHandler) {
    if (errorCode < 0) {
      throw new IllegalArgumentException();
    }
    if (lastStreamId < 0) {
      throw new IllegalArgumentException();
    }
    if (completionHandler != null) {
      Context completionContext = handlerContext.owner().getOrCreateContext();
      connection().addListener(new Http2ConnectionAdapter() {
        @Override
        public void onStreamClosed(Http2Stream stream) {
          if (connection().numActiveStreams() == 0) {
            completionContext.runOnContext(v -> {
              completionHandler.handle(null);
            });
          }
        }
      });
    }
    encoder().writeGoAway(context, lastStreamId, errorCode, debugData != null ? debugData.getByteBuf() : Unpooled.EMPTY_BUFFER, context.newPromise());
    context.flush();
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
    if (!shuttingDown) {
      shuttingDown = true;
      goAway(0, 2^31 - 1, null, v -> {
        context.close();
      });
      if (timeout != null) {
        handlerContext.owner().setTimer(timeout, timerID -> {
          context.close();
        });
      }
    }
    return this;
  }

  @Override
  public HttpConnection closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    return this;
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
    return toVertxSettings(clientSettings);
  }

  @Override
  public io.vertx.core.http.Http2Settings settings() {
    return toVertxSettings(serverSettings);
  }

  @Override
  public HttpConnection updateSettings(io.vertx.core.http.Http2Settings settings) {
    return updateSettings(settings, null);
  }

  @Override
  public HttpConnection updateSettings(io.vertx.core.http.Http2Settings settings, @Nullable Handler<AsyncResult<Void>> completionHandler) {
    Context completionContext = completionHandler != null ? handlerContext.owner().getOrCreateContext() : null;
    Http2Settings settingsUpdate = fromVertxSettings(settings);
    settingsUpdate.remove(Http2CodecUtil.SETTINGS_ENABLE_PUSH);
    encoder().writeSettings(context, settingsUpdate, context.newPromise()).addListener(fut -> {
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
    context.flush();
    return this;
  }


  public static Http2Settings fromVertxSettings(io.vertx.core.http.Http2Settings settings) {
    Http2Settings converted = new Http2Settings();
    if (settings.getEnablePush() != null) {
      converted.pushEnabled(settings.getEnablePush());
    }
    if (settings.getMaxConcurrentStreams() != null) {
      converted.maxConcurrentStreams(settings.getMaxConcurrentStreams());
    }
    if (settings.getMaxHeaderListSize() != null) {
      converted.maxHeaderListSize(settings.getMaxHeaderListSize());
    }
    if (settings.getMaxFrameSize() != null) {
      converted.maxFrameSize(settings.getMaxFrameSize());
    }
    if (settings.getInitialWindowSize() != null) {
      converted.initialWindowSize(settings.getInitialWindowSize());
    }
    if (settings.getHeaderTableSize() != null) {
      converted.headerTableSize((int)(long)settings.getHeaderTableSize());
    }
    return converted;
  }

  public static io.vertx.core.http.Http2Settings toVertxSettings(Http2Settings settings) {
    io.vertx.core.http.Http2Settings converted = new io.vertx.core.http.Http2Settings();
    converted.setEnablePush(settings.pushEnabled());
    converted.setMaxConcurrentStreams(settings.maxConcurrentStreams());
    converted.setMaxHeaderListSize(settings.maxHeaderListSize());
    converted.setMaxFrameSize(settings.maxFrameSize());
    converted.setInitialWindowSize(settings.initialWindowSize());
    if (settings.headerTableSize() != null) {
      converted.setHeaderTableSize((int)(long)settings.headerTableSize());
    }
    return converted;
  }
}
