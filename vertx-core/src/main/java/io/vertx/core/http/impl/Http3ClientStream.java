package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.Http3HeadersAdaptor;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.tracing.TracingPolicy;

class Http3ClientStream extends HttpStreamImpl<Http3ClientConnection, QuicStreamChannel> {
  private static final MultiMap EMPTY = new Http3HeadersAdaptor();
  private int headerReceivedCount = 0;
  private boolean trailersReceived = false;

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
    return this.streamChannel != null ? (int) this.streamChannel.streamId() : 0;
  }

  @Override
  protected Future<QuicStreamChannel> createStreamChannelInternal(int id, boolean b) {
    return conn.handler.createStreamChannel();
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
    return new Http3HeadersAdaptor();
  }

  @Override
  protected void consumeCredits(QuicStreamChannel stream, int len) {
    conn.consumeCredits(stream, len);
  }

  @Override
  public void writeFrame(QuicStreamChannel stream, byte type, short flags, ByteBuf payload, Promise<Void> promise) {
    conn.handler.writeFrame(stream, type, flags, payload, (FutureListener<Void>) promise);
  }

  @Override
  public void writeHeaders(QuicStreamChannel stream, VertxHttpHeaders headers, boolean end, StreamPriorityBase priority,
                           boolean checkFlush, FutureListener<Void> promise) {
    conn.handler.writeHeaders(stream, headers, end, priority, checkFlush, promise);
  }

  @Override
  public void writePriorityFrame(StreamPriorityBase priority) {
    conn.handler.writePriority(streamChannel, priority);
  }

  @Override
  public void writeData_(QuicStreamChannel stream, ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    conn.handler.writeData(stream, chunk, end, promise);
  }

  @Override
  public void writeReset_(int streamId, long code, FutureListener<Void> listener) {
    if (streamChannel.isOutputShutdown()) {
      createStreamChannelInternal(-1, false).onSuccess(quicStreamChannel -> {
        init(quicStreamChannel);
        conn.handler.writeReset(streamChannel, code, listener);
      });
    } else {
      conn.handler.writeReset(streamChannel, code, listener);
    }
  }

  @Override
  public void init_(VertxHttpStreamBase vertxHttpStream, QuicStreamChannel quicStreamChannel) {
    this.conn.quicStreamChannels.put(quicStreamChannel.streamId(), quicStreamChannel);
    VertxHttp3ConnectionHandler.setVertxStreamOnStreamChannel(quicStreamChannel, this);
    VertxHttp3ConnectionHandler.setLastStreamIdOnConnection(quicStreamChannel.parent(), quicStreamChannel.streamId());
  }

  @Override
  public synchronized int getStreamId() {
    return streamChannel != null ? (int) streamChannel.streamId() : -1;
  }

  @Override
  public boolean remoteSideOpen(QuicStreamChannel stream) {
    return !stream.closeFuture().isDone();
  }

  @Override
  public MultiMap getEmptyHeaders() {
    return EMPTY;
  }

  @Override
  protected boolean evaluateChannelWritability(QuicStreamChannel streamChannel) {
    return streamChannel.isWritable();
  }

  @Override
  void onHeaders(VertxHttpHeaders headers, StreamPriorityBase streamPriority) {
    super.onHeaders(headers, streamPriority);
  }

  @Override
  public StreamPriorityBase createDefaultStreamPriority() {
    return HttpUtils.DEFAULT_QUIC_STREAM_PRIORITY;
  }

  public boolean isTrailersReceived() {
    return trailersReceived;
  }

  public void determineIfTrailersReceived(VertxHttpHeaders headers) {
    trailersReceived = headerReceivedCount > 0 && headers.method() == null && headers.status() == null;
    headerReceivedCount++;
  }

  @Override
  public ContextInternal context() {
    return null;
  }

  @Override
  public Future<Void> write(ByteBuf buf, boolean end) {
    return null;
  }

  @Override
  public HttpClientStream endHandler(Handler<Void> handler) {
    return null;
  }

  @Override
  public HttpClientStream handler(Handler<Buffer> handler) {
    return null;
  }

  @Override
  public HttpClientStream trailersHandler(Handler<MultiMap> handler) {
    return null;
  }

  @Override
  public HttpClientStream resume() {
    return null;
  }

  @Override
  public HttpClientStream pause() {
    return null;
  }

  @Override
  public HttpClientStream fetch(long amount) {
    return null;
  }
}
