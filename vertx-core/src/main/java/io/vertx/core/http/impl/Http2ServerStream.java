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

import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.EmptyHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

class Http2ServerStream extends VertxHttpStreamBase<Http2ServerConnection, Http2Stream> {
  private static final MultiMap EMPTY = new Http2HeadersAdaptor(EmptyHttp2Headers.INSTANCE);

  protected final Http2Headers headers;
  protected final String scheme;
  protected final HttpMethod method;
  protected final String uri;
  protected final boolean hasAuthority;
  protected final HostAndPort authority;
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
    this.scheme = null;
    this.hasAuthority = false;
    this.authority = null;
    this.tracingPolicy = tracingPolicy;
    this.halfClosedRemote = halfClosedRemote;
  }

  Http2ServerStream(Http2ServerConnection conn,
                    ContextInternal context,
                    Http2Headers headers,
                    String scheme,
                    boolean hasAuthority,
                    HostAndPort authority,
                    HttpMethod method,
                    String uri,
                    TracingPolicy tracingPolicy,
                    boolean halfClosedRemote) {
    super(conn, context);

    this.scheme = scheme;
    this.headers = headers;
    this.hasAuthority = hasAuthority;
    this.authority = authority;
    this.uri = uri;
    this.method = method;
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
  void onHeaders(VertxHttpHeaders headers, StreamPriorityBase streamPriority) {
    if (streamPriority != null) {
      priority(streamPriority);
    }
    registerMetrics();
    CharSequence value = headers.get(HttpHeaderNames.EXPECT);
    if (conn.options.isHandle100ContinueAutomatically() &&
      ((value != null && HttpHeaderValues.CONTINUE.equals(value)) ||
        headers.contains(String.valueOf(HttpHeaderNames.EXPECT), String.valueOf(HttpHeaderValues.CONTINUE)))) {
      request.response().writeContinue();
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      trace = tracer.receiveRequest(context, SpanKind.RPC, tracingPolicy, request, method().name(), headers.toHeaderAdapter(), HttpUtils.SERVER_REQUEST_TAG_EXTRACTOR);
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
  void doWriteHeaders(VertxHttpHeaders headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    if (Metrics.METRICS_ENABLED && !end) {
      HttpServerMetrics metrics = conn.metrics();
      if (metrics != null) {
        metrics.responseBegin(metric, request.response());
      }
    }
    super.doWriteHeaders(headers, end, checkFlush, promise);
  }

  @Override
  protected void doWriteReset(long code) {
    if (!requestEnded || !responseEnded) {
      super.doWriteReset(code);
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
  void handlePriorityChange(StreamPriorityBase newPriority) {
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
    HttpServerMetrics metrics = conn.metrics();
    if (metrics != null && !responseEnded) {
      metrics.requestRouted(metric, route);
    }
  }

  @Override
  protected void consumeCredits(Http2Stream stream, int len) {
    conn.consumeCredits(stream, len);
  }

  @Override
  public void writeFrame(Http2Stream stream, byte type, short flags, ByteBuf payload, Promise<Void> promise) {
    conn.handler.writeFrame(stream, type, flags, payload, (FutureListener<Void>) promise);
  }
  @Override
  public void writeHeaders(Http2Stream stream, VertxHttpHeaders headers, boolean end, StreamPriorityBase priority,
                           boolean checkFlush, FutureListener<Void> promise) {
    conn.handler.writeHeaders(stream, headers, end, priority.getDependency(), priority.getWeight(), priority.isExclusive(),
      checkFlush, promise);
  }

  @Override
  public void writePriorityFrame(StreamPriorityBase priority) {
    conn.handler.writePriority(stream, priority.getDependency(), priority.getWeight(), priority.isExclusive());
  }

  @Override
  public void writeData_(Http2Stream stream, ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    conn.handler.writeData(stream, chunk, end, promise);
  }

  @Override
  public void writeReset_(int streamId, long code) {
    conn.handler.writeReset(streamId, code);
  }

  @Override
  public void init_(VertxHttpStreamBase vertxHttpStream, Http2Stream stream) {
    this.stream = stream;
    this.writable = this.conn.handler.encoder().flowController().isWritable(this.stream);
    stream.setProperty(conn.streamKey, vertxHttpStream);
  }

  @Override
  public synchronized int getStreamId() {
    return stream != null ? stream.id() : -1;
  }

  @Override
  public boolean remoteSideOpen(Http2Stream stream) {
    return stream.state().remoteSideOpen();
  }

  @Override
  public MultiMap getEmptyHeaders() {
    return EMPTY;
  }

  @Override
  public boolean isWritable_() {
    return writable;
  }

  @Override
  public boolean isTrailersReceived() {
    return stream.isTrailersReceived();
  }

  @Override
  protected StreamPriorityBase createDefaultStreamPriority() {
    return HttpUtils.DEFAULT_STREAM_PRIORITY;
  }
}