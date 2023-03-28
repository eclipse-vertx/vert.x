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

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

class Http2ServerStream extends VertxHttp2Stream<Http2ServerConnection> {

  protected final Http2Headers headers;
  protected final HttpMethod method;
  protected final String uri;
  protected final String host;
  private final TracingPolicy tracingPolicy;
  private Object metric;
  private Object trace;
  private boolean halfClosedRemote;
  private boolean requestEnded;
  private boolean responseEnded;
  Http2ServerStreamHandler request;

  Http2ServerStream(Http2ServerConnection conn,
                    ContextInternal context,
                    HttpMethod method,
                    String uri,
                    TracingPolicy tracingPolicy,
                    boolean halfClosedRemote) {
    super(conn, context);

    this.headers = null;
    this.method = method;
    this.uri = uri;
    this.host = null;
    this.tracingPolicy = tracingPolicy;
    this.halfClosedRemote = halfClosedRemote;
  }

  Http2ServerStream(Http2ServerConnection conn, ContextInternal context, Http2Headers headers, String serverOrigin, TracingPolicy tracingPolicy, boolean halfClosedRemote) {
    super(conn, context);

    String host = headers.get(":authority") != null ? headers.get(":authority").toString() : null;
    if (host == null) {
      int idx = serverOrigin.indexOf("://");
      host = serverOrigin.substring(idx + 3);
    }

    this.headers = headers;
    this.host = host;
    this.uri = headers.get(":path") != null ? headers.get(":path").toString() : null;
    this.method = headers.get(":method") != null ? HttpMethod.valueOf(headers.get(":method").toString()) : null;
    this.tracingPolicy = tracingPolicy;
    this.halfClosedRemote = halfClosedRemote;
  }

  void registerMetrics() {
    if (METRICS_ENABLED) {
      HttpServerMetrics metrics = conn.metrics();
      if (metrics != null) {
        if (request.response().isPush()) {
          metric = metrics.responsePushed(conn.metric(), method(), uri, request.response());
        } else {
          metric = metrics.requestBegin(conn.metric(), (HttpRequest) request);
        }
      }
    }
  }

  @Override
  void onHeaders(Http2Headers headers, StreamPriority streamPriority) {
    if (streamPriority != null) {
      priority(streamPriority);
    }
    registerMetrics();
    CharSequence value = headers.get(HttpHeaderNames.EXPECT);
    if (conn.options.isHandle100ContinueAutomatically() &&
      ((value != null && HttpHeaderValues.CONTINUE.equals(value)) ||
        headers.contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE))) {
      request.response().writeContinue();
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      trace = tracer.receiveRequest(context, SpanKind.RPC, tracingPolicy, request, method().name(), new Http2HeadersAdaptor(headers), HttpUtils.SERVER_REQUEST_TAG_EXTRACTOR);
    }
    request.dispatch(conn.requestHandler);
  }

  @Override
  void onEnd(MultiMap trailers) {
    requestEnded = true;
    if (Metrics.METRICS_ENABLED) {
      HttpServerMetrics metrics = conn.metrics();
      if (metrics != null) {
        metrics.requestEnd(metric, (HttpRequest) request, bytesRead());
      }
    }
    super.onEnd(trailers);
  }

  @Override
  void doWriteHeaders(Http2Headers headers, boolean end, Promise<Void> promise) {
    if (Metrics.METRICS_ENABLED && !end) {
      HttpServerMetrics metrics = conn.metrics();
      if (metrics != null) {
        metrics.responseBegin(metric, request.response());
      }
    }
    super.doWriteHeaders(headers, end, promise);
  }

  @Override
  protected void doWriteReset(long code) {
    if (!requestEnded || !responseEnded) {
      super.doWriteReset(code);
    }
  }

  @Override
  void handleWritabilityChanged(boolean writable) {
    request.response().handlerWritabilityChanged(writable);
  }

  public HttpMethod method() {
    return method;
  }

  @Override
  protected void endWritten() {
    responseEnded = true;
    if (METRICS_ENABLED) {
      HttpServerMetrics metrics = conn.metrics();
      if (metrics != null) {
        metrics.responseEnd(metric, request.response(), bytesWritten());
      }
    }
  }

  @Override
  void handleClose() {
    super.handleClose();
    request.handleClose();
  }

  @Override
  void handleReset(long errorCode) {
    request.handleReset(errorCode);
  }

  @Override
  void handleException(Throwable cause) {
    request.handleException(cause);
  }

  @Override
  void handleCustomFrame(HttpFrame frame) {
    request.handleCustomFrame(frame);
  }

  @Override
  void handlePriorityChange(StreamPriority newPriority) {
    request.handlePriorityChange(newPriority);
  }

  @Override
  void handleData(Buffer buf) {
    request.handleData(buf);
  }

  @Override
  void handleEnd(MultiMap trailers) {
    halfClosedRemote = true;
    request.handleEnd(trailers);
  }

  @Override
  void onClose() {
    if (METRICS_ENABLED) {
      HttpServerMetrics metrics = conn.metrics();
      // Null in case of push response : handle this case
      if (metrics != null && (!requestEnded || !responseEnded)) {
        metrics.requestReset(metric);
      }
    }
    request.onClose();
    VertxTracer tracer = context.tracer();
    Object trace = this.trace;
    if (tracer != null && trace != null) {
      Throwable failure;
      synchronized (conn) {
        if (!halfClosedRemote && (!requestEnded || !responseEnded)) {
          failure = HttpUtils.STREAM_CLOSED_EXCEPTION;
        } else {
          failure = null;
        }
      }
      tracer.sendResponse(context, failure == null ? request.response() : null, trace, failure, HttpUtils.SERVER_RESPONSE_TAG_EXTRACTOR);
    }
    super.onClose();
  }

  public Object metric() {
    return metric;
  }

  public HttpServerRequest routed(String route) {
    if (METRICS_ENABLED) {
      HttpServerMetrics metrics = conn.metrics();
      if (metrics != null && !responseEnded) {
        metrics.requestRouted(metric, route);
      }
    }
    return null;
  }
}
