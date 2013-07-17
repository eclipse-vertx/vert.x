package org.vertx.java.core.net.impl;


import io.netty.buffer.*;

public class PartialPooledByteBufAllocator implements ByteBufAllocator {
  private static final ByteBufAllocator POOLED = new PooledByteBufAllocator(false);
  private static final ByteBufAllocator UNPOOLED = new UnpooledByteBufAllocator(false);

  public static PartialPooledByteBufAllocator INSTANCE = new PartialPooledByteBufAllocator();

  private PartialPooledByteBufAllocator() {

  }
  @Override
  public ByteBuf buffer() {
    return UNPOOLED.heapBuffer();
  }

  @Override
  public ByteBuf buffer(int initialCapacity) {
    return UNPOOLED.heapBuffer(initialCapacity);
  }

  @Override
  public ByteBuf buffer(int initialCapacity, int maxCapacity) {
    return UNPOOLED.heapBuffer(initialCapacity, maxCapacity);
  }

  @Override
  public ByteBuf ioBuffer() {
    return UNPOOLED.heapBuffer();
  }

  @Override
  public ByteBuf ioBuffer(int initialCapacity) {
    return UNPOOLED.heapBuffer(initialCapacity);
  }

  @Override
  public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
    return UNPOOLED.heapBuffer(initialCapacity, maxCapacity);
  }

  @Override
  public ByteBuf heapBuffer() {
    return UNPOOLED.heapBuffer();
  }

  @Override
  public ByteBuf heapBuffer(int initialCapacity) {
    return UNPOOLED.heapBuffer(initialCapacity);
  }

  @Override
  public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
    return UNPOOLED.heapBuffer(initialCapacity, maxCapacity);
  }

  @Override
  public ByteBuf directBuffer() {
    return POOLED.directBuffer();
  }

  @Override
  public ByteBuf directBuffer(int initialCapacity) {
    return POOLED.directBuffer(initialCapacity);
  }

  @Override
  public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
    return POOLED.directBuffer(initialCapacity, maxCapacity);
  }

  @Override
  public CompositeByteBuf compositeBuffer() {
    return UNPOOLED.compositeHeapBuffer();
  }

  @Override
  public CompositeByteBuf compositeBuffer(int maxNumComponents) {
    return UNPOOLED.compositeHeapBuffer(maxNumComponents);
  }

  @Override
  public CompositeByteBuf compositeHeapBuffer() {
    return UNPOOLED.compositeHeapBuffer();
  }

  @Override
  public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
    return UNPOOLED.compositeHeapBuffer(maxNumComponents);
  }

  @Override
  public CompositeByteBuf compositeDirectBuffer() {
    return POOLED.compositeDirectBuffer();
  }

  @Override
  public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
    return POOLED.compositeDirectBuffer();
  }
}
