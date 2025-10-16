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
import io.netty.util.AsciiString;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.net.impl.MessageWrite;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ClientStream extends Http2StreamBase<Http2ClientStream> implements HttpClientStream {

  // Temporary id assignments
  private static final AtomicInteger id_seq = new AtomicInteger(-1);

  private final Http2ClientConnection connection;
  private final TracingPolicy tracingPolicy;
  private final boolean decompressionSupported;
  private final ClientMetrics clientMetrics;
  private HttpResponseHead responseHead;
  private HttpRequestHead requestHead;
  private Object metric;
  private Object trace;

  // Handlers
  private Handler<HttpResponseHead> headersHandler;
  private Handler<Void> continueHandler;
  private Handler<HttpClientPush> pushHandler;
  private Handler<MultiMap> earlyHintsHandler;

  public Http2ClientStream(Http2ClientConnection connection, ContextInternal context, TracingPolicy tracingPolicy,
                           boolean decompressionSupported, ClientMetrics clientMetrics) {
    this(id_seq.getAndDecrement(), connection, context, tracingPolicy, decompressionSupported, clientMetrics, true);
  }

  public Http2ClientStream(int id, Http2ClientConnection connection, ContextInternal context, TracingPolicy tracingPolicy,
                           boolean decompressionSupported, ClientMetrics clientMetrics, boolean writable) {
    super(id, connection, context, writable);

    this.connection = connection;
    this.tracingPolicy = tracingPolicy;
    this.decompressionSupported = decompressionSupported;
    this.clientMetrics = clientMetrics;
  }

  public void upgrade(Object metric, Object trace) {
    this.metric = metric;
    this.trace = trace;
  }

  @Override
  public Future<Void> writeHead(HttpRequestHead request, boolean chunked, Buffer buf, boolean end, StreamPriority priority, boolean connect) {
    PromiseInternal<Void> promise = context.promise();
    priority(priority);
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
      Http2HeadersMultiMap headers = connection.newHeaders();
      headers.method(request.method);
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
      request.remoteAddress = ((HttpConnection) connection).remoteAddress();
      requestHead = request;
      try {
        connection.createStream(Http2ClientStream.this);
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
    return metric;
  }

  public Object trace() {
    return trace;
  }

  @Override
  public Http2ClientConnection connection() {
    return connection;
  }

  public void onPush(Http2ClientStream pushStream, int promisedStreamId, Http2HeadersMultiMap headers, boolean writable) {
    HttpClientPush push = new HttpClientPush(new HttpRequestHead(headers.method(), headers.path(), headers, headers.authority(), null, null), pushStream);
    pushStream.init(promisedStreamId, writable);
    if (clientMetrics != null) {
      Object metric = clientMetrics.requestBegin(headers.path().toString(), push);
      pushStream.metric = metric;
      clientMetrics.requestEnd(metric, 0L);
    }
    context.execute(push, this::handlePush);
  }

  void onContinue() {
    context.execute(null, v -> handleContinue());
  }

  void onEarlyHints(MultiMap headers) {
    context.execute(null, v -> handleEarlyHints(headers));
  }

  public void onHeaders(Http2HeadersMultiMap headers) {
    int status = headers.status();
    if (status == 100) {
      onContinue();
      return;
    } else if (status == 103) {
      MultiMap headersMultiMap = HeadersMultiMap.httpHeaders();
      headers.remove(HttpHeaders.PSEUDO_STATUS);
      for (Map.Entry<String, String> header : headers) {
        headersMultiMap.add(header.getKey(), header.getValue());
      }
      onEarlyHints(headersMultiMap);
      return;
    }
    String statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
    this.responseHead = new HttpResponseHead(headers.status(), statusMessage, headers);
    super.onHeaders(headers);
  }

  @Override
  public void onClose() {
    if (!isTrailersReceived()) {
      // NOT SURE OF THAT
      onException(HttpUtils.STREAM_CLOSED_EXCEPTION);
    }
    super.onClose();
  }

  public Http2ClientStream headHandler(Handler<HttpResponseHead> handler) {
    headersHandler = handler;
    return this;
  }

  void handleHeader(Http2HeadersMultiMap map) {
    Handler<HttpResponseHead> handler = headersHandler;
    if (handler != null) {
      int status = map.status();
      String statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
      HttpResponseHead response = new HttpResponseHead(
        status,
        statusMessage,
        map);
      context.dispatch(response, handler);
    }
  }

  public Http2ClientStream continueHandler(Handler<Void> handler) {
    continueHandler = handler;
    return this;
  }

  void handleContinue() {
    Handler<Void> handler = continueHandler;
    if (handler != null) {
      context.dispatch(null, handler);
    }
  }

  public Http2ClientStream pushHandler(Handler<HttpClientPush> handler) {
    pushHandler = handler;
    return this;
  }

  void handlePush(HttpClientPush push) {
    Handler<HttpClientPush> handler = pushHandler;
    if (handler != null) {
      context.dispatch(push, handler);
    }
  }

  public Http2ClientStream earlyHintsHandler(Handler<MultiMap> handler) {
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
  protected void observeOutboundHeaders(Http2HeadersMultiMap headers) {
    if (clientMetrics != null) {
      metric = clientMetrics.requestBegin(requestHead.uri, requestHead);
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      BiConsumer<String, String> headers_ = (key, val) -> headers.add(key, val);
      String operation = requestHead.traceOperation;
      if (operation == null) {
        operation = requestHead.method().toString();
      }
      trace = tracer.sendRequest(context, SpanKind.RPC, tracingPolicy, requestHead, operation, headers_, HttpUtils.CLIENT_HTTP_REQUEST_TAG_EXTRACTOR);
    }
  }

  @Override
  protected void observeInboundTrailers() {
    if (clientMetrics != null) {
      clientMetrics.responseEnd(metric, bytesRead());
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null && trace != null) {
      tracer.receiveResponse(context, responseHead, trace, null, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
    }
  }

  @Override
  protected void observeReset() {
    if (clientMetrics != null) {
      clientMetrics.requestReset(metric);
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null && trace != null) {
      tracer.receiveResponse(context, responseHead, trace, HttpUtils.STREAM_CLOSED_EXCEPTION, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
    }
  }

  @Override
  protected void observeInboundHeaders(Http2HeadersMultiMap headers) {
    if (clientMetrics != null) {
      clientMetrics.responseBegin(metric, responseHead);
    }
  }

  @Override
  protected void observeOutboundTrailers() {
    if (clientMetrics != null) {
      clientMetrics.requestEnd(metric, bytesWritten());
    }
  }
}
