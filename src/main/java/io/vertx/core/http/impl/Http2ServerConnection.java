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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerConnection extends Http2ConnectionBase {

  private final HttpServerOptions options;
  private final String serverOrigin;
  private final Handler<HttpServerRequest> handler;

  private Long maxConcurrentStreams;
  private int concurrentStreams;
  private final ArrayDeque<Push> pendingPushes = new ArrayDeque<>();

  Http2ServerConnection(
      Channel channel,
      ContextImpl context,
      String serverOrigin,
      VertxHttp2ConnectionHandler connHandler,
      HttpServerOptions options,
      Handler<HttpServerRequest> handler) {
    super(channel, context, connHandler);

    this.options = options;
    this.serverOrigin = serverOrigin;
    this.handler = handler;
  }

  /*
  */
/**
   * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
   * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
   *//*

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
*/

  private boolean isMalformedRequest(Http2Headers headers) {
    if (headers.method() == null) {
      return true;
    }
    String method = headers.method().toString();
    if (method.equals("CONNECT")) {
      if (headers.scheme() != null || headers.path() != null || headers.authority() == null) {
        return true;
      }
    } else {
      if (headers.method() == null || headers.scheme() == null || headers.path() == null) {
        return true;
      }
    }
    if (headers.authority() != null) {
      URI uri;
      try {
        uri = new URI(null, headers.authority().toString(), null, null, null);
      } catch (URISyntaxException e) {
        return true;
      }
      if (uri.getRawUserInfo() != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                            Http2Headers headers, int padding, boolean endOfStream) {
    Http2Connection conn = connHandler.connection();
    VertxHttp2Stream stream = streams.get(streamId);
    if (stream == null) {
      if (isMalformedRequest(headers)) {
        connHandler.encoder().writeRstStream(ctx, streamId, Http2Error.PROTOCOL_ERROR.code(), ctx.newPromise());
        return;
      }
      String contentEncoding = options.isCompressionSupported() ? HttpUtils.determineContentEncoding(headers) : null;
      Http2ServerRequestImpl req = new Http2ServerRequestImpl(context.owner(), context, this, serverOrigin, conn.stream(streamId), ctx, connHandler.encoder(), connHandler.decoder(), headers, contentEncoding);
      stream = req;
      CharSequence value = headers.get(HttpHeaderNames.EXPECT);
      if (options.isHandle100ContinueAutomatically() &&
          ((value != null && HttpHeaderValues.CONTINUE.equals(value)) ||
              headers.contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE))) {
        req.response().writeContinue();
      }
      streams.put(streamId, req);
      context.executeFromIO(() -> {
        handler.handle(req);
      });
    } else {
      // Trailer - not implemented yet
    }
    if (endOfStream) {
      context.executeFromIO(stream::handleEnd);
    }
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                            short weight, boolean exclusive, int padding, boolean endOfStream) {
    onHeadersRead(ctx, streamId, headers, padding, endOfStream);
  }

  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                             short weight, boolean exclusive) {
  }

  @Override
  public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
    Long v = settings.maxConcurrentStreams();
    if (v != null) {
      maxConcurrentStreams = v;
    }
    super.onSettingsRead(ctx, settings);
  }

  private class Push extends VertxHttp2Stream {

    final Http2ServerResponseImpl response;
    final Handler<AsyncResult<HttpServerResponse>> handler;

    public Push(Http2Stream stream, String contentEncoding, Handler<AsyncResult<HttpServerResponse>> handler) {
      super(Http2ServerConnection.this.context.owner(), Http2ServerConnection.this.context, Http2ServerConnection.this.handlerContext, connHandler.encoder(), connHandler.decoder(), stream);
      this.response = new Http2ServerResponseImpl(this, (VertxInternal) context.owner(), handlerContext, Http2ServerConnection.this, connHandler.encoder(), stream, true, contentEncoding);
      this.handler = handler;
    }

    @Override
    void callEnd() {
      throw new UnsupportedOperationException();
    }

    @Override
    void callHandler(Buffer buf) {
      throw new UnsupportedOperationException();
    }

    @Override
    void callReset(long errorCode) {
      response.callReset(errorCode);
    }

    @Override
    void handleInterestedOpsChanged() {
      response.writabilityChanged();
    }

    @Override
    void handleException(Throwable cause) {
      response.handleError(cause);
    }

    @Override
    void handleClose() {
      if (pendingPushes.remove(this)) {
        context.executeFromIO(() -> {
          handler.handle(Future.failedFuture("Push reset by client"));
        });
      } else {
        context.executeFromIO(() -> {
          response.handleClose();
        });
        concurrentStreams--;
        checkPendingPushes();
      }
    }
  }

  void sendPush(int streamId, Http2Headers headers, Handler<AsyncResult<HttpServerResponse>> handler) {
    int promisedStreamId = connHandler.connection().local().incrementAndGetNextStreamId();
    connHandler.encoder().writePushPromise(handlerContext, streamId, promisedStreamId, headers, 0, handlerContext.newPromise()).addListener(fut -> {
      if (fut.isSuccess()) {
        String contentEncoding = HttpUtils.determineContentEncoding(headers);
        schedulePush(handlerContext, promisedStreamId, contentEncoding, handler);
      } else {
        context.executeFromIO(() -> {
          handler.handle(Future.failedFuture(fut.cause()));
        });
      }
    });
  }

  private void schedulePush(ChannelHandlerContext ctx, int streamId, String contentEncoding, Handler<AsyncResult<HttpServerResponse>> handler) {
    Http2Stream stream = connHandler.connection().stream(streamId);
    Push push = new Push(stream, contentEncoding, handler);
    streams.put(streamId, push);
    if (maxConcurrentStreams == null || concurrentStreams < maxConcurrentStreams) {
      concurrentStreams++;
      context.executeFromIO(() -> {
        handler.handle(Future.succeededFuture(push.response));
      });
    } else {
      pendingPushes.add(push);
    }
  }

  void checkPendingPushes() {
    while ((maxConcurrentStreams == null || concurrentStreams < maxConcurrentStreams) && pendingPushes.size() > 0) {
      Push push = pendingPushes.pop();
      concurrentStreams++;
      context.executeFromIO(() -> {
        push.handler.handle(Future.succeededFuture(push.response));
      });
    }
  }

  protected void updateSettings(Http2Settings settingsUpdate, Handler<AsyncResult<Void>> completionHandler) {
    settingsUpdate.remove(Http2CodecUtil.SETTINGS_ENABLE_PUSH);
    super.updateSettings(settingsUpdate, completionHandler);
  }
}
