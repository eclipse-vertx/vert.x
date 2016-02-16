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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionAdapter;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.AsciiString;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

import java.util.ArrayDeque;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxHttp2Handler extends Http2ConnectionHandler implements Http2FrameListener, HttpConnection {

  static final String UPGRADE_RESPONSE_HEADER = "http-to-http2-upgrade";

  private ChannelHandlerContext context;
  private final Vertx vertx;
  private final String serverOrigin;
  private final IntObjectMap<VertxHttp2Stream> streams = new IntObjectHashMap<>();
  private final Handler<HttpServerRequest> handler;
  private Handler<Void> closeHandler;

  private Handler<io.vertx.core.http.Http2Settings> clientSettingsHandler;
  private Http2Settings clientSettings = new Http2Settings();
  private final ArrayDeque<Runnable> updateSettingsHandler = new ArrayDeque<>(4);
  private Http2Settings serverSettings = new Http2Settings();

  private Long maxConcurrentStreams;
  private int concurrentStreams;
  private final ArrayDeque<Push> pendingPushes = new ArrayDeque<>();

  VertxHttp2Handler(ChannelHandlerContext context, Vertx vertx, String serverOrigin, Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                         Http2Settings initialSettings, Handler<HttpServerRequest> handler) {
    super(decoder, encoder, initialSettings);

    encoder.flowController().listener(stream -> {
      Http2ServerResponseImpl resp = streams.get(stream.id()).response();
      resp.writabilityChanged();
    });

    connection().addListener(new Http2ConnectionAdapter() {
      @Override
      public void onStreamClosed(Http2Stream stream) {
        VertxHttp2Stream removed = streams.remove(stream.id());
        if (removed instanceof Push) {
          if (pendingPushes.remove(removed)) {
            Push push = (Push) removed;
            vertx.runOnContext(v -> {
              push.handler.handle(Future.failedFuture("Push reset by client"));
            });
          } else {
            concurrentStreams--;
            checkPendingPushes();
          }
        }
      }
    });

    this.context = context;
    this.vertx = vertx;
    this.serverOrigin = serverOrigin;
    this.handler = handler;
  }

  /**
   * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
   * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
   */
  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
      // Write an HTTP/2 response to the upgrade request
      Http2Headers headers =
          new DefaultHttp2Headers().status(OK.codeAsText())
              .set(new AsciiString(UPGRADE_RESPONSE_HEADER), new AsciiString("true"));
      encoder().writeHeaders(ctx, 1, headers, 0, true, ctx.newPromise());
    }
    super.userEventTriggered(ctx, evt);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    super.exceptionCaught(ctx, cause);
    cause.printStackTrace();
    ctx.close();
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                            Http2Headers headers, int padding, boolean endOfStream) {
    Http2Connection conn = connection();
    Http2Stream stream = conn.stream(streamId);
    Http2ServerRequestImpl req = new Http2ServerRequestImpl(vertx, this, serverOrigin, conn, stream, ctx, encoder(), headers);
    streams.put(streamId, req);
    handler.handle(req);
    if (endOfStream) {
      req.end();
    }
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                            short weight, boolean exclusive, int padding, boolean endOfStream) {
    onHeadersRead(ctx, streamId, headers, padding, endOfStream);
  }

  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
    Http2ServerRequestImpl req = (Http2ServerRequestImpl) streams.get(streamId);
    int processed = padding;
    if (req.handleData(Buffer.buffer(data.copy()))) {
      processed += data.readableBytes();
    }
    if (endOfStream) {
      req.end();
    }
    return processed;
  }

  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                             short weight, boolean exclusive) {
  }

  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    VertxHttp2Stream req = streams.get(streamId);
    req.reset(errorCode);
  }

  @Override
  public void onSettingsAckRead(ChannelHandlerContext ctx) {
    Runnable handler = updateSettingsHandler.poll();
    if (handler != null) {
      handler.run();
    }
  }

  @Override
  public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
    Long v = settings.maxConcurrentStreams();
    if (v != null) {
      maxConcurrentStreams = v;
    }
    clientSettings.putAll(settings);
    if (clientSettingsHandler != null) {
      clientSettingsHandler.handle(clientSettings());
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
                                Http2Headers headers, int padding) {
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
      closeHandler.handle(null);
    }
  }

  private class Push extends VertxHttp2Stream {

    final Http2ServerResponseImpl response;
    final Handler<AsyncResult<HttpServerResponse>> handler;

    public Push(Http2ServerResponseImpl response, Handler<AsyncResult<HttpServerResponse>> handler) {
      this.response = response;
      this.handler = handler;
    }

    @Override
    Http2ServerResponseImpl response() {
      return response;
    }

    @Override
    void reset(long code) {
      response.reset(code);
    }
  }

  void schedulePush(ChannelHandlerContext ctx, int streamId, Handler<AsyncResult<HttpServerResponse>> handler) {
    Http2Stream stream = connection().stream(streamId);
    Http2ServerResponseImpl resp = new Http2ServerResponseImpl(ctx, encoder(), stream);
    Push push = new Push(resp, handler);
    streams.put(streamId, push);
    if (maxConcurrentStreams == null || concurrentStreams < maxConcurrentStreams) {
      concurrentStreams++;
      handler.handle(Future.succeededFuture(resp));
    } else {
      pendingPushes.add(push);
    }
  }

  void checkPendingPushes() {
    while ((maxConcurrentStreams == null || concurrentStreams < maxConcurrentStreams) && pendingPushes.size() > 0) {
      Push push = pendingPushes.pop();
      concurrentStreams++;
      vertx.runOnContext(v -> {
        push.handler.handle(Future.succeededFuture(push.response));
      });
    }
  }

  @Override
  public HttpConnection closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    return this;
  }

  @Override
  public HttpConnection clientSettingsHandler(Handler<io.vertx.core.http.Http2Settings> handler) {
    clientSettingsHandler = handler;
    return this;
  }

  @Override
  public Handler<io.vertx.core.http.Http2Settings> clientSettingsHandler() {
    return clientSettingsHandler;
  }

  @Override
  public io.vertx.core.http.Http2Settings clientSettings() {
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
    Http2Settings settingsUpdate = fromVertxSettings(settings);
    settingsUpdate.remove(Http2CodecUtil.SETTINGS_ENABLE_PUSH);
    encoder().writeSettings(context, settingsUpdate, context.newPromise()).addListener(fut -> {
      if (fut.isSuccess()) {
        updateSettingsHandler.add(() -> {
          serverSettings.putAll(settingsUpdate);
          if (completionHandler != null) {
            completionHandler.handle(Future.succeededFuture());
          }
        });
      } else {
        completionHandler.handle(Future.failedFuture(fut.cause()));
      }
    });
    context.flush();
    return this;
  }

  public static Http2Settings fromVertxSettings(io.vertx.core.http.Http2Settings settings) {
    Http2Settings converted = new Http2Settings();
    converted.pushEnabled(settings.getEnablePush());
    converted.maxConcurrentStreams(settings.getMaxConcurrentStreams());
    converted.maxHeaderListSize(settings.getMaxHeaderListSize());
    converted.maxFrameSize(settings.getMaxFrameSize());
    converted.initialWindowSize(settings.getInitialWindowSize());
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
