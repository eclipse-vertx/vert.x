package io.vertx.core.internal.quic;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.net.SocketInternal;
import io.vertx.core.quic.QuicStream;

public interface QuicStreamInternal extends QuicStream, SocketInternal {

  @Override
  QuicStreamInternal messageHandler(Handler<Object> handler);

  @Override
  QuicStreamInternal readCompletionHandler(Handler<Void> handler);

  @Override
  QuicStreamInternal eventHandler(Handler<Object> handler);

  @Override
  QuicStreamInternal exceptionHandler(@Nullable Handler<Throwable> handler);

  @Override
  QuicStreamInternal handler(@Nullable Handler<Buffer> handler);

  @Override
  QuicStreamInternal pause();

  @Override
  QuicStreamInternal resume();

  @Override
  QuicStreamInternal fetch(long amount);

  @Override
  QuicStreamInternal endHandler(@Nullable Handler<Void> endHandler);

  @Override
  QuicStreamInternal setWriteQueueMaxSize(int maxSize);

  @Override
  QuicStreamInternal drainHandler(@Nullable Handler<Void> handler);

  @Override
  QuicStreamInternal closeHandler(@Nullable Handler<Void> handler);

}
