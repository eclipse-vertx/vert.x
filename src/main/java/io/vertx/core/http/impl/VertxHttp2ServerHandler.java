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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.AsciiString;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketAddressImpl;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxHttp2ServerHandler extends VertxHttp2ConnectionHandler {

  static final String UPGRADE_RESPONSE_HEADER = "http-to-http2-upgrade";

  private final IntObjectMap<VertxHttp2Stream> streams = new IntObjectHashMap<>();

  private final HttpServerOptions options;
  private final String serverOrigin;
  private final Handler<HttpServerRequest> handler;

  private Long maxConcurrentStreams;
  private int concurrentStreams;
  private final ArrayDeque<Push> pendingPushes = new ArrayDeque<>();

  VertxHttp2ServerHandler(ChannelHandlerContext handlerCtx, Channel channel, ContextImpl context, String serverOrigin, Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                          Http2Settings initialSettings, HttpServerOptions options, Handler<HttpServerRequest> handler) {
    super(handlerCtx, channel, context, decoder, encoder, initialSettings);

    encoder.flowController().listener(stream -> {
      Http2ServerResponseImpl resp = streams.get(stream.id()).response();
      resp.writabilityChanged();
    });

    this.options = options;
    this.serverOrigin = serverOrigin;
    this.handler = handler;
  }

  @Override
  public void onStreamClosed(Http2Stream stream) {
    super.onStreamClosed(stream);
    VertxHttp2Stream removed = streams.remove(stream.id());
    if (removed != null) {
      context.executeFromIO(() -> {
        removed.handleClose();
      });
    }
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
    Http2Connection conn = connection();
    Http2Stream stream = conn.stream(streamId);
    Http2ServerRequestImpl req = (Http2ServerRequestImpl) streams.get(streamId);
    if (req == null) {
      String contentEncoding = options.isCompressionSupported() ? UriUtils.determineContentEncoding(headers) : null;
      Http2ServerRequestImpl newReq = req = new Http2ServerRequestImpl(context.owner(), this, serverOrigin, conn, stream, ctx, encoder(), headers, contentEncoding);
      if (isMalformedRequest(headers)) {
        encoder().writeRstStream(ctx, streamId, Http2Error.PROTOCOL_ERROR.code(), ctx.newPromise());
        return;
      }
      CharSequence value = headers.get(HttpHeaderNames.EXPECT);
      if (options.isHandle100ContinueAutomatically() &&
          ((value != null && HttpHeaderValues.CONTINUE.equals(value)) ||
              headers.contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE))) {
        req.response().writeContinue();
      }
      streams.put(streamId, newReq);
      context.executeFromIO(() -> {
        handler.handle(newReq);
      });
    } else {
      // Trailer - not implemented yet
    }
    if (endOfStream) {
      context.executeFromIO(req::end);
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
    context.executeFromIO(() -> {
      req.handleData(Buffer.buffer(data.copy()));
    });
    if (endOfStream) {
      context.executeFromIO(req::end);
    }
    return padding;
  }

  @Override
  public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                             short weight, boolean exclusive) {
  }

  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    VertxHttp2Stream req = streams.get(streamId);
    req.handleReset(errorCode);
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

    public Push(Http2ServerResponseImpl response, Handler<AsyncResult<HttpServerResponse>> handler) {
      this.response = response;
      this.handler = handler;
    }

    @Override
    void handleException(Throwable cause) {
      response.handleError(cause);
    }

    @Override
    Http2ServerResponseImpl response() {
      return response;
    }

    @Override
    void handleReset(long code) {
      response.handleReset(code);
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
    int promisedStreamId = connection().local().incrementAndGetNextStreamId();
    encoder().writePushPromise(handlerCtx, streamId, promisedStreamId, headers, 0, handlerCtx.newPromise()).addListener(fut -> {
      if (fut.isSuccess()) {
        String contentEncoding = UriUtils.determineContentEncoding(headers);
        schedulePush(handlerCtx, promisedStreamId, contentEncoding, handler);
      } else {
        context.executeFromIO(() -> {
          handler.handle(Future.failedFuture(fut.cause()));
        });
      }
    });
  }

  private void schedulePush(ChannelHandlerContext ctx, int streamId, String contentEncoding, Handler<AsyncResult<HttpServerResponse>> handler) {
    Http2Stream stream = connection().stream(streamId);
    Http2ServerResponseImpl resp = new Http2ServerResponseImpl((VertxInternal) context.owner(), ctx, this, encoder(), stream, true, contentEncoding);
    Push push = new Push(resp, handler);
    streams.put(streamId, push);
    if (maxConcurrentStreams == null || concurrentStreams < maxConcurrentStreams) {
      concurrentStreams++;
      context.executeFromIO(() -> {
        handler.handle(Future.succeededFuture(resp));
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

  @Override
  protected void onStreamError(ChannelHandlerContext ctx, Throwable cause, Http2Exception.StreamException http2Ex) {
    VertxHttp2Stream stream = streams.get(http2Ex.streamId());
    if (stream != null) {
      context.executeFromIO(() -> {
        stream.handleException(http2Ex);
      });
    }
    // Default behavior reset stream
    super.onStreamError(ctx, cause, http2Ex);
  }

  @Override
  protected void onConnectionError(ChannelHandlerContext ctx, Throwable cause, Http2Exception http2Ex) {
    for (VertxHttp2Stream stream : streams.values()) {
      context.executeFromIO(() -> {
        stream.handleException(cause);
      });
    }
    super.onConnectionError(ctx, cause, http2Ex);
  }

  public SocketAddress remoteAddress() {
    InetSocketAddress addr = (InetSocketAddress) channel.remoteAddress();
    if (addr == null) return null;
    return new SocketAddressImpl(addr);
  }

}
