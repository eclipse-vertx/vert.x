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
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerConnection extends Http2ConnectionBase implements HttpServerConnection {

  final HttpServerOptions options;
  private final String serverOrigin;
  private final HttpServerMetrics metrics;
  private final Function<String, String> encodingDetector;
  private final Supplier<ContextInternal> streamContextSupplier;

  Handler<HttpServerRequest> requestHandler;
  private int concurrentStreams;
  private final ArrayDeque<Push> pendingPushes = new ArrayDeque<>(8);
  private VertxHttp2Stream upgraded;

  Http2ServerConnection(
    ContextInternal context,
    Supplier<ContextInternal> streamContextSupplier,
    String serverOrigin,
    VertxHttp2ConnectionHandler connHandler,
    Function<String, String> encodingDetector,
    HttpServerOptions options,
    HttpServerMetrics metrics) {
    super(context, connHandler);

    this.options = options;
    this.serverOrigin = serverOrigin;
    this.encodingDetector = encodingDetector;
    this.streamContextSupplier = streamContextSupplier;
    this.metrics = metrics;
  }

  @Override
  public HttpServerConnection handler(Handler<HttpServerRequest> handler) {
    requestHandler = handler;
    return this;
  }

  @Override
  public HttpServerConnection invalidRequestHandler(Handler<HttpServerRequest> handler) {
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
      if (headers.method() == null || headers.scheme() == null || headers.path() == null || headers.path().length() == 0) {
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
      CharSequence host = headers.get(HttpHeaders.HOST);
      if (host != null) {
        URI hostURI;
        try {
          hostURI = new URI(null, host.toString(), null, null, null);
        } catch (URISyntaxException e) {
          return true;
        }
        if (uri.getRawUserInfo() != null || !uri.getAuthority().equals(hostURI.getAuthority())) {
          return true;
        }
      }
    }
    return false;
  }


  private static class EncodingDetector extends HttpContentCompressor {

    private EncodingDetector(CompressionOptions[] compressionOptions) {
      super(compressionOptions);
    }

    @Override
    protected String determineEncoding(String acceptEncoding) {
      return super.determineEncoding(acceptEncoding);
    }
  }

  String determineContentEncoding(Http2Headers headers) {
    String acceptEncoding = headers.get(HttpHeaderNames.ACCEPT_ENCODING) != null ? headers.get(HttpHeaderNames.ACCEPT_ENCODING).toString() : null;
    if (acceptEncoding != null && encodingDetector != null) {
      return encodingDetector.apply(acceptEncoding);
    }
    return null;
  }

  private Http2ServerStream createStream(int streamId, Http2Headers headers, boolean streamEnded) {
    Http2Stream stream = handler.connection().stream(streamId);
    String contentEncoding = options.isCompressionSupported() ? determineContentEncoding(headers) : null;
    Http2ServerStream vertxStream = new Http2ServerStream(this, streamContextSupplier.get(), headers, serverOrigin, options.getTracingPolicy(), streamEnded);
    Http2ServerRequest request = new Http2ServerRequest(vertxStream, serverOrigin, headers, contentEncoding);
    vertxStream.request = request;
    vertxStream.isConnect = request.method() == HttpMethod.CONNECT;
    vertxStream.init(stream);
    return vertxStream;
  }

  VertxHttp2Stream<?> stream(int id) {
    VertxHttp2Stream<?> stream = super.stream(id);
    if (stream == null && id == 1 && handler.upgraded) {
      return upgraded;
    }
    return stream;
  }

  @Override
  protected synchronized void onHeadersRead(int streamId, Http2Headers headers, StreamPriority streamPriority, boolean endOfStream) {
    VertxHttp2Stream stream = stream(streamId);
    if (stream == null) {
      if (isMalformedRequest(headers)) {
        handler.writeReset(streamId, Http2Error.PROTOCOL_ERROR.code());
        return;
      }
      if (streamId == 1 && handler.upgraded) {
        stream = createStream(streamId, headers, true);
        upgraded = stream;
      } else {
        stream = createStream(streamId, headers, endOfStream);
      }
      stream.onHeaders(headers, streamPriority);
    } else {
      // Http server request trailer - not implemented yet (in api)
    }
    if (endOfStream) {
      stream.onEnd();
    }
  }

  void sendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriority streamPriority, Promise<HttpServerResponse> promise) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doSendPush(streamId, authority, method, headers, path, streamPriority, promise);
    } else {
      eventLoop.execute(() -> doSendPush(streamId, authority, method, headers, path, streamPriority, promise));
    }
  }

  private synchronized void doSendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriority streamPriority, Promise<HttpServerResponse> promise) {
    boolean ssl = isSsl();
    Http2Headers headers_ = new DefaultHttp2Headers();
    headers_.method(method.name());
    headers_.path(path);
    headers_.scheme(ssl ? "https" : "http");
    if (authority != null) {
      String s = (ssl && authority.port() == 443) || (!ssl && authority.port() == 80) || authority.port() <= 0 ? authority.host() : authority.host() + ':' + authority.port();
      headers_.authority(s);
    }
    if (headers != null) {
      headers.forEach(header -> headers_.add(header.getKey(), header.getValue()));
    }
    Future<Integer> fut = handler.writePushPromise(streamId, headers_);
    fut.addListener((FutureListener<Integer>) future -> {
      if (future.isSuccess()) {
        synchronized (Http2ServerConnection.this) {
          int promisedStreamId = future.getNow();
          String contentEncoding = determineContentEncoding(headers_);
          Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
          Http2ServerStream vertxStream = new Http2ServerStream(this, context, method, path, options.getTracingPolicy(), true);
          Push push = new Push(vertxStream, contentEncoding, promise);
          vertxStream.request = push;
          push.stream.priority(streamPriority);
          push.stream.init(promisedStream);
          int maxConcurrentStreams = handler.maxConcurrentStreams();
          if (concurrentStreams < maxConcurrentStreams) {
            concurrentStreams++;
            push.complete();
          } else {
            pendingPushes.add(push);
          }
        }
      } else {
        promise.fail(future.cause());
      }
    });
  }

  protected void updateSettings(Http2Settings settingsUpdate, Handler<AsyncResult<Void>> completionHandler) {
    settingsUpdate.remove(Http2CodecUtil.SETTINGS_ENABLE_PUSH);
    super.updateSettings(settingsUpdate, completionHandler);
  }

  private class Push implements Http2ServerStreamHandler {

    protected final ContextInternal context;
    protected final Http2ServerStream stream;
    protected final Http2ServerResponse response;
    private final Promise<HttpServerResponse> promise;

    public Push(Http2ServerStream stream,
                String contentEncoding,
                Promise<HttpServerResponse> promise) {
      this.context = stream.context;
      this.stream = stream;
      this.response = new Http2ServerResponse(stream.conn, stream, true, contentEncoding);
      this.promise = promise;
    }

    @Override
    public Http2ServerResponse response() {
      return response;
    }

    @Override
    public  void dispatch(Handler<HttpServerRequest> handler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void handleReset(long errorCode) {
      if (!promise.tryFail(new StreamResetException(errorCode))) {
        response.handleReset(errorCode);
      }
    }

    @Override
    public  void handleException(Throwable cause) {
      if (response != null) {
        response.handleException(cause);
      }
    }

    @Override
    public  void handleClose() {
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
      stream.registerMetrics();
      promise.complete(response);
    }
  }
}
