package io.vertx.core.http.impl.http3;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http3.*;
import io.netty.handler.stream.ChunkedInput;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpServerStream;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.HostAndPort;

public class Http3ServerStream extends Http3Stream<Http3ServerStream, Http3ServerConnection> implements HttpServerStream {

  private Handler<HttpRequestHead> headHandler;
  private boolean endReceived;

  public Http3ServerStream(Http3ServerConnection connection, QuicStreamInternal stream, ContextInternal context) {
    super(connection, stream, context);
  }

  @Override
  protected int handleHead(Http3Headers headers) {
    HttpRequestHeaders requestHeaders = new HttpRequestHeaders(headers);
    if (requestHeaders.validate()) {
      HttpRequestHead head = new HttpRequestHead(
        requestHeaders.method(),
        requestHeaders.path(),
        requestHeaders,
        requestHeaders.authority(),
        null,
        null);
      head.scheme = "https";
      Handler<HttpRequestHead> handler = headHandler;
      if (handler != null) {
        context.emit(head, handler);
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
    super.handleReset(code);
    if (!endReceived) {
      stream.reset(Http3ErrorCode.H3_REQUEST_INCOMPLETE.code());
    }
  }

  @Override
  public void routed(String route) {
  }

  @Override
  public HttpServerConnection connection() {
    return connection;
  }

  @Override
  public Future<Void> writeHead(HttpResponseHead head, Buffer chunk, boolean end) {
    Http3Headers headers = (Http3Headers) ((HttpHeaders) head.headers).unwrap();
    headers.status("" + head.statusCode);
    return writeHeaders(headers, chunk, end);
  }

  @Override
  public Future<Void> writeHeaders(MultiMap headers, boolean end) {
    Http3Headers http3Headers = (Http3Headers) ((HttpHeaders) headers).unwrap();
    return writeHeaders(http3Headers, null, end);
  }

  @Override
  public Future<HttpServerStream> sendPush(HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriority priority) {
    return null;
  }

  @Override
  public HttpServerStream headHandler(Handler<HttpRequestHead> handler) {
    this.headHandler = handler;
    return this;
  }

  @Override
  public HttpServerStream priorityChangeHandler(Handler<StreamPriority> handler) {
    return this;
  }

  @Override
  public void sendFile(ChunkedInput<ByteBuf> file, Promise<Void> promise) {

  }

  @Override
  public HttpServerStream updatePriority(StreamPriority streamPriority) {
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
