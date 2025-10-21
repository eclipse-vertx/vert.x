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
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

class DefaultHttp2ServerStream extends DefaultHttp2Stream<DefaultHttp2ServerStream> implements HttpServerStream, Http2ServerStream {

  private final Http2ServerConnection connection;
  private HttpRequestHeaders requestHeaders;
  private HttpResponseHeaders responseHeaders;
  private String scheme;
  private HttpMethod method;
  private String uri;

  // Observability
  private final HttpServerMetrics serverMetrics;
  private final Object socketMetric;
  private final TracingPolicy tracingPolicy;
  private Object metric;
  private Object trace;
  private HttpRequest observableRequest;
  private HttpResponse observableResponse;

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

    this.connection = connection;
    this.requestHeaders = requestHeaders;
    this.method = method;
    this.uri = uri;
    this.scheme = null;
    this.tracingPolicy = tracingPolicy;
    this.serverMetrics = serverMetrics;
    this.socketMetric = socketMetric;
  }

  DefaultHttp2ServerStream(Http2ServerConnection connection,
                           HttpServerMetrics serverMetrics,
                           Object socketMetric,
                           ContextInternal context,
                           TracingPolicy tracingPolicy) {
    super(connection, context);

    this.connection = connection;
    this.tracingPolicy = tracingPolicy;
    this.serverMetrics = serverMetrics;
    this.socketMetric = socketMetric;
  }

  private HttpRequest observableRequest() {
    if (observableRequest == null) {
      observableRequest = new HttpRequestHead(method, uri, requestHeaders, requestHeaders.authority(), null, null);
    }
    return observableRequest;
  }

  private HttpResponse observableResponse() {
    if (observableResponse == null) {
      observableResponse = new HttpResponseHead(responseHeaders.status(), null, responseHeaders);
    }
    return observableResponse;
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
    return metric;
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
      requestHeaders.method(),
      requestHeaders.path(),
      map,
      requestHeaders.authority(),
      null,
      null
    );
    head.scheme = requestHeaders.scheme();
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

  @Override
  void writeHeaders0(HttpHeaders headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    // Work around
    if (headers instanceof HttpResponseHeaders) {
      responseHeaders = (HttpResponseHeaders) headers;
    }
    super.writeHeaders0(headers, end, checkFlush, promise);
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
      s.responseHeaders = mmap;
      s.observablePush();
      return pushStream.unwrap();
    });
  }

  @Override
  protected void observeOutboundHeaders(HttpHeaders headers) {
    if (Metrics.METRICS_ENABLED) {
      if (serverMetrics != null) {
        serverMetrics.responseBegin(metric, observableResponse());
      }
    }
    VertxTracer tracer = context.tracer();
    Object trace = this.trace;
    if (tracer != null && trace != null) {
      tracer.sendResponse(context, observableResponse(), trace, null, HttpUtils.SERVER_RESPONSE_TAG_EXTRACTOR);
    }
  }

  @Override
  protected void observeInboundTrailers() {
    if (Metrics.METRICS_ENABLED) {
      if (serverMetrics != null) {
        serverMetrics.requestEnd(metric, observableRequest(), bytesRead());
      }
    }
  }

  @Override
  protected void observeInboundHeaders(HttpHeaders headers) {
    if (METRICS_ENABLED) {
      if (serverMetrics != null) {
        metric = serverMetrics.requestBegin(socketMetric, observableRequest());
      }
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      trace = tracer.receiveRequest(context, SpanKind.RPC, tracingPolicy, this, method().name(), headers, HTTP_2_SERVER_STREAM_TAG_EXTRACTOR);
    }
  }

  @Override
  protected void observeOutboundTrailers() {
    if (serverMetrics != null) {
      serverMetrics.responseEnd(metric, observableResponse, bytesWritten());
    }
  }

  @Override
  protected void observeReset() {
    if (serverMetrics != null) {
      serverMetrics.requestReset(metric);
    }
    VertxTracer tracer = context.tracer();
    Object trace = this.trace;
    if (tracer != null && trace != null) {
      tracer.sendResponse(context, null, trace, HttpUtils.STREAM_CLOSED_EXCEPTION, HttpUtils.SERVER_RESPONSE_TAG_EXTRACTOR);
    }
  }

  private void observeRoute(String route) {
    if (serverMetrics != null && !isTrailersSent()) {
      serverMetrics.requestRouted(metric, route);
    }
  }

  private void observablePush() {
    if (serverMetrics != null) {
      metric = serverMetrics.responsePushed(socketMetric, method(), uri, observableResponse());
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

  public static final TagExtractor<DefaultHttp2ServerStream> HTTP_2_SERVER_STREAM_TAG_EXTRACTOR = new TagExtractor<>() {
    @Override
    public int len(DefaultHttp2ServerStream req) {
      return ((HttpRequestHeaders)req.headers()).path().indexOf('?') == -1 ? 4 : 5;
    }

    @Override
    public String name(DefaultHttp2ServerStream req, int index) {
      switch (index) {
        case 0:
          return "http.url";
        case 1:
          return "http.request.method";
        case 2:
          return "url.scheme";
        case 3:
          return "url.path";
        case 4:
          return "url.query";
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }

    @Override
    public String value(DefaultHttp2ServerStream req, int index) {
      int idx;
      switch (index) {
        case 0:
          String sb = req.scheme() +
            "://" +
            req.authority() +
            ((HttpRequestHeaders)req.headers()).path();
          return sb;
        case 1:
          return req.method().name();
        case 2:
          return req.scheme();
        case 3:
          String path = ((HttpRequestHeaders)req.headers()).path();
          idx = path.indexOf('?');
          if (idx > 0) {
            path = path.substring(0, idx);
          }
          return path;
        case 4:
          String query = ((HttpRequestHeaders)req.headers()).path();
          idx = query.indexOf('?');
          query = query.substring(idx + 1);
          return query;
      }
      throw new IndexOutOfBoundsException("Invalid tag index " + index);
    }
  };
}
