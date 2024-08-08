package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.MultiMap;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http3HeadersAdaptor;

class VertxHttp3ConnectionDelegate implements VertxHttpConnectionDelegate<QuicStreamChannel, Http3Headers> {
  private static final MultiMap EMPTY = new Http3HeadersAdaptor(new DefaultHttp3Headers());
  public QuicStreamChannel quicStreamChannel;
  private final Http3ConnectionBase conn;
  private boolean writable;

  VertxHttp3ConnectionDelegate(Http3ConnectionBase conn) {
    this.conn = conn;
  }

  @Override
  public void consumeCredits(int len) {
    conn.consumeCredits(this.quicStreamChannel, len);
  }

  @Override
  public void writeFrame(byte type, short flags, ByteBuf payload) {
    quicStreamChannel.write(payload);
  }

  @Override
  public void writeHeaders(Http3Headers headers, boolean end, int dependency, short weight, boolean exclusive,
                           boolean checkFlush, FutureListener<Void> promise) {
    quicStreamChannel
      .write(headers)
      .addListener(promise)
      .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);  //TODO: review
  }

  @Override
  public void writePriorityFrame(StreamPriority priority) {
    throw new RuntimeException("Method not implemented");
  }

  @Override
  public void writeData(ByteBuf chunk, boolean end, FutureListener<Void> promise) {
    quicStreamChannel.write(chunk).addListener(promise);
  }

  @Override
  public void writeReset(int streamId, long code) {
    quicStreamChannel.write(code);
  }

  @Override
  public void init(QuicStreamChannel quicStreamChannel) {
    this.quicStreamChannel = quicStreamChannel;
    this.writable = quicStreamChannel.isWritable();
//    quicStreamChannel.attr(this); //TODO: review
  }

  @Override
  public int getStreamId() {
    return quicStreamChannel != null ? (int) quicStreamChannel.streamId() : -1;
  }

  @Override
  public boolean remoteSideOpen() {
    return quicStreamChannel.isOutputShutdown();  //TODO: review
  }

  @Override
  public int id() {
    return (int) quicStreamChannel.streamId(); //TODO: review
  }

  @Override
  public boolean hasStream() {
    return quicStreamChannel != null;
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
    return false;  //TODO review
  }

  @Override
  public long getWindowSize() {
    return conn.getWindowSize();
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
}
