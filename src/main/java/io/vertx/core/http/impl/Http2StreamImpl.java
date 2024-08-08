package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.Map;
import java.util.function.BiConsumer;

class Http2StreamImpl extends HttpStreamImpl<Http2ClientConnection, Http2Stream, Http2Headers> {
  Http2StreamImpl(Http2ClientConnection conn, ContextInternal context, boolean push,
                  VertxHttpConnectionDelegate<Http2Stream, Http2Headers> connectionDelegate, ClientMetrics metrics) {
    super(conn, context, push, connectionDelegate, metrics);
  }

  protected void writeHeaders(HttpRequestHead request, ByteBuf buf, boolean end, StreamPriority priority,
                              boolean connect,
                              Handler<AsyncResult<Void>> handler) {
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
    if (this.conn.client.options().isTryUseCompression() && headers.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
      headers.set(HttpHeaderNames.ACCEPT_ENCODING, Http1xClientConnection.determineCompressionAcceptEncoding());
    }
    try {
      createStream(request, headers, handler);
    } catch (Http2Exception ex) {
      if (handler != null) {
        handler.handle(context.failedFuture(ex));
      }
      handleException(ex);
      return;
    }
    if (buf != null) {
      doWriteHeaders(headers, false, false, null);
      doWriteData(buf, e, handler);
    } else {
      doWriteHeaders(headers, e, true, handler);
    }
  }

  private void createStream(HttpRequestHead head, Http2Headers headers, Handler<AsyncResult<Void>> handler) throws Http2Exception {
    int id = this.conn.handler.encoder().connection().local().lastStreamCreated();
    if (id == 0) {
      id = 1;
    } else {
      id += 2;
    }
    head.id = id;
    head.remoteAddress = conn.remoteAddress();
    Http2Stream stream = this.conn.handler.encoder().connection().local().createStream(id, false);
    init(stream);
    if (metrics != null) {
      metric = metrics.requestBegin(headers.path().toString(), head);
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      BiConsumer<String, String> headers_ = (key, val) -> new Http2HeadersAdaptor(headers).add(key, val);
      String operation = head.traceOperation;
      if (operation == null) {
        operation = headers.method().toString();
      }
      trace = tracer.sendRequest(context, SpanKind.RPC, conn.client.options().getTracingPolicy(), head, operation,
        headers_,
        HttpUtils.CLIENT_HTTP_REQUEST_TAG_EXTRACTOR);
    }
  }

  @Override
  public HttpClientConnection connection() {
    return conn;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_2;
  }

  @Override
  void metricsEnd(HttpStream<?, ?, ?> stream) {
    conn.metricsEnd(stream);
  }

  @Override
  void recycle() {
    conn.recycle();
  }















  //TODO: implement the following methods correctly!

  @Override
  public Future<Void> write(Buffer data) {
    return super.write(data);
  }

  @Override
  public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
    super.write(data, handler);
  }

  @Override
  public Future<Void> end(Buffer data) {
    return super.end(data);
  }

  @Override
  public void end(Buffer data, Handler<AsyncResult<Void>> handler) {
    super.end(data, handler);
  }

  @Override
  public Future<Void> end() {
    return super.end();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    super.end(handler);
  }

}
