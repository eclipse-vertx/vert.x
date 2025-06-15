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
package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.impl.MessageWrite;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Http2ClientStream extends VertxHttp2Stream<Http2ClientConnection> {

  private final boolean push;
  private HttpResponseHead response;
  private Object metric;
  private Object trace;
  private boolean requestEnded;
  private boolean responseEnded;

  Http2ClientStream(Http2ClientConnection conn, ContextInternal context, boolean push) {
    super(conn, context);

    this.push = push;
  }

  void upgrade(Http2Stream stream, Object metric, Object trace) {
    init(stream);
    this.metric = metric;
    this.trace = trace;
    this.requestEnded = true;
  }

  private void createStream(HttpRequestHead head, Http2Headers headers) throws Http2Exception {
    Http2Stream stream = conn.createStream(head, headers);
    init(stream);
    ClientMetrics clientMetrics = conn.clientMetrics();
    if (clientMetrics != null) {
      metric = clientMetrics.requestBegin(headers.path().toString(), head);
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      BiConsumer<String, String> headers_ = (key, val) -> new Http2HeadersAdaptor(headers).add(key, val);
      String operation = head.traceOperation;
      if (operation == null) {
        operation = headers.method().toString();
      }
      trace = tracer.sendRequest(context, SpanKind.RPC, conn.client().options().getTracingPolicy(), head, operation, headers_, HttpUtils.CLIENT_HTTP_REQUEST_TAG_EXTRACTOR);
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
      Http2Headers headers = new DefaultHttp2Headers();
      headers.method(request.method.name());
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
      if (conn.client().options().isDecompressionSupported() && headers.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
        headers.set(HttpHeaderNames.ACCEPT_ENCODING, Http1xClientConnection.determineCompressionAcceptEncoding());
      }
      try {
        createStream(request, headers);
      } catch (Http2Exception ex) {
        promise.fail(ex);
        onException(ex);
        return;
      }
      if (buf != null) {
        doWriteHeaders(headers, false, false, null);
        doWriteData(buf, e, promise);
      } else {
        doWriteHeaders(headers, e, true, promise);
      }
    }

    @Override
    public void cancel(Throwable cause) {
      promise.fail(cause);
    }
  }

  void onPush(Http2Stream stream, Http2Headers headers) {
    Http2ClientStreamImpl pushStream = new Http2ClientStreamImpl(conn, context, true);
    pushStream.init(stream);
    HttpClientPush push = new HttpClientPush(headers, pushStream);
    ClientMetrics metrics = conn.clientMetrics();
    if (metrics != null) {
      Object metric = metrics.requestBegin(headers.path().toString(), push);
      ((Http2ClientStream)pushStream).metric = metric;
      metrics.requestEnd(metric, 0L);
    }
    context.dispatch(push, this::handlePush);
  }

  void onContinue() {
    context.emit(null, v -> handleContinue());
  }

  void onEarlyHints(MultiMap headers) {
    context.emit(null, v -> handleEarlyHints(headers));
  }

  abstract void handleContinue();

  abstract void handlePush(HttpClientPush push);

  abstract void handleEarlyHints(MultiMap headers);

  abstract void handleHead(HttpResponseHead response);

  public Object metric() {
    return metric;
  }

  public Object trace() {
    return trace;
  }

  @Override
  void doWriteHeaders(Http2Headers headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    isConnect = "CONNECT".contentEquals(headers.method());
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

  protected void endWritten() {
    requestEnded = true;
    ClientMetrics clientMetrics = conn.clientMetrics();
    if (clientMetrics != null) {
      clientMetrics.requestEnd(metric, bytesWritten());
    }
  }

  @Override
  void onEnd(MultiMap trailers) {
    ClientMetrics clientMetrics = conn.clientMetrics();
    if (clientMetrics != null) {
      clientMetrics.responseEnd(metric, bytesRead());
    }
    responseEnded = true;
    super.onEnd(trailers);
  }

  @Override
  void onReset(long code) {
    ClientMetrics clientMetrics = conn.clientMetrics();
    if (clientMetrics != null) {
      clientMetrics.requestReset(metric);
    }
    super.onReset(code);
  }

  @Override
  void onHeaders(Http2Headers headers, StreamPriority streamPriority) {
    if (streamPriority != null) {
      priority(streamPriority);
    }
    if (response == null) {
      int status;
      String statusMessage;
      try {
        status = Integer.parseInt(headers.status().toString());
        statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
      } catch (Exception e) {
        handleException(e);
        writeReset(0x01 /* PROTOCOL_ERROR */);
        return;
      }
      if (status == 100) {
        onContinue();
        return;
      } else if (status == 103) {
        MultiMap headersMultiMap = HeadersMultiMap.httpHeaders();
        removeStatusHeaders(headers);
        for (Map.Entry<CharSequence, CharSequence> header : headers) {
          headersMultiMap.add(header.getKey(), header.getValue());
        }
        onEarlyHints(headersMultiMap);
        return;
      }
      response = new HttpResponseHead(
        HttpVersion.HTTP_2,
        status,
        statusMessage,
        new Http2HeadersAdaptor(headers));
      removeStatusHeaders(headers);

      ClientMetrics clientMetrics = conn.clientMetrics();
      if (clientMetrics != null) {
        clientMetrics.responseBegin(metric, response);
      }

      handleHead(response);
    }
  }

  private void removeStatusHeaders(Http2Headers headers) {
    headers.remove(HttpHeaders.PSEUDO_STATUS);
  }

  @Override
  void onClose() {
    ClientMetrics clientMetrics = conn.clientMetrics();
    if (clientMetrics != null) {
      if (!requestEnded || !responseEnded) {
        clientMetrics.requestReset(metric);
      }
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null && trace != null) {
      VertxException err;
      if (responseEnded && requestEnded) {
        err = null;
      } else {
        err = HttpUtils.STREAM_CLOSED_EXCEPTION;
      }
      tracer.receiveResponse(context, response, trace, err, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
    }
    if (!responseEnded) {
      // NOT SURE OF THAT
      onException(HttpUtils.STREAM_CLOSED_EXCEPTION);
    }
    super.onClose();
    // commented to be used later when we properly define the HTTP/2 connection expiration from the pool
    // boolean disposable = conn.streams.isEmpty();
    if (!push) {
      conn.recycle();
    } /* else {
      conn.listener.onRecycle(0, disposable);
    } */
  }
}
