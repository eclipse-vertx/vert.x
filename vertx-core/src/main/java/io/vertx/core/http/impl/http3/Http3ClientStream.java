package io.vertx.core.http.impl.http3;

import io.netty.handler.codec.http3.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;

public class Http3ClientStream extends Http3Stream<Http3ClientStream, Http3ClientConnection> implements HttpClientStream {

  private Handler<Void> continueHandler;
  private Handler<HttpResponseHead> headHandler;
  private boolean endReceived;

  public Http3ClientStream(Http3ClientConnection connection, QuicStreamInternal stream, ContextInternal context) {
    super(connection, stream, context);
  }

  @Override
  protected int handleHead(Http3Headers headers) {
    HttpResponseHeaders responseHeaders = new HttpResponseHeaders(headers);
    if (responseHeaders.validate()) {
      int status = responseHeaders.status();
      if (status == 100) {
        Handler<Void> handler = continueHandler;
        if (handler != null) {
          context.emit(null, handler);
        }
//      } else if (status == 103) {
//        MultiMap headersMultiMap = Http1xHeaders.httpHeaders();
//        headers.remove(io.vertx.core.http.HttpHeaders.PSEUDO_STATUS);
//        for (Map.Entry<String, String> header : headers) {
//          headersMultiMap.add(header.getKey(), header.getValue());
//        }
//        onEarlyHints(headersMultiMap);
        return 1;
      } else {
        HttpResponseHead head = new HttpResponseHead(
          responseHeaders.status(),
          null,
          responseHeaders);
        Handler<HttpResponseHead> handler = headHandler;
        if (handler != null) {
          context.emit(head, handler);
        }
      }
      return 2;
    } else {
      return 0;
    }
  }

  @Override
  protected void handleEnd() {
    endReceived = true;
    super.handleEnd();
  }

  @Override
  protected void handleReset(int code) {
    if (!endReceived) {
      stream.reset(Http3ErrorCode.H3_REQUEST_CANCELLED.code());
    }
    super.handleReset(code);
  }

  @Override
  public Object trace() {
    return null;
  }

  @Override
  public HttpClientConnection connection() {
    return connection;
  }

  @Override
  public Future<Void> writeHead(HttpRequestHead request, boolean chunked, Buffer chunk, boolean end, StreamPriority priority, boolean connect) {
    HttpRequestHeaders headers = ((HttpRequestHeaders)request.headers());
    headers.authority(request.authority);
    headers.method(request.method);
    if (request.method != HttpMethod.CONNECT) {
      headers.path(request.uri);
      headers.scheme("https");
    }
    headers.prepare();
    return writeHeaders((Http3Headers) headers.unwrap(), chunk, end);
  }

  @Override
  public HttpClientStream headHandler(Handler<HttpResponseHead> handler) {
    this.headHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream continueHandler(Handler<Void> handler) {
    this.continueHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream earlyHintsHandler(Handler<MultiMap> handler) {
    return null;
  }

  @Override
  public HttpClientStream pushHandler(Handler<HttpClientPush> handler) {
    return null;
  }

  @Override
  public HttpClientStream priorityChangeHandler(Handler<StreamPriority> handler) {
    return null;
  }

  @Override
  public HttpClientStream updatePriority(StreamPriority streamPriority) {
    return null;
  }

  @Override
  public HttpVersion version() {
    return null;
  }

  @Override
  public Object metric() {
    return null;
  }

  @Override
  public Future<Void> writeFrame(int type, int flags, Buffer payload) {
    return null;
  }

  @Override
  public StreamPriority priority() {
    return null;
  }
}
