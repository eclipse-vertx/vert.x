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
import io.netty.handler.codec.http2.*;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.VertxHttp2Headers;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;

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
  private VertxHttpStreamBase<?, ?> upgraded;

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

  private static boolean isMalformedRequest(Http2ServerStream request) {
    if (request.method == null) {
      return true;
    }

    if (request.method == HttpMethod.CONNECT) {
      if (request.scheme != null || request.uri != null || request.authority == null) {
        return true;
      }
    } else {
      if (request.scheme == null || request.uri == null || request.uri.length() == 0) {
        return true;
      }
    }
    if (request.hasAuthority) {
      if (request.authority == null) {
        return true;
      }
      CharSequence hostHeader = request.headers.get(HttpHeaders.HOST);
      if (hostHeader != null) {
        HostAndPort host = HostAndPort.parseAuthority(hostHeader.toString(), -1);
        return host == null || (!request.authority.host().equals(host.host()) || request.authority.port() != host.port());
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

  private Http2ServerStream createStream(Http2Headers headers, boolean streamEnded) {
    CharSequence schemeHeader = headers.getAndRemove(HttpHeaders.PSEUDO_SCHEME);
    HostAndPort authority = null;
    String authorityHeaderAsString;
    CharSequence authorityHeader = headers.getAndRemove(HttpHeaders.PSEUDO_AUTHORITY);
    if (authorityHeader != null) {
      authorityHeaderAsString = authorityHeader.toString();
      authority = HostAndPort.parseAuthority(authorityHeaderAsString, -1);
    }
    CharSequence pathHeader = headers.getAndRemove(HttpHeaders.PSEUDO_PATH);
    CharSequence methodHeader = headers.getAndRemove(HttpHeaders.PSEUDO_METHOD);
    return new Http2ServerStream(
      this,
      streamContextSupplier.get(),
      headers,
      schemeHeader != null ? schemeHeader.toString() : null,
      authorityHeader != null,
      authority,
      methodHeader != null ? HttpMethod.valueOf(methodHeader.toString()) : null,
      pathHeader != null ? pathHeader.toString() : null,
      options.getTracingPolicy(), streamEnded);
  }

  private void initStream(int streamId, Http2ServerStream vertxStream) {
    Http2ServerRequest request = new Http2ServerRequest(vertxStream, serverOrigin, vertxStream.headers);
    vertxStream.request = request;
    vertxStream.isConnect = request.method() == HttpMethod.CONNECT;
    Http2Stream stream = handler.connection().stream(streamId);
    vertxStream.init(stream);
  }

  VertxHttpStreamBase<?, ?> stream(int id) {
    VertxHttpStreamBase<?, ?> stream = super.stream(id);
    if (stream == null && id == 1 && handler.upgraded) {
      return upgraded;
    }
    return stream;
  }

  @Override
  protected synchronized void onHeadersRead(int streamId, Http2Headers headers, StreamPriorityBase streamPriority, boolean endOfStream) {
    Http2ServerStream stream = (Http2ServerStream) stream(streamId);
    if (stream == null) {
      if (streamId == 1 && handler.upgraded) {
        stream = createStream(headers, true);
        upgraded = stream;
      } else {
        stream = createStream(headers, endOfStream);
      }
      if (isMalformedRequest(stream)) {
        handler.writeReset(streamId, Http2Error.PROTOCOL_ERROR.code());
        return;
      }
      initStream(streamId, stream);
      stream.onHeaders(new VertxHttp2Headers(headers), streamPriority);
    } else {
      // Http server request trailer - not implemented yet (in api)
    }
    if (endOfStream) {
      stream.onEnd();
    }
  }

  void sendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriorityBase streamPriority, Promise<HttpServerResponse> promise) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doSendPush(streamId, authority, method, headers, path, streamPriority, promise);
    } else {
      eventLoop.execute(() -> doSendPush(streamId, authority, method, headers, path, streamPriority, promise));
    }
  }

  private synchronized void doSendPush(int streamId, HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriorityBase streamPriority, Promise<HttpServerResponse> promise) {
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
          Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
          Http2ServerStream vertxStream = new Http2ServerStream(this, context, headers_, method, path, options.getTracingPolicy(), true);
          Push push = new Push(vertxStream, promise);
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

  protected io.vertx.core.Future<Void> updateSettings(HttpSettings settingsUpdate) {
    settingsUpdate.remove(Http2CodecUtil.SETTINGS_ENABLE_PUSH);
    return super.updateSettings(settingsUpdate);
  }

  private class Push implements Http2ServerStreamHandler {

    protected final ContextInternal context;
    protected final Http2ServerStream stream;
    protected final Http2ServerResponse response;
    private final Promise<HttpServerResponse> promise;

    public Push(Http2ServerStream stream,
                Promise<HttpServerResponse> promise) {
      this.context = stream.context;
      this.stream = stream;
      this.response = new Http2ServerResponse(stream.conn, stream, true);
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
