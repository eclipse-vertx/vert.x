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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

class Http2ServerStream extends VertxHttp2Stream {

  private final Http2ServerConnection conn;
  private final String serverOrigin;
  private Http2HeadersAdaptor headers;
  private String scheme;
  private HttpMethod method;
  private String uri;
  private boolean hasAuthority;
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
  private boolean halfClosedRemote;
  private boolean requestEnded;
  private boolean responseEnded;
  Http2ServerStreamHandler request;
  private final Handler<HttpServerRequest> requestHandler;
  final int promisedId;

  Http2ServerStream(Http2ServerConnection conn,
                    String serverOrigin,
                    HttpServerMetrics serverMetrics,
                    Object socketMetric,
                    ContextInternal context,
                    Handler<HttpServerRequest> requestHandler,
                    boolean handle100ContinueAutomatically,
                    int maxFormAttributeSize,
                    int maxFormFields,
                    int maxFormBufferedBytes,
                    Http2HeadersAdaptor headers,
                    HttpMethod method,
                    String uri,
                    TracingPolicy tracingPolicy,
                    boolean halfClosedRemote,
                    int promisedId) {
    super(conn, context);

    this.serverOrigin = serverOrigin;
    this.conn = conn;
    this.headers = headers;
    this.method = method;
    this.uri = uri;
    this.scheme = null;
    this.hasAuthority = false;
    this.authority = null;
    this.tracingPolicy = tracingPolicy;
    this.halfClosedRemote = halfClosedRemote;
    this.requestHandler = requestHandler;
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    this.maxFormAttributeSize = maxFormAttributeSize;
    this.maxFormFields = maxFormFields;
    this.maxFormBufferedBytes = maxFormBufferedBytes;
    this.serverMetrics = serverMetrics;
    this.socketMetric = socketMetric;
    this.promisedId = promisedId;
  }

  Http2ServerStream(Http2ServerConnection conn,
                    String serverOrigin,
                    HttpServerMetrics serverMetrics,
                    Object socketMetric,
                    ContextInternal context,
                    Handler<HttpServerRequest> requestHandler,
                    boolean handle100ContinueAutomatically,
                    int maxFormAttributeSize,
                    int maxFormFields,
                    int maxFormBufferedBytes,
                    TracingPolicy tracingPolicy,
                    boolean halfClosedRemote) {
    super(conn, context);

    this.conn = conn;
    this.serverOrigin = serverOrigin;
    this.tracingPolicy = tracingPolicy;
    this.halfClosedRemote = halfClosedRemote;
    this.requestHandler = requestHandler;
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    this.serverMetrics = serverMetrics;
    this.socketMetric = socketMetric;
    this.promisedId = -1;
    this.maxFormAttributeSize = maxFormAttributeSize;
    this.maxFormFields = maxFormFields;
    this.maxFormBufferedBytes = maxFormBufferedBytes;
  }

  Http2ServerConnection connection() {
    return conn;
  }

  Http2HeadersAdaptor headers() {
    return headers;
  }

  String uri() {
    return uri;
  }

  String scheme() {
    return scheme;
  }

  HostAndPort authority() {
    return authority;
  }

  boolean hasAuthority() {
    return hasAuthority;
  }

  void registerMetrics() {
    if (METRICS_ENABLED) {
      if (serverMetrics != null) {
        if (request.response().isPush()) {
          metric = serverMetrics.responsePushed(socketMetric, method(), uri, request.response());
        } else {
          metric = serverMetrics.requestBegin(socketMetric, (HttpRequest) request);
        }
      }
    }
  }

  boolean onHeaders(Http2HeadersAdaptor headers, StreamPriority streamPriority) {

    CharSequence methodHeader = headers.method();
    if (methodHeader == null) {
      return false;
    }
    HttpMethod method = HttpMethod.valueOf(methodHeader.toString());

    CharSequence schemeHeader = headers.scheme();
    String scheme = schemeHeader != null ? schemeHeader.toString() : null;

    CharSequence pathHeader = headers.path();
    String uri = pathHeader != null ? pathHeader.toString() : null;

    HostAndPort authority = null;
    String authorityHeaderAsString;
    CharSequence authorityHeader = headers.authority();
    if (authorityHeader != null) {
      authorityHeaderAsString = authorityHeader.toString();
      authority = HostAndPort.parseAuthority(authorityHeaderAsString, -1);
    }

    CharSequence hostHeader = headers.get(HttpHeaders.HOST);
    if (authority == null) {
      headers.remove(HttpHeaders.HOST);
      if (hostHeader != null) {
        authority = HostAndPort.parseAuthority(hostHeader.toString(), -1);
      }
    }

    if (method == HttpMethod.CONNECT) {
      if (scheme != null || uri != null || authority == null) {
        return false;
      }
    } else {
      if (scheme == null || uri == null || uri.length() == 0) {
        return false;
      }
    }

    boolean hasAuthority = authorityHeader != null || hostHeader != null;
    if (hasAuthority) {
      if (authority == null) {
        return false;
      }
      if (hostHeader != null) {
        HostAndPort host = HostAndPort.parseAuthority(hostHeader.toString(), -1);
        if (host == null || (!authority.host().equals(host.host()) || authority.port() != host.port())) {
          return false;
        }
      }
    }

    // Sanitize headers
    headers.authority(null);
    headers.path(null);
    headers.method(null);
    headers.scheme(null);
    headers.path(null);

    this.method = method;
    this.isConnect = method == HttpMethod.CONNECT;
    this.uri = uri;
    this.authority = authority;
    this.scheme = scheme;
    this.hasAuthority = hasAuthority;
    this.headers = headers;
    this.request = new Http2ServerRequest(this, maxFormAttributeSize, maxFormFields, maxFormBufferedBytes, serverOrigin, headers);

    if (streamPriority != null) {
      priority(streamPriority);
    }
    registerMetrics();
    CharSequence value = headers.get(HttpHeaderNames.EXPECT);
    if (handle100ContinueAutomatically &&
      ((value != null && HttpHeaderValues.CONTINUE.equals(value)) ||
        headers.contains(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE, true))) {
      request.response().writeContinue();
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      trace = tracer.receiveRequest(context, SpanKind.RPC, tracingPolicy, request, method().name(), headers, HttpUtils.SERVER_REQUEST_TAG_EXTRACTOR);
    }
    request.dispatch(requestHandler);

    return true;
  }

  @Override
  void onEnd(MultiMap trailers) {
    requestEnded = true;
    if (Metrics.METRICS_ENABLED) {
      if (serverMetrics != null) {
        serverMetrics.requestEnd(metric, (HttpRequest) request, bytesRead());
      }
    }
    super.onEnd(trailers);
  }

  @Override
  void doWriteHeaders(Http2HeadersAdaptor headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    if (Metrics.METRICS_ENABLED && !end) {
      if (serverMetrics != null) {
        serverMetrics.responseBegin(metric, request.response());
      }
    }
    super.doWriteHeaders(headers, end, checkFlush, promise);
  }

  @Override
  protected void doWriteReset(long code, Promise<Void> promise) {
    if (!requestEnded || !responseEnded) {
      super.doWriteReset(code, promise);
    } else {
      promise.fail("Request ended");
    }
  }

  @Override
  void handleWriteQueueDrained() {
    request.response().handleWriteQueueDrained();
  }

  public HttpMethod method() {
    return method;
  }

  @Override
  protected void endWritten() {
    responseEnded = true;
    if (METRICS_ENABLED) {
      if (serverMetrics != null) {
        serverMetrics.responseEnd(metric, request.response(), bytesWritten());
      }
    }
  }

  @Override
  void handleClose() {
    super.handleClose();
    if (request != null) {
      request.handleClose();
    }
  }

  @Override
  void handleReset(long errorCode) {
    if (request != null) {
      request.handleReset(errorCode);
    }
  }

  @Override
  void handleException(Throwable cause) {
    if (request != null) {
      request.handleException(cause);
    }
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
      // Null in case of push response : handle this case
      if (serverMetrics != null && (!requestEnded || !responseEnded)) {
        serverMetrics.requestReset(metric);
      }
    }
    if (request != null) {
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
    if (serverMetrics != null && !responseEnded) {
      serverMetrics.requestRouted(metric, route);
    }
  }
}
