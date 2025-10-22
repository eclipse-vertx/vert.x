package io.vertx.core.http.impl.http3;

import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3Headers;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;

public class Http3ClientStream extends Http3Stream<Http3ClientStream, Http3ClientConnection> implements HttpClientStream {

  private Handler<HttpResponseHead> headHandler;

  public Http3ClientStream(Http3ClientConnection connection, QuicStreamInternal stream) {
    super(connection, stream);
  }

  @Override
  protected boolean handleHead(Http3Headers headers) {
    HttpResponseHeaders responseHeaders = new HttpResponseHeaders(headers);
    if (responseHeaders.validate()) {
      HttpResponseHead head = new HttpResponseHead(
        responseHeaders.status(),
        null,
        responseHeaders);
      Handler<HttpResponseHead> handler = headHandler;
      if (handler != null) {
        handler.handle(head);
      }
      return true;
    } else {
      // Not yet implemented
      return false;
    }
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
  public Future<Void> writeHead(HttpRequestHead request, boolean chunked, Buffer buf, boolean end, StreamPriority priority, boolean connect) {

    HttpRequestHeaders headers = ((HttpRequestHeaders)request.headers());

    headers.authority(request.authority);
    headers.method(request.method);
    headers.path(request.uri);
    headers.scheme("https");

    headers.prepare();

    Http3HeadersFrame frame = new DefaultHttp3HeadersFrame((Http3Headers) headers.unwrap());

    Future<Void> res = stream.writeMessage(frame);

    if (end) {
      res = stream.end();
    }

    return res;
  }

  @Override
  public HttpClientStream headHandler(Handler<HttpResponseHead> handler) {
    this.headHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream resetHandler(Handler<Long> handler) {
    return null;
  }

  @Override
  public HttpClientStream exceptionHandler(Handler<Throwable> handler) {
    return null;
  }

  @Override
  public HttpClientStream continueHandler(Handler<Void> handler) {
    return null;
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
  public HttpClientStream closeHandler(Handler<Void> handler) {
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
  public Future<Void> writeChunk(Buffer buf, boolean end) {
    return null;
  }

  @Override
  public Future<Void> writeFrame(int type, int flags, Buffer payload) {
    return null;
  }

  @Override
  public Future<Void> writeReset(long code) {
    return null;
  }

  @Override
  public StreamPriority priority() {
    return null;
  }
}
