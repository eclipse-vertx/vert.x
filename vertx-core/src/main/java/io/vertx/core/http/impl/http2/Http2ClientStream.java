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
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.Http1xClientConnection;
import io.vertx.core.http.impl.HttpClientPush;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.impl.MessageWrite;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ClientStream extends Http2StreamBase {

  private final Http2ClientConnection conn;
  private final TracingPolicy tracingPolicy;
  private final boolean decompressionSupported;
  private final ClientMetrics clientMetrics;
  private HttpResponseHead response;
  private Object metric;
  private Object trace;

  Http2ClientStreamHandler impl;

  public Http2ClientStream(Http2ClientConnection conn,
                    ContextInternal context,
                    TracingPolicy tracingPolicy,
                    boolean decompressionSupported,
                    ClientMetrics clientMetrics) {
    super(conn, context);

    this.conn = conn;
    this.tracingPolicy = tracingPolicy;
    this.decompressionSupported = decompressionSupported;
    this.clientMetrics = clientMetrics;
  }

  @Override
  public Http2StreamHandler handler() {
    return impl;
  }

  @Override
  public Http2ClientStream handler(Http2StreamHandler handler) {
    this.impl = (Http2ClientStreamHandler) handler;
    return this;
  }

  public void upgrade(int streamId, Object metric, Object trace, boolean writable) {
    init(streamId, writable);
    this.metric = metric;
    this.trace = trace;
    this.trailersSent = true;
  }

  private void createStream(HttpRequestHead head, Http2HeadersMultiMap headers) throws Exception {
    conn.createStream(this);
    head.remoteAddress = ((HttpConnection)conn).remoteAddress();
    if (clientMetrics != null) {
      metric = clientMetrics.requestBegin(headers.path().toString(), head);
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      BiConsumer<String, String> headers_ = (key, val) -> headers.add(key, val);
      String operation = head.traceOperation;
      if (operation == null) {
        operation = headers.method().toString();
      }
      trace = tracer.sendRequest(context, SpanKind.RPC, tracingPolicy, head, operation, headers_, HttpUtils.CLIENT_HTTP_REQUEST_TAG_EXTRACTOR);
    }
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
      Http2HeadersMultiMap headers = conn.newHeaders();
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
        headers.scheme(conn.isSsl() ? "https" : "http");
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
      try {
        createStream(request, headers);
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
  void writeHeaders0(Http2HeadersMultiMap headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    isConnect = headers.method() == HttpMethod.CONNECT;
    super.writeHeaders0(headers, end, checkFlush, promise);
  }

  @Override
  protected void writeReset0(long code, Promise<Void> promise) {
    if (!isTrailersSent() || !isTrailersReceived()) {
      super.writeReset0(code, promise);
    } else {
      promise.fail("Request ended");
    }
  }

  protected void endWritten() {
    super.endWritten();
    if (clientMetrics != null) {
      clientMetrics.requestEnd(metric, bytesWritten());
    }
  }

  public void onPush(Http2ClientStreamImpl pushStream, int promisedStreamId, Http2HeadersMultiMap headers, boolean writable) {
    HttpClientPush push = new HttpClientPush(headers, pushStream);
    pushStream.stream.init(promisedStreamId, writable);
    if (clientMetrics != null) {
      Object metric = clientMetrics.requestBegin(headers.path().toString(), push);
      pushStream.stream.metric = metric;
      clientMetrics.requestEnd(metric, 0L);
    }
    context.dispatch(push, this::handlePush);
  }

  void onContinue() {
    context.execute(null, v -> handleContinue());
  }

  void onEarlyHints(MultiMap headers) {
    context.execute(null, v -> handleEarlyHints(headers));
  }

  @Override
  public void onTrailers(MultiMap trailers) {
    if (clientMetrics != null) {
      clientMetrics.responseEnd(metric, bytesRead());
    }
    super.onTrailers(trailers);
  }

  @Override
  public void onReset(long code) {
    if (clientMetrics != null) {
      clientMetrics.requestReset(metric);
    }
    super.onReset(code);
  }

  public void onHeaders(Http2HeadersMultiMap headers) {
    int status = headers.status();
    String statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
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
    super.onHeaders(headers);
    response = new HttpResponseHead(
      HttpVersion.HTTP_2,
      status,
      statusMessage,
      headers);
    headers.remove(HttpHeaders.PSEUDO_STATUS);
    if (clientMetrics != null) {
      clientMetrics.responseBegin(metric, response);
    }
    context.execute(response, this::handleHead);
  }

  @Override
  public void onClose() {
    if (clientMetrics != null) {
      if (!isTrailersSent() || !isTrailersReceived()) {
        clientMetrics.requestReset(metric);
      }
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null && trace != null) {
      VertxException err;
      if (isTrailersReceived() && isTrailersSent()) {
        err = null;
      } else {
        err = HttpUtils.STREAM_CLOSED_EXCEPTION;
      }
      tracer.receiveResponse(context, response, trace, err, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
    }
    if (!isTrailersReceived()) {
      // NOT SURE OF THAT
      onException(HttpUtils.STREAM_CLOSED_EXCEPTION);
    }
    super.onClose();
  }

  void handleContinue() {
    Http2ClientStreamHandler i = impl;
    if (i != null) {
      i.handleContinue();
    }
  }

  void handlePush(HttpClientPush push) {
    Http2ClientStreamHandler i = impl;
    if (i != null) {
      i.handlePush(push);
    }
  }

  void handleEarlyHints(MultiMap headers) {
    Http2ClientStreamHandler i = impl;
    if (i != null) {
      i.handleEarlyHints(headers);
    }
  }

  void handleHead(HttpResponseHead response) {
    Http2ClientStreamHandler i = impl;
    if (i != null) {
      i.handleHead(response);
    }
  }
}
