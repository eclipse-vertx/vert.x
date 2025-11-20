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
package io.vertx.core.http.impl.http2;

import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.HttpServerStream;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.http.impl.observability.StreamObserver;
import io.vertx.core.http.impl.observability.ServerStreamObserver;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

class DefaultHttp2ServerStream extends DefaultHttp2Stream<DefaultHttp2ServerStream> implements HttpServerStream, Http2ServerStream {

  private final Http2ServerConnection connection;
  private final ServerStreamObserver observable;
  private HttpRequestHeaders requestHeaders;
  private String scheme;
  private HttpMethod method;
  private String uri;

  // Client handlers
  private Handler<HttpRequestHead> headersHandler;

  DefaultHttp2ServerStream(Http2ServerConnection connection,
                           HttpServerMetrics serverMetrics,
                           Object socketMetric,
                           ContextInternal context,
                           HttpRequestHeaders requestHeaders,
                           HttpMethod method,
                           String uri,
                           TracingPolicy tracingPolicy,
                           int promisedId) {
    super(promisedId, connection, context, true);

    VertxTracer<?, ?> tracer = vertx.tracer();

    this.connection = connection;
    this.requestHeaders = requestHeaders;
    this.method = method;
    this.uri = uri;
    this.scheme = null;
    this.observable = serverMetrics != null || tracer != null ? new ServerStreamObserver(context, serverMetrics, tracer, socketMetric, tracingPolicy, connection.remoteAddress()) : null;
  }

  DefaultHttp2ServerStream(Http2ServerConnection connection,
                           HttpServerMetrics<?, ?, ?> serverMetrics,
                           Object socketMetric,
                           ContextInternal context,
                           TracingPolicy tracingPolicy) {
    super(connection, context);

    VertxTracer<?, ?> tracer = vertx.tracer();

    this.connection = connection;
    this.observable = serverMetrics != null || tracer != null ? new ServerStreamObserver(context, serverMetrics, tracer, socketMetric, tracingPolicy, connection.remoteAddress()) : null;
  }

  @Override
  StreamObserver observer() {
    return observable;
  }

  public HttpHeaders headers() {
    return requestHeaders;
  }

  public HttpMethod method() {
    return method;
  }

  public String uri() {
    return uri;
  }

  public String scheme() {
    return scheme;
  }

  public HostAndPort authority() {
    return requestHeaders.authority();
  }

  public HostAndPort authority(boolean real) {
    return requestHeaders.authority();
  }

  public Object metric() {
    return observable != null ? observable.metric() : null;
  }

  @Override
  public Http2ServerConnection connection() {
    return connection;
  }

  public void onHeaders(HttpHeaders headers) {

    HttpRequestHeaders requestHeaders = (HttpRequestHeaders)headers;

    this.method = requestHeaders.method();
    this.uri = requestHeaders.path();
    this.scheme = requestHeaders.scheme();
    this.requestHeaders = (HttpRequestHeaders) headers;

    super.onHeaders(headers);
  }

  public DefaultHttp2ServerStream headHandler(Handler<HttpRequestHead> handler) {
    headersHandler = handler;
    return this;
  }

  void handleHeader(HttpHeaders map) {
    Handler<HttpRequestHead> handler = headersHandler;

    HttpRequestHeaders requestHeaders = (HttpRequestHeaders)map;

    HttpRequestHead head = new HttpRequestHead(
      requestHeaders.scheme(),
      requestHeaders.method(),
      requestHeaders.path(),
      map,
      requestHeaders.authority(),
      null,
      null
    );
    map.sanitize();
    if (handler != null) {
      context.dispatch(head, handler);
    }
  }

  public final Future<Void> writeHead(HttpResponseHead head, Buffer chunk, boolean end) {
    Promise<Void> promise = context.promise();
    HttpResponseHeaders headers = (HttpResponseHeaders)head.headers();
    headers.status(head.statusCode);
    if (chunk != null) {
      writeHeaders(headers, false, false, null);
      writeData(((BufferInternal)chunk).getByteBuf(), end, promise);
    } else {
      writeHeaders(headers, end, true, promise);
    }
    return promise.future();
  }

  public void routed(String route) {
    if (METRICS_ENABLED) {
      EventLoop eventLoop = vertx.getOrCreateContext().nettyEventLoop();
      if (!eventLoop.inEventLoop()) {
        eventLoop.execute(() -> observeRoute(route));
      } else {
        observeRoute(route);
      }
    }
  }

  public Future<HttpServerStream> sendPush(HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriority priority) {
    Promise<Http2ServerStream> promise = context.promise();
    connection.sendPush(id(), authority, method, headers, path, priority(), promise);
    return promise.future().map(pushStream -> {
      pushStream.priority(priority()); // Necessary ???
      HttpResponseHeaders mmap = new HttpResponseHeaders(new DefaultHttp2Headers());
      if (headers != null) {
        mmap.addAll(headers);
      }
      mmap.status(200);
      DefaultHttp2ServerStream s = (DefaultHttp2ServerStream)pushStream;
      s.observePush(mmap);
      return pushStream.unwrap();
    });
  }

  private void observeRoute(String route) {
    if (observable != null && !isTrailersSent()) {
      observable.observeRoute(route);
    }
  }

  private void observePush(HttpResponseHeaders headers) {
    if (observable != null) {
      observable.observePush(headers, method, uri);
    }
  }

  @Override
  public HttpServerStream setWriteQueueMaxSize(int maxSize) {
    // ????
    return this;
  }

  @Override
  public HttpServerStream unwrap() {
    return this;
  }
}
