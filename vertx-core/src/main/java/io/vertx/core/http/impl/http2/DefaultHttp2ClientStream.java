/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.http2;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.util.AsciiString;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.headers.Http1xHeaders;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.http.impl.http1x.Http1xClientConnection;
import io.vertx.core.http.impl.observability.ClientStreamObserver;
import io.vertx.core.http.impl.observability.StreamObserver;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.impl.MessageWrite;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class DefaultHttp2ClientStream extends DefaultHttp2Stream<DefaultHttp2ClientStream> implements HttpClientStream, Http2ClientStream {

  // Temporary id assignments
  private static final AtomicInteger id_seq = new AtomicInteger(-1);

  private final Http2ClientConnection connection;
  private final boolean decompressionSupported;
  private final ClientStreamObserver observable;
  private String scheme;
  private HostAndPort authority;

  // Handlers
  private Handler<HttpResponseHead> headersHandler;
  private Handler<Void> continueHandler;
  private Handler<HttpClientPush> pushHandler;
  private Handler<MultiMap> earlyHintsHandler;

  DefaultHttp2ClientStream(Http2ClientConnection connection, ContextInternal context, TracingPolicy tracingPolicy,
                           boolean decompressionSupported, TransportMetrics<?> transportMetrics, ClientMetrics clientMetrics) {
    this(id_seq.getAndDecrement(), connection, context, tracingPolicy, decompressionSupported, transportMetrics, clientMetrics, true);
  }

  DefaultHttp2ClientStream(int id, Http2ClientConnection connection, ContextInternal context, TracingPolicy tracingPolicy,
                           boolean decompressionSupported, TransportMetrics<?> transportMetrics,  ClientMetrics clientMetrics, boolean writable) {
    super(id, connection, context, writable);

    VertxTracer<?, ?> tracer = vertx.tracer();

    this.connection = connection;
    this.decompressionSupported = decompressionSupported;
    this.observable = clientMetrics != null || tracer != null ? new ClientStreamObserver(context, tracingPolicy,
      clientMetrics, transportMetrics, connection.metric(), tracer, connection.remoteAddress()) : null;
  }

  @Override
  public String scheme() {
    return scheme;
  }

  @Override
  public HostAndPort authority() {
    return authority;
  }

  @Override
  StreamObserver observer() {
    return observable;
  }

  public void upgrade(Object metric, Object trace) {
    if (observable != null) {
      observable.observeUpgrade(metric, trace);
    }
  }

  @Override
  public Future<Void> writeHead(HttpRequestHead request, boolean chunked, Buffer buf, boolean end, StreamPriority priority, boolean connect) {
    PromiseInternal<Void> promise = context.promise();
    priority(priority);
    scheme = request.scheme;
    authority = request.authority;
    write(new HeadersWrite(request, buf != null ? ((BufferInternal)buf).getByteBuf() : null, end, promise));
    return promise.future();
  }

  @Override
  public HttpClientStream setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  void writeHeaders(HttpRequestHead request, ByteBuf buf, boolean end, StreamPriority priority, Promise<Void> promise) {
    priority(priority);
    write(new HeadersWrite(request, buf, end, promise));
  }

  private class HeadersWrite implements MessageWrite {

    private final HttpRequestHead request;
    private final ByteBuf buf;
    private final boolean end;
    private final Promise<Void> promise;

    public HeadersWrite(HttpRequestHead request, ByteBuf buf, boolean end, Promise<Void> promise) {
      this.request = request;
      this.buf = buf;
      this.end = end;
      this.promise = promise;
    }

    @Override
    public void write() {
      HttpRequestHeaders headers = new HttpRequestHeaders(new DefaultHttp2Headers());
      headers.method(request.method);
      headers.trace(request.traceOperation);
      boolean e;
      if (request.method == HttpMethod.CONNECT) {
        if (request.authority == null) {
          throw new IllegalArgumentException("Missing :authority / host header");
        }
        headers.authority(request.authority);
        // don't end stream for CONNECT
        e = false;
      } else {
        headers.path(request.uri);
        headers.scheme(connection.isSsl() ? "https" : "http");
        if (request.authority != null) {
          headers.authority(request.authority);
        }
        e = end;
      }
      if (request.headers != null && !request.headers.isEmpty()) {
        for (Map.Entry<String, String> header : request.headers) {
          CharSequence headerName = HttpUtils.toLowerCase(header.getKey());
          if (!AsciiString.contentEquals(TRANSFER_ENCODING, headerName)) {
            headers.add(headerName, header.getValue());
          }
        }
      }
      if (decompressionSupported && headers.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
        headers.set(HttpHeaderNames.ACCEPT_ENCODING, Http1xClientConnection.determineCompressionAcceptEncoding());
      }
      try {
        connection.createStream(DefaultHttp2ClientStream.this);
      } catch (Exception ex) {
        promise.fail(ex);
        onException(ex);
        return;
      }
      if (buf != null) {
        writeHeaders0(headers, false, false, null);
        writeData0(buf, e, promise);
      } else {
        writeHeaders0(headers, e, true, promise);
      }
    }

    @Override
    public void cancel(Throwable cause) {
      promise.fail(cause);
    }
  }

  public Object metric() {
    return observable != null ? observable.metric() : null;
  }

  public Object trace() {
    return observable != null ? observable.trace() : null;
  }

  @Override
  public Http2ClientConnection connection() {
    return connection;
  }

  @Override
  public void onPush(Http2ClientStream pushStream, int promisedStreamId, HttpHeaders headers, boolean writable) {
    onPush((DefaultHttp2ClientStream)  pushStream, promisedStreamId, (HttpRequestHeaders)headers, writable);
  }

  public void onPush(DefaultHttp2ClientStream pushStream, int promisedStreamId, HttpRequestHeaders headers, boolean writable) {
    HttpClientPush push = new HttpClientPush(new HttpRequestHead(headers.scheme(), headers.method(), headers.path(), headers, headers.authority(), null, null), pushStream);
    pushStream.init(promisedStreamId, writable);
    if (pushStream.observable != null) {
      pushStream.observable.observePush(headers);
    }
    context.execute(push, this::handlePush);
  }

  void onContinue() {
    context.execute(null, v -> handleContinue());
  }

  void onEarlyHints(MultiMap headers) {
    context.execute(null, v -> handleEarlyHints(headers));
  }

  public void onHeaders(HttpHeaders headers) {
    HttpResponseHeaders responseHeaders = (HttpResponseHeaders)headers;
    int status = responseHeaders.status();
    if (status == 100) {
      onContinue();
      return;
    } else if (status == 103) {
      MultiMap headersMultiMap = Http1xHeaders.httpHeaders();
      headers.remove(io.vertx.core.http.HttpHeaders.PSEUDO_STATUS);
      for (Map.Entry<String, String> header : headers) {
        headersMultiMap.add(header.getKey(), header.getValue());
      }
      onEarlyHints(headersMultiMap);
      return;
    }
    super.onHeaders(headers);
  }

  public DefaultHttp2ClientStream headHandler(Handler<HttpResponseHead> handler) {
    headersHandler = handler;
    return this;
  }

  void handleHeader(HttpHeaders map) {
    Handler<HttpResponseHead> handler = headersHandler;
    if (handler != null) {
      HttpResponseHeaders responseHeaders = (HttpResponseHeaders)map;
      int status = responseHeaders.status();
      String statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
      HttpResponseHead response = new HttpResponseHead(
        status,
        statusMessage,
        map);
      context.dispatch(response, handler);
    }
  }

  public DefaultHttp2ClientStream continueHandler(Handler<Void> handler) {
    continueHandler = handler;
    return this;
  }

  void handleContinue() {
    Handler<Void> handler = continueHandler;
    if (handler != null) {
      context.dispatch(null, handler);
    }
  }

  public DefaultHttp2ClientStream pushHandler(Handler<HttpClientPush> handler) {
    pushHandler = handler;
    return this;
  }

  void handlePush(HttpClientPush push) {
    Handler<HttpClientPush> handler = pushHandler;
    if (handler != null) {
      context.dispatch(push, handler);
    }
  }

  public DefaultHttp2ClientStream earlyHintsHandler(Handler<MultiMap> handler) {
    earlyHintsHandler = handler;
    return this;
  }

  void handleEarlyHints(MultiMap headers) {
    Handler<MultiMap> handler = earlyHintsHandler;
    if (handler != null) {
      context.dispatch(headers, handler);
    }
  }

  @Override
  public HttpClientStream unwrap() {
    return this;
  }
}
