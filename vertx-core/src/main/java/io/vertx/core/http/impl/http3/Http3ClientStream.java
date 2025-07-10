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
package io.vertx.core.http.impl.http3;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.impl.MessageWrite;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3ClientStream extends Http3StreamBase {

  // Temporary id assignments
  private static final AtomicInteger id_seq = new AtomicInteger(-1);

  private final Http3ClientConnection connection;
  private final TracingPolicy tracingPolicy;
  private final boolean decompressionSupported;
  private final ClientMetrics clientMetrics;
  private Http3ClientStreamHandler handler;
  private HttpResponseHead responseHead;
  private HttpRequestHead requestHead;
  private Object metric;
  private Object trace;

  public Http3ClientStream(Http3ClientConnection connection, ContextInternal context, TracingPolicy tracingPolicy,
                           boolean decompressionSupported, ClientMetrics clientMetrics) {
    this(id_seq.getAndDecrement(), connection, context, tracingPolicy, decompressionSupported, clientMetrics, true);
  }

  public Http3ClientStream(int id, Http3ClientConnection connection, ContextInternal context, TracingPolicy tracingPolicy,
                           boolean decompressionSupported, ClientMetrics clientMetrics, boolean writable) {
    super(id, connection, context, writable);

    this.connection = connection;
    this.tracingPolicy = tracingPolicy;
    this.decompressionSupported = decompressionSupported;
    this.clientMetrics = clientMetrics;
  }

  @Override
  public Http3ClientStreamHandler handler() {
    return handler;
  }

  public Http3ClientStream handler(Http3ClientStreamHandler handler) {
    this.handler = handler;
    return this;
  }

  public void upgrade(Object metric, Object trace) {
    this.metric = metric;
    this.trace = trace;
  }

  void writeHeaders(HttpRequestHead request, ByteBuf buf, boolean end, StreamPriorityBase priority, Promise<Void> promise) {
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
      Http3HeadersMultiMap headers = connection.newHeaders();
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
      if (request.headers != null && request.headers.size() > 0) {
        for (Map.Entry<String, String> header : request.headers) {
          headers.add(HttpUtils.toLowerCase(header.getKey()), header.getValue());
        }
      }
      if (decompressionSupported && headers.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
        headers.set(HttpHeaderNames.ACCEPT_ENCODING, Http1xClientConnection.determineCompressionAcceptEncoding());
      }
      request.remoteAddress = ((HttpConnection) connection).remoteAddress();
      requestHead = request;
      try {
        connection.createStream(Http3ClientStream.this, streamChannel_ -> {
          if (buf != null) {
            writeHeaders0(headers, false, false, null);
            writeData0(buf, e, promise);
          } else {
            writeHeaders0(headers, e, true, promise);
          }
        });
      } catch (Exception ex) {
        promise.fail(ex);
        onException(ex);
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
  public Http3ClientConnection connection() {
    return connection;
  }

  public void onPush(Http3ClientStreamImpl pushStream, int promisedStreamId, Http3HeadersMultiMap headers, boolean writable) {
    Http3ClientPush push = new Http3ClientPush(headers, pushStream);
/*
    pushStream.stream.init(promisedStreamId, writable);
    if (clientMetrics != null) {
      Object metric = clientMetrics.requestBegin(headers.path().toString(), push);
      pushStream.stream.metric = metric;
      clientMetrics.requestEnd(metric, 0L);
    }
*/
    context.dispatch(push, this::handlePush);
  }

  void onContinue() {
    context.execute(null, v -> handleContinue());
  }

  void onEarlyHints(MultiMap headers) {
    context.execute(null, v -> handleEarlyHints(headers));
  }

  public void onHeaders(Http3HeadersMultiMap headers) {
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
    this.responseHead = new HttpResponseHead(HttpVersion.HTTP_3, headers.status(), statusMessage, headers);
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

  void handleContinue() {
    Http3ClientStreamHandler i = handler;
    if (i != null) {
      i.handleContinue();
    }
  }

  void handlePush(Http3ClientPush push) {
    Http3ClientStreamHandler i = handler;
    if (i != null) {
      i.handlePush(push);
    }
  }

  void handleEarlyHints(MultiMap headers) {
    Http3ClientStreamHandler i = handler;
    if (i != null) {
      i.handleEarlyHints(headers);
    }
  }

  @Override
  protected void observeOutboundHeaders(Http3HeadersMultiMap headers) {
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
  protected void observeInboundHeaders(Http3HeadersMultiMap headers) {
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

  @Override
  protected StreamPriorityBase createDefaultStreamPriority() {
    return HttpUtils.DEFAULT_QUIC_STREAM_PRIORITY;
  }

}
