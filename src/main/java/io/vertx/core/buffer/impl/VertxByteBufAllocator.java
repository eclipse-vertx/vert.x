/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.buffer.impl;

import io.netty.buffer.*;
import io.netty.util.internal.LongCounter;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

public abstract class VertxByteBufAllocator extends AbstractByteBufAllocator implements ByteBufAllocatorMetricProvider {

  /**
   * Vert.x pooled allocator.
   */
  public static final ByteBufAllocator POOLED_ALLOCATOR = new PooledByteBufAllocator(true);
  /**
   * Vert.x shared unpooled allocator.
   */
  public static final ByteBufAllocator UNPOOLED_ALLOCATOR = new UnpooledByteBufAllocator(false);

  private static final VertxByteBufAllocator UNSAFE_IMPL = new VertxByteBufAllocator() {
    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
      return new VertxUnsafeHeapByteBuf(this, initialCapacity, maxCapacity);
    }
  };

  private static final VertxByteBufAllocator IMPL = new VertxByteBufAllocator() {
    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
      return new VertxHeapByteBuf(this, initialCapacity, maxCapacity);
    }
  };

  public static final VertxByteBufAllocator DEFAULT = PlatformDependent.hasUnsafe() ? UNSAFE_IMPL : IMPL;

  final LongCounter heapCounter = PlatformDependent.newLongCounter();
  private final ByteBufAllocatorMetric metric = new ByteBufAllocatorMetric() {

    @Override
    public long usedHeapMemory() {
      return heapCounter.value();
    }

    @Override
    public long usedDirectMemory() {
      // This is accounted by UNPOOLED_ALLOCATOR
      return 0;
    }

    @Override
    public String toString() {
      return StringUtil.simpleClassName(this) +
        "(usedHeapMemory: " + usedHeapMemory() + "; usedDirectMemory: " + usedDirectMemory() + ')';
    }
  };

  @Override
  protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
    return UNPOOLED_ALLOCATOR.directBuffer(initialCapacity, maxCapacity);
  }

  @Override
  public boolean isDirectBufferPooled() {
    return false;
  }

  @Override
  public ByteBufAllocatorMetric metric() {
    return metric;
  }
}
