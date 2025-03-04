package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.EmptyHttp2Headers;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
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
  protected Future<Http2Stream> createStreamChannelInternal(int id, boolean b) throws HttpException {
    try {
      return Future.succeededFuture(this.conn.handler.encoder().connection().local().createStream(id, false));
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
    return new Http2HeadersAdaptor();
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
    conn.handler.writePriority(streamChannel, priority.getDependency(), priority.getWeight(), priority.isExclusive());
  }

  @Override
  public void writeData_(Http2Stream stream, ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    conn.handler.writeData(stream, chunk, end, promise);
  }

  @Override
  public void writeReset_(int streamId, long code, FutureListener<Void> listener) {
    conn.handler.writeReset(streamId, code, listener);
  }

  @Override
  public void init_(VertxHttpStreamBase vertxHttpStream, Http2Stream http2Stream) {
    this.streamChannel = http2Stream;
    this.writable = this.conn.handler.encoder().flowController().isWritable(this.streamChannel);
    http2Stream.setProperty(conn.streamKey, vertxHttpStream);
  }

  @Override
  public synchronized int getStreamId() {
    return streamChannel != null ? streamChannel.id() : -1;
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
    return streamChannel.isTrailersReceived();
  }

  @Override
  public StreamPriorityBase createDefaultStreamPriority() {
    return HttpUtils.DEFAULT_STREAM_PRIORITY;
  }
}
