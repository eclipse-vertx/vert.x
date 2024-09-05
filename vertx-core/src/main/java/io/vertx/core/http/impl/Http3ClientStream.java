package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.incubator.codec.http3.Http3RequestStreamInitializer;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.Http3HeadersAdaptor;
import io.vertx.core.http.impl.headers.VertxHttp3Headers;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.tracing.TracingPolicy;

class Http3ClientStream extends HttpStreamImpl<Http3ClientConnection, QuicStreamChannel> {
  private static final MultiMap EMPTY = new Http3HeadersAdaptor(new DefaultHttp3Headers());

  Http3ClientStream(Http3ClientConnection conn, ContextInternal context, boolean push) {
    super(conn, context, push);
  }

  @Override
  public HttpClientConnectionInternal connection() {
    return conn;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_3;
  }

  @Override
  protected void metricsEnd(HttpStream<?, ?> stream) {
    conn.metricsEnd(stream);
  }

  @Override
  protected void recycle() {
    conn.recycle();
  }

  @Override
  int lastStreamCreated() {
    return this.stream != null ? (int) this.stream.streamId() : 0;
  }

  @Override
  protected void createStreamInternal(int id, boolean b, Handler<AsyncResult<QuicStreamChannel>> onComplete) {
    Http3.newRequestStream((QuicChannel) conn.channelHandlerContext().channel().parent(),
        new Http3RequestStreamInitializer() {
          @Override
          protected void initRequestStream(QuicStreamChannel ch) {
            ch.pipeline()
              .addLast(new Http3FrameToHttpObjectCodec(false))
              .addLast(conn.handler);
            onComplete.handle(Future.succeededFuture(ch));
          }
        });
  }

  @Override
  protected TracingPolicy getTracingPolicy() {
    return conn.client.options().getTracingPolicy();
  }

  @Override
  protected boolean isTryUseCompression() {
    return this.conn.client.options().isDecompressionSupported();
  }

  @Override
  VertxHttpHeaders createHttpHeadersWrapper() {
    return new VertxHttp3Headers();
  }

  @Override
  protected void consumeCredits(QuicStreamChannel stream, int len) {
    conn.consumeCredits(stream, len);
  }

  @Override
  public void writeFrame(QuicStreamChannel stream, byte type, short flags, ByteBuf payload, Promise<Void> promise) {
    stream.write(payload);
  }

  @Override
  public long getWindowSize() {
    return conn.getWindowSize();
  }

  @Override
  public void writeHeaders(QuicStreamChannel stream, VertxHttpHeaders headers, boolean end, StreamPriorityBase priority,
                           boolean checkFlush, FutureListener<Void> promise) {
    conn.handler.writeHeaders(stream, headers, end, priority, checkFlush, promise);
  }

  @Override
  public void writePriorityFrame(StreamPriorityBase priority) {
    conn.handler.writePriority(stream, priority.urgency(), priority.isIncremental());
  }

  @Override
  public void writeData_(QuicStreamChannel stream, ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    conn.handler.writeData(stream, chunk, end, promise);
  }

  @Override
  public void writeReset_(int streamId, long code) {
    stream.write(code);
  }

  @Override
  public void init_(VertxHttpStreamBase vertxHttpStream, QuicStreamChannel stream) {
    this.stream = stream;
    this.writable = stream.isWritable();
    VertxHttp3ConnectionHandler.setHttp3ClientStream(stream, this);
  }

  @Override
  public synchronized int getStreamId() {
    return stream != null ? (int) stream.streamId() : -1;
  }

  @Override
  public boolean remoteSideOpen(QuicStreamChannel stream) {
    return stream.isOpen();
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
  public boolean isTrailersReceived() {
    return false;  //TODO review
  }

  @Override
  public StreamPriorityBase createDefaultStreamPriority() {
    return HttpUtils.DEFAULT_QUIC_STREAM_PRIORITY;
  }
}
