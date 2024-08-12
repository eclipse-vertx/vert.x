package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http3HeadersAdaptor;
import io.vertx.core.http.impl.headers.VertxDefaultHttp3Headers;
import io.vertx.core.http.impl.headers.VertxDefaultHttpHeaders;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.tracing.TracingPolicy;

class Http3ClientStream extends HttpStreamImpl<Http3ClientConnection, QuicStreamChannel, Http3Headers> {
  private static final MultiMap EMPTY = new Http3HeadersAdaptor(new DefaultHttp3Headers());

  private final HttpClientImpl client;
  private final ClientMetrics clientMetrics;

  Http3ClientStream(Http3ClientConnection conn, ContextInternal context, boolean push,
                    ClientMetrics<?, ?, ?, ?> metrics,
                    HttpClientImpl client, ClientMetrics clientMetrics) {
    super(conn, context, push, metrics);

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
  protected void metricsEnd(HttpStream<?, ?, ?> stream) {
    conn.metricsEnd(stream);
  }

  @Override
  protected void recycle() {
    conn.recycle();
  }

  @Override
  int lastStreamCreated() {
    return this.conn.quicStreamChannel != null ? (int) this.conn.quicStreamChannel.streamId() : 0;
  }

  @Override
  protected void createStreamInternal(int id, boolean b, Handler<AsyncResult<QuicStreamChannel>> onComplete) {
    VertxHttp3StreamHandler handler = new VertxHttp3StreamHandler(client, clientMetrics,
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

  protected void consumeCredits(int len) {
    conn.consumeCredits(this.stream, len);
  }

  @Override
  public void writeFrame(byte type, short flags, ByteBuf payload) {
    stream.write(payload);
  }
  @Override
  public CharSequence getHeaderMethod(Http3Headers headers) {
    return headers.method();
  }

  @Override
  public String getHeaderStatus(Http3Headers headers) {
    return headers.status().toString();
  }

  @Override
  public MultiMap createHeaderAdapter(Http3Headers headers) {
    return new Http3HeadersAdaptor(headers);
  }

  @Override
  public long getWindowSize() {
    return conn.getWindowSize();
  }
  @Override
  public void writeHeaders(Http3Headers headers, boolean end, int dependency, short weight, boolean exclusive,
                           boolean checkFlush, FutureListener<Void> promise) {
    stream
      .write(headers)
      .addListener(promise)
      .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
    ;  //TODO: review
  }

  @Override
  public void writePriorityFrame(StreamPriority priority) {
    throw new RuntimeException("Method not implemented");
  }

  @Override
  public void writeData_(ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    stream.write(chunk).addListener(promise);
  }

  @Override
  public void writeReset_(int streamId, long code) {
    stream.write(code);
  }

  @Override
  public void init_(VertxHttpStreamBase vertxHttpStream, QuicStreamChannel stream) {
    this.stream = stream;
    this.writable = stream.isWritable();
  }

  @Override
  public int getStreamId() {
    return stream != null ? (int) stream.streamId() : -1;
  }

  @Override
  public boolean remoteSideOpen() {
    return stream.isOutputShutdown();  //TODO: review
  }

  @Override
  public boolean hasStream() {
    return stream != null;
  }

  @Override
  public MultiMap getEmptyHeaders() {
    return EMPTY;
  }

  @Override
  public boolean isWritable_() {
    return writable;
  }

  @Override
  public boolean isTrailersReceived_() {
    return false;  //TODO review
  }
}
