package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.EmptyHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.MultiMap;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;

class VertxHttp2ConnectionDelegate implements VertxHttpConnectionDelegate<Http2Stream, Http2Headers> {

  private static final MultiMap EMPTY = new Http2HeadersAdaptor(EmptyHttp2Headers.INSTANCE);
  private Http2Stream stream;

  private final Http2ConnectionBase conn;
  private boolean writable;

  VertxHttp2ConnectionDelegate(Http2ConnectionBase conn) {
    this.conn = conn;
  }

  @Override
  public void consumeCredits(int len) {
    conn.consumeCredits(this.stream, len);
  }

  @Override
  public void writeFrame(byte type, short flags, ByteBuf payload) {
    conn.handler.writeFrame(stream, type, flags, payload);
  }

  @Override
  public void writeHeaders(Http2Headers headers, boolean end, int dependency, short weight, boolean exclusive,
                           boolean checkFlush, FutureListener<Void> promise) {
    conn.handler.writeHeaders(stream, headers, end, dependency, weight, exclusive, checkFlush, promise);
  }

  @Override
  public void writePriorityFrame(StreamPriority priority) {
    conn.handler.writePriority(stream, priority.getDependency(), priority.getWeight(), priority.isExclusive());
  }

  @Override
  public void writeData(ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    conn.handler.writeData(stream, chunk, end, promise);
  }

  @Override
  public void writeReset(int streamId, long code) {
    conn.handler.writeReset(streamId, code);
  }

  @Override
  public void init(VertxHttpStreamBase vertxHttpStream, Http2Stream stream) {
    this.stream = stream;
    this.writable = this.conn.handler.encoder().flowController().isWritable(this.stream);
    stream.setProperty(conn.streamKey, vertxHttpStream);
  }

  @Override
  public synchronized int getStreamId() {
    return stream != null ? stream.id() : -1;
  }

  @Override
  public boolean remoteSideOpen() {
    return stream.state().remoteSideOpen();
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
  public boolean isWritable() {
    return writable;
  }

  @Override
  public boolean isTrailersReceived() {
    return stream.isTrailersReceived();
  }

  @Override
  public long getWindowSize() {
    return conn.getWindowSize();
  }

  @Override
  public CharSequence getHeaderMethod(Http2Headers headers) {
    return headers.method();
  }

  @Override
  public String getHeaderStatus(Http2Headers headers) {
    return headers.status().toString();
  }

  @Override
  public MultiMap createHeaderAdapter(Http2Headers headers) {
    return new Http2HeadersAdaptor(headers);
  }
}
