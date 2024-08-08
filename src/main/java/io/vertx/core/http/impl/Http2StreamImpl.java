package io.vertx.core.http.impl;

import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.VertxDefaultHttp2Headers;
import io.vertx.core.http.impl.headers.VertxDefaultHttpHeaders;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.tracing.TracingPolicy;

class Http2StreamImpl extends HttpStreamImpl<Http2ClientConnection, Http2Stream, Http2Headers> {
  Http2StreamImpl(Http2ClientConnection conn, ContextInternal context, boolean push,
                  VertxHttpConnectionDelegate<Http2Stream, Http2Headers> connectionDelegate, ClientMetrics metrics) {
    super(conn, context, push, connectionDelegate, metrics);
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

  @Override
  int lastStreamCreated() {
    return this.conn.handler.encoder().connection().local().lastStreamCreated();
  }

  @Override
  protected Http2Stream createStream2(int id, boolean b) throws HttpException {
    try {
      return this.conn.handler.encoder().connection().local().createStream(id, false);
    } catch (Http2Exception e) {
      throw new HttpException(e);
    }
  }

  @Override
  protected TracingPolicy getTracingPolicy() {
    return conn.client.options().getTracingPolicy();
  }

  @Override
  protected boolean isTryUseCompression() {
    return this.conn.client.options().isTryUseCompression();
  }

  @Override
  VertxDefaultHttpHeaders<Http2Headers> createHttpHeadersWrapper() {
    return new VertxDefaultHttp2Headers();
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
