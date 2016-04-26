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
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerConnection extends Http2ConnectionBase {

  private final HttpServerOptions options;
  private final String serverOrigin;
  private final Handler<HttpServerRequest> requestHandler;
  private final HttpServerMetrics metrics;
  private final Object metric;

  private Long maxConcurrentStreams;
  private int concurrentStreams;
  private final ArrayDeque<Push> pendingPushes = new ArrayDeque<>(8);

  Http2ServerConnection(
      Channel channel,
      ContextImpl context,
      String serverOrigin,
      VertxHttp2ConnectionHandler connHandler,
      HttpServerOptions options,
      Handler<HttpServerRequest> requestHandler,
      HttpServerMetrics metrics) {
    super(channel, context, connHandler, metrics);

    this.options = options;
    this.serverOrigin = serverOrigin;
    this.requestHandler = requestHandler;
    this.metric = metrics.connected(remoteAddress(), remoteName());
    this.metrics = metrics;
  }

  HttpServerMetrics metrics() {
    return metrics;
  }

  private static boolean isMalformedRequest(Http2Headers headers) {
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
  public synchronized void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                            Http2Headers headers, int padding, boolean endOfStream) {
    VertxHttp2Stream stream = streams.get(streamId);
    if (stream == null) {
      if (isMalformedRequest(headers)) {
        handler.writeReset(streamId, Http2Error.PROTOCOL_ERROR.code());
        return;
      }
      String contentEncoding = options.isCompressionSupported() ? HttpUtils.determineContentEncoding(headers) : null;
      Http2ServerRequestImpl req = new Http2ServerRequestImpl(this, handler.connection().stream(streamId), metrics, serverOrigin, headers, contentEncoding);
      stream = req;
      CharSequence value = headers.get(HttpHeaderNames.EXPECT);
      if (options.isHandle100ContinueAutomatically() &&
          ((value != null && HttpHeaderValues.CONTINUE.equals(value)) ||
              headers.contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE))) {
        req.response().writeContinue();
      }
      streams.put(streamId, req);
      context.executeFromIO(() -> {
        requestHandler.handle(req);
      });
    } else {
      // Http server request trailer - not implemented yet (in api)
    }
    if (endOfStream) {
      context.executeFromIO(stream::onEnd);
    }
  }

  @Override
  public synchronized void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
    Long v = settings.maxConcurrentStreams();
    if (v != null) {
      maxConcurrentStreams = v;
    }
    super.onSettingsRead(ctx, settings);
  }

  synchronized void sendPush(int streamId, String host, HttpMethod method, MultiMap headers, String path, Handler<AsyncResult<HttpServerResponse>> completionHandler) {
    Http2Headers headers_ = new DefaultHttp2Headers();
    if (method == HttpMethod.OTHER) {
      throw new IllegalArgumentException("Cannot push HttpMethod.OTHER");
    } else {
      headers_.method(method.name());
    }
    headers_.path(path);
    headers_.scheme(isSsl() ? "https" : "http");
    if (host != null) {
      headers_.authority(host);
    }
    if (headers != null) {
      headers.forEach(header -> headers_.add(header.getKey(), header.getValue()));
    }
    handler.writePushPromise(streamId, headers_, new Handler<AsyncResult<Integer>>() {
      @Override
      public void handle(AsyncResult<Integer> ar) {
        if (ar.succeeded()) {
          synchronized (Http2ServerConnection.this) {
            int promisedStreamId = ar.result();
            String contentEncoding = HttpUtils.determineContentEncoding(headers_);
            Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
            Push push = new Push(promisedStream, contentEncoding, method, path, completionHandler);
            streams.put(promisedStreamId, push);
            if (maxConcurrentStreams == null || concurrentStreams < maxConcurrentStreams) {
              concurrentStreams++;
              context.executeFromIO(push::complete);
            } else {
              pendingPushes.add(push);
            }
          }
        } else {
          context.executeFromIO(() -> {
            completionHandler.handle(Future.failedFuture(ar.cause()));
          });
        }
      }
    });
  }

  protected void updateSettings(Http2Settings settingsUpdate, Handler<AsyncResult<Void>> completionHandler) {
    settingsUpdate.remove(Http2CodecUtil.SETTINGS_ENABLE_PUSH);
    super.updateSettings(settingsUpdate, completionHandler);
  }

  @Override
  protected Object metric() {
    return metric;
  }

  private class Push extends VertxHttp2Stream<Http2ServerConnection> {

    private final HttpMethod method;
    private final String uri;
    private final String contentEncoding;
    private Http2ServerResponseImpl response;
    private final Future<HttpServerResponse> completionHandler;

    public Push(Http2Stream stream,
                String contentEncoding,
                HttpMethod method,
                String uri,
                Handler<AsyncResult<HttpServerResponse>> completionHandler) {
      super(Http2ServerConnection.this, stream);
      this.method = method;
      this.uri = uri;
      this.contentEncoding = contentEncoding;
      this.completionHandler = Future.<HttpServerResponse>future().setHandler(completionHandler);
    }

    @Override
    void handleEnd(MultiMap trailers) {
    }

    @Override
    void handleData(Buffer buf) {
    }

    @Override
    void handleInterestedOpsChanged() {
      if (response != null) {
        response.writabilityChanged();
      }
    }

    @Override
    void handleReset(long errorCode) {
      if (response != null) {
        response.callReset(errorCode);
      } else {
        completionHandler.fail(new StreamResetException(errorCode));
      }
    }

    @Override
    void handleException(Throwable cause) {
      if (response != null) {
        response.handleError(cause);
      }
    }

    @Override
    void handleClose() {
      if (pendingPushes.remove(this)) {
        completionHandler.fail("Push reset by client");
      } else {
        concurrentStreams--;
        while ((maxConcurrentStreams == null || concurrentStreams < maxConcurrentStreams) && pendingPushes.size() > 0) {
          Push push = pendingPushes.pop();
          concurrentStreams++;
          context.runOnContext(v -> {
            push.complete();
          });
        }
        response.handleClose();
      }
    }

    void complete() {
      synchronized (Http2ServerConnection.this) {
        response = new Http2ServerResponseImpl(Http2ServerConnection.this, this, method, uri, true, contentEncoding);
        completionHandler.complete(response);
      }
    }
  }
}
