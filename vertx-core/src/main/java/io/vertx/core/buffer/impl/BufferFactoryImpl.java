package io.vertx.core.buffer.impl;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.spi.BufferFactory;
import io.vertx.internal.core.buffer.BufferInternal;

import java.util.Objects;

public class BufferFactoryImpl implements BufferFactory {

  @Override
  public Buffer buffer(ByteBuf byteBuf) {
    return new BufferImpl(Objects.requireNonNull(byteBuf));
  }

  @Override
  public Buffer buffer(int initialSizeHint) {
    return new BufferImpl(initialSizeHint);
  }

  @Override
  public Buffer buffer() {
    return new BufferImpl();
  }

  @Override
  public Buffer buffer(String str) {
    return new BufferImpl(str);
  }

  @Override
  public Buffer buffer(String str, String enc) {
    return new BufferImpl(str, enc);
  }

  @Override
  public BufferInternal buffer(byte[] bytes) {
    return new BufferImpl(bytes);
  }
}
