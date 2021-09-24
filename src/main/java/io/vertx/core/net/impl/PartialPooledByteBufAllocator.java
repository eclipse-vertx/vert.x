/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;


import io.netty.buffer.*;

/**
 * A {@link io.netty.buffer.ByteBufAllocator} which is partial pooled. Which means only direct {@link io.netty.buffer.ByteBuf}s are pooled. The rest
 * is unpooled.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class PartialPooledByteBufAllocator implements ByteBufAllocator {
  // Make sure we use the same number of areas as EventLoop's to reduce condition.
  // We can remove this once the following netty issue is fixed:
  // See https://github.com/netty/netty/issues/2264
  private static final ByteBufAllocator POOLED = new PooledByteBufAllocator(true);
  public static final ByteBufAllocator UNPOOLED = new UnpooledByteBufAllocator(false);

  public static final PartialPooledByteBufAllocator INSTANCE = new PartialPooledByteBufAllocator();

  private PartialPooledByteBufAllocator() { }

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
    return POOLED.directBuffer();
  }

  @Override
  public ByteBuf ioBuffer(int initialCapacity) {
    return POOLED.directBuffer(initialCapacity);
  }

  @Override
  public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
    return POOLED.directBuffer(initialCapacity, maxCapacity);
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

  @Override
  public boolean isDirectBufferPooled() {
    return true;
  }

  @Override
  public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
    return POOLED.calculateNewCapacity(minNewCapacity, maxCapacity);
  }
}
