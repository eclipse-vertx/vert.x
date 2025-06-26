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
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

public class Http2ServerStream extends Http2StreamBase {

  private final Http2ServerConnection conn;
  private Http2HeadersMultiMap requestHeaders;
  private Http2HeadersMultiMap responseHeaders;
  private String scheme;
  private HttpMethod method;
  private String uri;
  private HostAndPort authority;
  private final HttpServerMetrics serverMetrics;
  private final Object socketMetric;
  private final TracingPolicy tracingPolicy;
  private Object metric;
  private Object trace;
  private Http2ServerStreamHandler handler;
  private HttpRequest observableRequest;
  private HttpResponse observableResponse;

  public Http2ServerStream(Http2ServerConnection conn,
                           HttpServerMetrics serverMetrics,
                           Object socketMetric,
                           ContextInternal context,
                           Http2HeadersMultiMap requestHeaders,
                           HttpMethod method,
                           String uri,
                           TracingPolicy tracingPolicy,
                           int promisedId) {
    super(promisedId, conn, context, true);

    this.conn = conn;
    this.requestHeaders = requestHeaders;
    this.method = method;
    this.uri = uri;
    this.scheme = null;
    this.authority = null;
    this.tracingPolicy = tracingPolicy;
    this.serverMetrics = serverMetrics;
    this.socketMetric = socketMetric;
  }

  public Http2ServerStream(Http2ServerConnection conn,
                           HttpServerMetrics serverMetrics,
                           Object socketMetric,
                           ContextInternal context,
                           TracingPolicy tracingPolicy) {
    super(conn, context);

    this.conn = conn;
    this.tracingPolicy = tracingPolicy;
    this.serverMetrics = serverMetrics;
    this.socketMetric = socketMetric;
  }

  private HttpRequest observableRequest() {
    if (observableRequest == null) {
      observableRequest = new HttpRequestHead(method, uri, requestHeaders, authority, null, null);
    }
    return observableRequest;
  }

  private HttpResponse observableResponse() {
    if (observableResponse == null) {
      observableResponse = new HttpResponseHead(HttpVersion.HTTP_2, responseHeaders.status(), null, responseHeaders);
    }
    return observableResponse;
  }

  public Http2ServerStream handler(Http2ServerStreamHandler handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public Http2ServerStreamHandler handler() {
    return handler;
  }

  public Http2ServerConnection connection() {
    return conn;
  }

  public Http2HeadersMultiMap headers() {
    return requestHeaders;
  }

  public String uri() {
    return uri;
  }

  public String scheme() {
    return scheme;
  }

  public HostAndPort authority() {
    return authority;
  }

  private void registerMetrics() {
    if (METRICS_ENABLED) {
      if (serverMetrics != null) {
        metric = serverMetrics.requestBegin(socketMetric, observableRequest());
      }
    }
  }

  public void onHeaders(Http2HeadersMultiMap headers) {

    this.method = headers.method();
    this.isConnect = method == HttpMethod.CONNECT;
    this.uri = headers.path();
    this.authority = headers.authority();
    this.scheme = headers.scheme();
    this.requestHeaders = headers;

    registerMetrics();

    //
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      trace = tracer.receiveRequest(context, SpanKind.RPC, tracingPolicy, handler, method().name(), headers, HttpUtils.SERVER_REQUEST_TAG_EXTRACTOR);
    }

    //
    context.execute(headers, this::handleHead);
  }

  private void handleHead(Http2HeadersMultiMap map) {
    handler.handleHead(map);
  }

  @Override
  public void onTrailers(Http2HeadersMultiMap trailers) {
    if (Metrics.METRICS_ENABLED) {
      if (serverMetrics != null) {
        serverMetrics.requestEnd(metric, observableRequest(), bytesRead());
      }
    }
    super.onTrailers(trailers);
  }

  @Override
  void writeHeaders0(Http2HeadersMultiMap headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    // Work around
    responseHeaders = headers;
    if (Metrics.METRICS_ENABLED && !end) {
      if (serverMetrics != null) {
        serverMetrics.responseBegin(metric, observableResponse());
      }
    }
    super.writeHeaders0(headers, end, checkFlush, promise);
  }

  public HttpMethod method() {
    return method;
  }

  @Override
  protected void endWritten() {
    if (METRICS_ENABLED) {
      if (serverMetrics != null) {
        serverMetrics.responseEnd(metric, observableResponse, bytesWritten());
      }
    }
  }

  @Override
  public void onClose() {
    if (METRICS_ENABLED) {
      // Null in case of push response : handle this case
      if (serverMetrics != null && (!isTrailersReceived() || !isTrailersSent())) {
        serverMetrics.requestReset(metric);
      }
    }
    if (handler != null) {
      VertxTracer tracer = context.tracer();
      Object trace = this.trace;
      if (tracer != null && trace != null) {
        Throwable failure;
        synchronized (conn) {
          if (!isTrailersReceived() || !isTrailersSent()) {
            failure = HttpUtils.STREAM_CLOSED_EXCEPTION;
          } else {
            failure = null;
          }
        }
        tracer.sendResponse(context, failure == null ? observableResponse() : null, trace, failure, HttpUtils.SERVER_RESPONSE_TAG_EXTRACTOR);
      }
    }
    super.onClose();
  }

  public Object metric() {
    return metric;
  }

  public void routed(String route) {
    if (METRICS_ENABLED) {
      EventLoop eventLoop = vertx.getOrCreateContext().nettyEventLoop();
      synchronized (this) {
        if (!eventLoop.inEventLoop()) {
          eventLoop.execute(() -> routedInternal(route));
          return;
        }
      }
      routedInternal(route);
    }
  }

  private void routedInternal(String route) {
    if (serverMetrics != null && !isTrailersSent()) {
      serverMetrics.requestRouted(metric, route);
    }
  }

  public Future<Http2ServerStream> sendPush(HostAndPort authority, HttpMethod method, MultiMap headers, String path) {
    Promise<Http2ServerStream> promise = context.promise();
    conn.sendPush(id(), authority, method, headers, path, priority(), promise);
    return promise.future().andThen(ar -> {
      if (ar.succeeded()) {
        Http2ServerStream pushStream = ar.result();
        pushStream.priority(priority()); // Necessary ???
        Http2HeadersMultiMap mmap = conn.newHeaders();
        if (headers != null) {
          mmap.addAll(headers);
        }
        mmap.status(200);
        pushStream.registerPushMetrics(mmap);
      }
    });
  }

  private void registerPushMetrics(Http2HeadersMultiMap headers) {
    responseHeaders = headers;
    if (METRICS_ENABLED) {
      if (serverMetrics != null) {
        metric = serverMetrics.responsePushed(socketMetric, method(), uri, observableResponse());
      }
    }
  }
}
