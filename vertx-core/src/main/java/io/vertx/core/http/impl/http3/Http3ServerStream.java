package io.vertx.core.http.impl.http3;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http3.*;
import io.netty.handler.stream.ChunkedInput;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
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
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.HostAndPort;

public class Http3ServerStream extends Http3Stream<Http3ServerStream, Http3ServerConnection> implements HttpServerStream {

  private Handler<HttpRequestHead> headHandler;

  public Http3ServerStream(Http3ServerConnection connection, QuicStreamInternal stream) {
    super(connection, stream);
  }

  @Override
  protected boolean handleHead(Http3Headers headers) {
    HttpRequestHeaders requestHeaders = new HttpRequestHeaders(headers);
    if (requestHeaders.validate()) {
      HttpRequestHead head = new HttpRequestHead(
        requestHeaders.method(),
        requestHeaders.path(),
        requestHeaders,
        requestHeaders.authority(),
        null,
        null);
      Handler<HttpRequestHead> handler = headHandler;
      if (handler != null) {
        handler.handle(head);
      }
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void routed(String route) {
  }

  @Override
  public long bytesWritten() {
    return 0;
  }

  @Override
  public long bytesRead() {
    return 0;
  }

  @Override
  public HttpServerConnection connection() {
    return connection;
  }

  @Override
  public Future<Void> writeHead(HttpResponseHead head, Buffer chunk, boolean end) {
    Http3Headers headers = (Http3Headers) ((HttpHeaders) head.headers).unwrap();
    headers.status("" + head.statusCode);
    Future<Void> fut = stream.writeMessage(new DefaultHttp3HeadersFrame(headers));
    if (chunk != null) {
      fut = stream.writeMessage(new DefaultHttp3DataFrame(((BufferInternal)chunk).getByteBuf()));
    }
    if (end) {
      fut = stream.end();
    }
    return fut;
  }

  @Override
  public Future<Void> writeHeaders(MultiMap headers, boolean end) {
    Http3Headers http3Headers = (Http3Headers) ((HttpHeaders) headers).unwrap();
    Future<Void> fut = stream.writeMessage(new DefaultHttp3HeadersFrame(http3Headers));
    if (end) {
      fut = stream.end();
    }
    return fut;
  }

  @Override
  public Future<Void> writeChunk(Buffer chunk, boolean end) {
    Future<Void> fut;
    if (chunk != null) {
      fut = stream.writeMessage(new DefaultHttp3DataFrame(((BufferInternal)chunk).getByteBuf()));
    } else {
      fut = null;
    }
    if (end) {
      fut = stream.end();
    }
    return fut;
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
  public HttpServerStream resetHandler(Handler<Long> handler) {
    return null;
  }

  @Override
  public HttpServerStream exceptionHandler(Handler<Throwable> handler) {
    return null;
  }

  @Override
  public HttpServerStream priorityChangeHandler(Handler<StreamPriority> handler) {
    return this;
  }

  @Override
  public HttpServerStream closeHandler(Handler<Void> handler) {
    stream.closeHandler(handler);
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
  public HttpVersion version() {
    throw new UnsupportedOperationException();
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
  public Future<Void> writeReset(long code) {
    return null;
  }

  @Override
  public StreamPriority priority() {
    return null;
  }
}
