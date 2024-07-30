package io.vertx.core.buffer.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;

public class NettyVertxAllocatorCompatibilityTest {

  @Test
  public void reuseDefaultPooledNettyAllocatorsWithoutConfiguration() {
    assertNull(System.getProperty("io.netty.allocator.type"));
    assertTrue(PooledByteBufAllocator.defaultPreferDirect());
    assertSame(PooledByteBufAllocator.DEFAULT, VertxByteBufAllocator.POOLED_ALLOCATOR);
    assertNotSame(UnpooledByteBufAllocator.DEFAULT, VertxByteBufAllocator.UNPOOLED_ALLOCATOR);
  }
}
