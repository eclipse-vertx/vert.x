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

package io.vertx.core.http.impl;

import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerConnection extends Http2ConnectionBase implements HttpServerConnection {

  final HttpServerOptions options;
  private final String serverOrigin;
  private final HttpServerMetrics metrics;

  Handler<HttpServerRequest> requestHandler;
  private int concurrentStreams;
  private final ArrayDeque<Push> pendingPushes = new ArrayDeque<>(8);

  Http2ServerConnection(
    ContextInternal context,
      String serverOrigin,
      VertxHttp2ConnectionHandler connHandler,
      HttpServerOptions options,
      HttpServerMetrics metrics) {
    super(context, connHandler);

    this.options = options;
    this.serverOrigin = serverOrigin;
    this.metrics = metrics;
  }

  @Override
  public HttpServerConnection handler(Handler<HttpServerRequest> handler) {
    requestHandler = handler;
    return this;
  }

  public HttpServerMetrics metrics() {
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

  private Http2ServerRequestImpl createRequest(int streamId, Http2Headers headers, boolean streamEnded) {
    Http2Stream stream = handler.connection().stream(streamId);
    String contentEncoding = options.isCompressionSupported() ? HttpUtils.determineContentEncoding(headers) : null;
    Http2ServerRequestImpl request = new Http2ServerRequestImpl(this, context.duplicate(), serverOrigin, headers, contentEncoding, streamEnded);
    request.init(stream);
    return request;
  }

  @Override
  protected synchronized void onHeadersRead(int streamId, Http2Headers headers, StreamPriority streamPriority, boolean endOfStream) {
    VertxHttp2Stream stream = stream(streamId);
    if (stream == null) {
      if (isMalformedRequest(headers)) {
        handler.writeReset(streamId, Http2Error.PROTOCOL_ERROR.code());
        return;
      }
      stream = createRequest(streamId, headers, endOfStream);
      stream.onHeaders(headers, streamPriority);
    } else {
      // Http server request trailer - not implemented yet (in api)
    }
    if (endOfStream) {
      stream.onEnd();
    }
  }

  void sendPush(int streamId, String host, HttpMethod method, MultiMap headers, String path, StreamPriority streamPriority, Promise<HttpServerResponse> promise) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doSendPush(streamId, host, method, headers, path, streamPriority, promise);
    } else {
      eventLoop.execute(() -> doSendPush(streamId, host, method, headers, path, streamPriority, promise));
    }
  }

  private synchronized void doSendPush(int streamId, String host, HttpMethod method, MultiMap headers, String path, StreamPriority streamPriority, Promise<HttpServerResponse> promise) {
    Http2Headers headers_ = new DefaultHttp2Headers();
    headers_.method(method.name());
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
            Push push = new Push(context, contentEncoding, method, path, promise);
            push.priority(streamPriority);
            push.init(promisedStream);
            int maxConcurrentStreams = handler.maxConcurrentStreams();
            if (concurrentStreams < maxConcurrentStreams) {
              concurrentStreams++;
              push.complete();
            } else {
              pendingPushes.add(push);
            }
          }
        } else {
          promise.fail(ar.cause());
        }
      }
    });
  }

  protected void updateSettings(Http2Settings settingsUpdate, Handler<AsyncResult<Void>> completionHandler) {
    settingsUpdate.remove(Http2CodecUtil.SETTINGS_ENABLE_PUSH);
    super.updateSettings(settingsUpdate, completionHandler);
  }

  private class Push extends Http2ServerStream {

    private final Promise<HttpServerResponse> promise;

    public Push(ContextInternal context,
                String contentEncoding,
                HttpMethod method,
                String uri,
                Promise<HttpServerResponse> promise) {
      super(Http2ServerConnection.this, context, contentEncoding, method, uri);
      this.promise = promise;
    }

    @Override
    void dispatch(Handler<HttpServerRequest> handler) {
      throw new UnsupportedOperationException();
    }

    @Override
    void handleEnd(MultiMap trailers) {
    }

    @Override
    void handleData(Buffer buf) {
    }

    @Override
    void handlePriorityChange(StreamPriority newPriority) {
    }

    @Override
    void handleWritabilityChanged(boolean writable) {
      response.handlerWritabilityChanged(writable);
    }

    @Override
    void handleReset(long errorCode) {
      if (!promise.tryFail(new StreamResetException(errorCode))) {
        response.handleReset(errorCode);
      }
    }

    @Override
    void handleException(Throwable cause) {
      if (response != null) {
        response.handleException(cause);
      }
    }

    @Override
    void handleClose() {
      super.handleClose();
      if (pendingPushes.remove(this)) {
        promise.fail("Push reset by client");
      } else {
        concurrentStreams--;
        int maxConcurrentStreams = handler.maxConcurrentStreams();
        while (concurrentStreams < maxConcurrentStreams && pendingPushes.size() > 0) {
          Push push = pendingPushes.pop();
          concurrentStreams++;
          push.complete();
        }
        response.handleClose();
      }
    }

    void complete() {
      registerMetrics();
      promise.complete(response);
    }
  }
}
