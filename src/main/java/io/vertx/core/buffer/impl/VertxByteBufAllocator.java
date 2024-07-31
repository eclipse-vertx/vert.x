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

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;

public abstract class VertxByteBufAllocator extends AbstractByteBufAllocator {

  /**
   * Vert.x pooled allocator.<br>
   * Setting {@code -Dio.netty.allocator.type=adaptive} will use {@link AdaptiveByteBufAllocator} in
   * {@link ByteBufAllocator#DEFAULT} instead of the default {@link PooledByteBufAllocator#DEFAULT} one.<br>
   * Setting anything different from {@code adaptive} will still use {@link PooledByteBufAllocator#DEFAULT} as the allocator.
   */
  public static final ByteBufAllocator POOLED_ALLOCATOR = pooledAllocator();

  /**
   * Tries hard to reuse the existing default Netty allocators instances to avoid creating new instances.
   */
  private static ByteBufAllocator pooledAllocator() {
    String allocType = allocType();
    if (PooledByteBufAllocator.defaultPreferDirect()) {
      if ("adaptive".equals(allocType)) {
        assert ByteBufAllocator.DEFAULT instanceof AdaptiveByteBufAllocator;
        return ByteBufAllocator.DEFAULT;
      } else {
        return PooledByteBufAllocator.DEFAULT;
      }
    } else {
      if ("adaptive".equals(allocType)) {
        return new AdaptiveByteBufAllocator(true);
      } else {
        return new PooledByteBufAllocator(true);
      }
    }
  }

  /**
   * This is how currently Netty decide which allocator to use
   * @see <a href="https://github.com/netty/netty/blob/netty-4.1.112.Final/buffer/src/main/java/io/netty/buffer/ByteBufUtil.java#L80-L98">ByteBufUtil</a>
   */
  private static String allocType() {
    return SystemPropertyUtil.get("io.netty.allocator.type", PlatformDependent.isAndroid() ? "unpooled" : "pooled");
  }

  /**
   * Vert.x shared unpooled allocator.
   */
  public static final ByteBufAllocator UNPOOLED_ALLOCATOR = !PooledByteBufAllocator.defaultPreferDirect() ?
    UnpooledByteBufAllocator.DEFAULT :
    new UnpooledByteBufAllocator(false);

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

  @Override
  protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
    return UNPOOLED_ALLOCATOR.directBuffer(initialCapacity, maxCapacity);
  }

  @Override
  public boolean isDirectBufferPooled() {
    return false;
  }
}
