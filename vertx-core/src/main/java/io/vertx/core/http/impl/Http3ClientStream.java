package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.FutureListener;
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
    return this.channelStream != null ? (int) this.channelStream.streamId() : 0;
  }

  @Override
  protected void createChannelStreamInternal(int id, boolean b, Handler<QuicStreamChannel> onComplete) {
    conn.handler.createStreamChannel(onComplete);
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
  public void writeHeaders(QuicStreamChannel stream, VertxHttpHeaders headers, boolean end, StreamPriorityBase priority,
                           boolean checkFlush, FutureListener<Void> promise) {
    conn.handler.writeHeaders(stream, headers, end, priority, checkFlush, promise);
  }

  @Override
  public void writePriorityFrame(StreamPriorityBase priority) {
    conn.handler.writePriority(channelStream, priority.urgency(), priority.isIncremental());
  }

  @Override
  public void writeData_(QuicStreamChannel stream, ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    conn.handler.writeData(stream, chunk, end, promise);
  }

  @Override
  public void writeReset_(int streamId, long code) {
    conn.handler.writeReset(conn.quicStreamChannels.get(streamId), code);
  }

  @Override
  public void init_(VertxHttpStreamBase vertxHttpStream, QuicStreamChannel quicStreamChannel) {
    this.channelStream = quicStreamChannel;
    this.writable = quicStreamChannel.isWritable();
    this.conn.quicStreamChannels.put(quicStreamChannel.streamId(), quicStreamChannel);
    VertxHttp3ConnectionHandler.setVertxStreamOnStreamChannel(quicStreamChannel, this);
  }

  @Override
  public synchronized int getStreamId() {
    return channelStream != null ? (int) channelStream.streamId() : -1;
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
    return false;  //TODO: review
  }

  @Override
  public StreamPriorityBase createDefaultStreamPriority() {
    return HttpUtils.DEFAULT_QUIC_STREAM_PRIORITY;
  }
}
