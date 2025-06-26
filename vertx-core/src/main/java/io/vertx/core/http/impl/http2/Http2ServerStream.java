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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

public class Http2ServerStream extends Http2StreamBase {

  private final Http2ServerConnection conn;
  private final String serverOrigin;
  private Http2HeadersMultiMap headers;
  private String scheme;
  private HttpMethod method;
  private String uri;
  private HostAndPort authority;
  private final HttpServerMetrics serverMetrics;
  private final Object socketMetric;
  private final TracingPolicy tracingPolicy;
  private final boolean handle100ContinueAutomatically;
  private final int maxFormAttributeSize;
  private final int maxFormFields;
  private final int maxFormBufferedBytes;
  private Object metric;
  private Object trace;
  private Http2ServerStreamHandler handler;
  private final Handler<HttpServerRequest> requestHandler; // I think we can get rid of this

  public Http2ServerStream(Http2ServerConnection conn,
                    String serverOrigin,
                    HttpServerMetrics serverMetrics,
                    Object socketMetric,
                    ContextInternal context,
                    Handler<HttpServerRequest> requestHandler,
                    boolean handle100ContinueAutomatically,
                    int maxFormAttributeSize,
                    int maxFormFields,
                    int maxFormBufferedBytes,
                    Http2HeadersMultiMap headers,
                    HttpMethod method,
                    String uri,
                    TracingPolicy tracingPolicy,
                    int promisedId) {
    super(promisedId, conn, context, true);

    this.serverOrigin = serverOrigin;
    this.conn = conn;
    this.headers = headers;
    this.method = method;
    this.uri = uri;
    this.scheme = null;
    this.authority = null;
    this.tracingPolicy = tracingPolicy;
    this.requestHandler = requestHandler;
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    this.maxFormAttributeSize = maxFormAttributeSize;
    this.maxFormFields = maxFormFields;
    this.maxFormBufferedBytes = maxFormBufferedBytes;
    this.serverMetrics = serverMetrics;
    this.socketMetric = socketMetric;
  }

  public Http2ServerStream(Http2ServerConnection conn,
                    String serverOrigin,
                    HttpServerMetrics serverMetrics,
                    Object socketMetric,
                    ContextInternal context,
                    Handler<HttpServerRequest> requestHandler,
                    boolean handle100ContinueAutomatically,
                    int maxFormAttributeSize,
                    int maxFormFields,
                    int maxFormBufferedBytes,
                    TracingPolicy tracingPolicy) {
    super(conn, context);

    this.conn = conn;
    this.serverOrigin = serverOrigin;
    this.tracingPolicy = tracingPolicy;
    this.requestHandler = requestHandler;
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    this.serverMetrics = serverMetrics;
    this.socketMetric = socketMetric;
    this.maxFormAttributeSize = maxFormAttributeSize;
    this.maxFormFields = maxFormFields;
    this.maxFormBufferedBytes = maxFormBufferedBytes;
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
    return headers;
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
        metric = serverMetrics.requestBegin(socketMetric, (HttpRequest) handler);
      }
    }
  }

  public void onHeaders(Http2HeadersMultiMap headers) {

    this.method = headers.method();
    this.isConnect = method == HttpMethod.CONNECT;
    this.uri = headers.path();
    this.authority = headers.authority();
    this.scheme = headers.scheme();
    this.headers = headers;
    this.handler = new Http2ServerRequest(this, context, maxFormAttributeSize, maxFormFields, maxFormBufferedBytes, serverOrigin, headers);

    registerMetrics();

    CharSequence value = headers.get(HttpHeaderNames.EXPECT);

    // SHOULD BE DONE IN RESPONSE
    if (handle100ContinueAutomatically &&
      ((value != null && HttpHeaderValues.CONTINUE.equals(value)) ||
        headers.contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE, true))) {
      handler.response().writeContinue();
    }

    //
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      trace = tracer.receiveRequest(context, SpanKind.RPC, tracingPolicy, handler, method().name(), headers, HttpUtils.SERVER_REQUEST_TAG_EXTRACTOR);
    }
    handler.dispatch(requestHandler);
  }

  @Override
  public void onTrailers(MultiMap trailers) {
    if (Metrics.METRICS_ENABLED) {
      if (serverMetrics != null) {
        serverMetrics.requestEnd(metric, (HttpRequest) handler, bytesRead());
      }
    }
    super.onTrailers(trailers);
  }

  @Override
  void writeHeaders0(Http2HeadersMultiMap headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    if (Metrics.METRICS_ENABLED && !end) {
      if (serverMetrics != null) {
        serverMetrics.responseBegin(metric, handler.response());
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
        serverMetrics.responseEnd(metric, handler.response(), bytesWritten());
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
        tracer.sendResponse(context, failure == null ? handler.response() : null, trace, failure, HttpUtils.SERVER_RESPONSE_TAG_EXTRACTOR);
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
        pushStream.registerPushMetrics(headers);
      }
    });
  }

  private void registerPushMetrics(MultiMap headers) {
    if (METRICS_ENABLED) {
      if (serverMetrics != null) {
        metric = serverMetrics.responsePushed(socketMetric, method(), uri, new HttpResponseHead(HttpVersion.HTTP_2, 200, null, headers));
      }
    }
  }
}
