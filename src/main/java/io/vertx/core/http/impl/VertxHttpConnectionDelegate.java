package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.Headers;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.MultiMap;
import io.vertx.core.http.StreamPriority;

interface VertxHttpConnectionDelegate<S, H extends Headers<CharSequence, CharSequence, H>> {
  void consumeCredits(int len);

  void writeFrame(byte type, short flags, ByteBuf payload);

  void writeHeaders(H headers, boolean end, int dependency, short weight, boolean exclusive, boolean checkFlush,
                    FutureListener<Void> promise);

  void writePriorityFrame(StreamPriority priority);

  void writeData(ByteBuf chunk, boolean end, FutureListener<Void> promise);

  void writeReset(int streamId, long code);

  void init(S stream);

  int getStreamId();

  boolean remoteSideOpen();

  int id();

  boolean hasStream();

  MultiMap getEmptyHeaders();

  boolean isWritable();

  boolean isTrailersReceived();
}
