package io.vertx5.core.buffer.impl;

import io.netty5.buffer.api.BufferAllocator;
import io.vertx5.core.buffer.Buffer;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;

public enum BufferOwnershipStrategy {

  SHARED() {
    @Override
    public io.netty5.buffer.api.Buffer wrap(io.netty5.buffer.api.Buffer buffer) {
      int len = buffer.readableBytes();
      ByteBuffer byteBuffer = ByteBuffer.allocate(len);
      buffer.copyInto(buffer.readerOffset(), byteBuffer, 0, len);
      buffer = new SharedNettyBuffer(byteBuffer);
      buffer.writerOffset(len);
      return buffer;
    }
    @Override
    public io.netty5.buffer.api.Buffer unwrap(io.netty5.buffer.api.Buffer buffer) {
      return new SharedNettyBuffer(((SharedNettyBuffer)buffer));
    }
  },

  COPY_ON_TRANSFER() {
    @Override
    public io.netty5.buffer.api.Buffer wrap(io.netty5.buffer.api.Buffer buffer) {
      return buffer;
    }
    @Override
    public io.netty5.buffer.api.Buffer unwrap(io.netty5.buffer.api.Buffer buffer) {
      return buffer.copy();
    }
  },

  COPY_ON_WRITE() {
    @Override
    public io.netty5.buffer.api.Buffer wrap(io.netty5.buffer.api.Buffer buffer) {
      return new CopyOnWriteNettyBuffer(buffer);
    }
    @Override
    public io.netty5.buffer.api.Buffer unwrap(io.netty5.buffer.api.Buffer buffer) {
      if (!buffer.readOnly()) {
        buffer.makeReadOnly();
      }
      return buffer.copy(true);
    }
  };

  private static volatile BufferOwnershipStrategy STRATEGY = BufferOwnershipStrategy.COPY_ON_TRANSFER;

  public static void ownershipStrategy(BufferOwnershipStrategy strategy) {
    Objects.requireNonNull(strategy);
    STRATEGY = strategy;
  }

  public static BufferOwnershipStrategy ownershipStrategy() {
    return STRATEGY;
  }

  private static final BufferAllocator UNPOOLED_HEAP_ALLOCATOR = BufferAllocator.onHeapUnpooled();

  public Buffer buffer() {
    return buffer(0);
  }

  public Buffer buffer(int initialSizeHint) {
    return new BufferImpl(this, UNPOOLED_HEAP_ALLOCATOR.allocate(initialSizeHint));
  }

  public Buffer buffer(String str) {
    return buffer(str, "UTF-8");
  }

  public Buffer buffer(String str, String cs) {
    return buffer(str.getBytes(Charset.forName(cs)));
  }
  public Buffer buffer(byte[] bytes) {
    return new BufferImpl(this, UNPOOLED_HEAP_ALLOCATOR.copyOf(bytes));
  }

  public Buffer buffer(io.netty5.buffer.api.Buffer buffer) {
    return new BufferImpl(this, buffer);
  }

  public abstract io.netty5.buffer.api.Buffer wrap(io.netty5.buffer.api.Buffer buffer);

  public abstract io.netty5.buffer.api.Buffer unwrap(io.netty5.buffer.api.Buffer buffer);

}
