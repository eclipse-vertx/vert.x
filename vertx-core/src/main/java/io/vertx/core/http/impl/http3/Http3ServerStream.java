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
package io.vertx.core.http.impl.http3;

import io.netty.channel.EventLoop;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriorityBase;
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

public class Http3ServerStream extends Http3StreamBase {

  private final Http3ServerConnection connection;
  private Http3HeadersMultiMap requestHeaders;
  private Http3HeadersMultiMap responseHeaders;
  private String scheme;
  private HttpMethod method;
  private String uri;
  private HostAndPort authority;
  private Http3ServerStreamHandler handler;

  // Observability
  private final HttpServerMetrics serverMetrics;
  private final Object socketMetric;
  private final TracingPolicy tracingPolicy;
  private Object metric;
  private Object trace;
  private HttpRequest observableRequest;
  private HttpResponse observableResponse;

  public Http3ServerStream(Http3ServerConnection connection,
                           HttpServerMetrics serverMetrics,
                           Object socketMetric,
                           ContextInternal context,
                           Http3HeadersMultiMap requestHeaders,
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
    this.authority = null;
    this.tracingPolicy = tracingPolicy;
    this.serverMetrics = serverMetrics;
    this.socketMetric = socketMetric;
  }

  public Http3ServerStream(Http3ServerConnection connection,
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
      observableRequest = new HttpRequestHead(method, uri, requestHeaders, authority, null, null);
    }
    return observableRequest;
  }

  private HttpResponse observableResponse() {
    if (observableResponse == null) {
      observableResponse = new HttpResponseHead(HttpVersion.HTTP_3, responseHeaders.status(), null, responseHeaders);
    }
    return observableResponse;
  }

  public Http3ServerStream handler(Http3ServerStreamHandler handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public Http3ServerStreamHandler handler() {
    return handler;
  }

  public Http3HeadersMultiMap headers() {
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
    return authority;
  }

  public Object metric() {
    return metric;
  }

  @Override
  public Http3ServerConnection connection() {
    return connection;
  }

  public void onHeaders(Http3HeadersMultiMap headers) {

    this.method = headers.method();
    this.uri = headers.path();
    this.authority = headers.authority();
    this.scheme = headers.scheme();
    this.requestHeaders = headers;

    super.onHeaders(headers);
  }

  @Override
  void writeHeaders0(Http3HeadersMultiMap headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    // Work around
    responseHeaders = headers;
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

  public Future<Http3ServerStream> sendPush(HostAndPort authority, HttpMethod method, MultiMap headers, String path) {
    Promise<Http3ServerStream> promise = context.promise();
    connection.sendPush(id(), authority, method, headers, path, priority(), promise);
    return promise.future().andThen(ar -> {
      if (ar.succeeded()) {
        Http3ServerStream pushStream = ar.result();
        pushStream.priority(priority()); // Necessary ???
        Http3HeadersMultiMap mmap = connection.newHeaders();
        if (headers != null) {
          mmap.addAll(headers);
        }
        mmap.status(200);
        pushStream.responseHeaders = mmap;
        pushStream.observablePush();
      }
    });
  }

  @Override
  protected void observeOutboundHeaders(Http3HeadersMultiMap headers) {
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
  protected void observeInboundHeaders(Http3HeadersMultiMap headers) {
    if (METRICS_ENABLED) {
      if (serverMetrics != null) {
        metric = serverMetrics.requestBegin(socketMetric, observableRequest());
      }
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      trace = tracer.receiveRequest(context, SpanKind.RPC, tracingPolicy, handler, method().name(), headers, HttpUtils.SERVER_REQUEST_TAG_EXTRACTOR);
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
  protected StreamPriorityBase createDefaultStreamPriority() {
    return HttpUtils.DEFAULT_QUIC_STREAM_PRIORITY;
  }
}
