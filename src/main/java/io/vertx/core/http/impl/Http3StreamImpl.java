package io.vertx.core.http.impl;

import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.VertxDefaultHttp3Headers;
import io.vertx.core.http.impl.headers.VertxDefaultHttpHeaders;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.tracing.TracingPolicy;

class Http3StreamImpl extends HttpStreamImpl<Http3ClientConnection, QuicStreamChannel, Http3Headers> {
  private final HttpClientImpl client;
  private final ClientMetrics clientMetrics;

  Http3StreamImpl(Http3ClientConnection conn, ContextInternal context, boolean push,
                  VertxHttpConnectionDelegate<QuicStreamChannel, Http3Headers> connectionDelegate,
                  ClientMetrics<?, ?, ?, ?> metrics,
                  HttpClientImpl client, ClientMetrics clientMetrics) {
    super(conn, context, push, connectionDelegate, metrics);

    this.client = client;
    this.clientMetrics = clientMetrics;
  }

  @Override
  public HttpClientConnection connection() {
    return conn;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_3;
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
    return this.conn.quicStreamChannel != null ? (int) this.conn.quicStreamChannel.streamId() : 0;
  }

  @Override
  protected void createStreamInternal(int id, boolean b, Handler<AsyncResult<QuicStreamChannel>> onComplete) {
    VertxHttp3ControlStreamHandler handler = new VertxHttp3ControlStreamHandler(client, clientMetrics,
      metric, this);

    Http3.newRequestStream(conn.quicChannel, handler)
      .addListener((GenericFutureListener<io.netty.util.concurrent.Future<QuicStreamChannel>>) quicStreamChannelFuture -> {
        QuicStreamChannel quicStreamChannel = quicStreamChannelFuture.get();
        onComplete.handle(Future.succeededFuture(quicStreamChannel));
      });
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
  VertxDefaultHttpHeaders<Http3Headers> createHttpHeadersWrapper() {
    return new VertxDefaultHttp3Headers();
  }

}
