package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.EmptyHttp2Headers;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.http.impl.headers.VertxHttp2Headers;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.tracing.TracingPolicy;

class Http2ClientStream extends HttpStreamImpl<Http2ClientConnection, Http2Stream> {
  private static final MultiMap EMPTY = new Http2HeadersAdaptor(EmptyHttp2Headers.INSTANCE);

  Http2ClientStream(Http2ClientConnection conn, ContextInternal context, boolean push) {
    super(conn, context, push);
  }

  @Override
  public HttpClientConnectionInternal connection() {
    return conn;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_2;
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
    return this.conn.handler.encoder().connection().local().lastStreamCreated();
  }

  @Override
  protected void createStreamInternal(int id, boolean b, Handler<AsyncResult<Http2Stream>> onComplete) throws HttpException {
    try {
      Http2Stream stream = this.conn.handler.encoder().connection().local().createStream(id, false);
      onComplete.handle(Future.succeededFuture(stream));
    } catch (Http2Exception e) {
      throw new HttpException(e);
    }
  }

  @Override
  protected TracingPolicy getTracingPolicy() {
    return conn.client().options().getTracingPolicy();
  }

  @Override
  protected boolean isTryUseCompression() {
    return this.conn.client().options().isDecompressionSupported();
  }

  @Override
  VertxHttpHeaders createHttpHeadersWrapper() {
    return new VertxHttp2Headers();
  }

  @Override
  protected void consumeCredits(Http2Stream stream, int len) {
    conn.consumeCredits(stream, len);
  }

  @Override
  public void writeFrame(Http2Stream stream, byte type, short flags, ByteBuf payload, Promise<Void> promise) {
    conn.handler.writeFrame(stream, type, flags, payload, (FutureListener<Void>) promise);
  }

  @Override
  public void writeHeaders(Http2Stream stream, VertxHttpHeaders headers, boolean end, StreamPriorityBase priority,
                           boolean checkFlush, FutureListener<Void> promise) {
    conn.handler.writeHeaders(stream, headers, end, priority.getDependency(), priority.getWeight(), priority.isExclusive(),
      checkFlush, promise);
  }

  @Override
  public void writePriorityFrame(StreamPriorityBase priority) {
    conn.handler.writePriority(stream, priority.getDependency(), priority.getWeight(), priority.isExclusive());
  }

  @Override
  public void writeData_(Http2Stream stream, ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    conn.handler.writeData(stream, chunk, end, promise);
  }

  @Override
  public void writeReset_(int streamId, long code) {
    conn.handler.writeReset(streamId, code);
  }

  @Override
  public void init_(VertxHttpStreamBase vertxHttpStream, Http2Stream stream) {
    this.stream = stream;
    this.writable = this.conn.handler.encoder().flowController().isWritable(this.stream);
    stream.setProperty(conn.streamKey, vertxHttpStream);
  }

  @Override
  public synchronized int getStreamId() {
    return stream != null ? stream.id() : -1;
  }

  @Override
  public boolean remoteSideOpen(Http2Stream stream) {
    return stream.state().remoteSideOpen();
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
    return stream.isTrailersReceived();
  }

  @Override
  public StreamPriorityBase createDefaultStreamPriority() {
    return HttpUtils.DEFAULT_STREAM_PRIORITY;
  }
}
