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

package io.vertx.tests.buffer;

import io.netty.buffer.*;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.buffer.impl.VertxHeapByteBuf;
import io.vertx.core.buffer.impl.VertxUnsafeHeapByteBuf;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.buffer.BufferInternal;
import org.junit.Test;

import static org.junit.Assert.*;

public class VertxBufferTest {

  @Test
  public void testAllocateVertxBuffer() {
    BufferImpl buffer = new BufferImpl();
    ByteBuf byteBuf = buffer.byteBuf();
    assertTrue(byteBuf instanceof VertxHeapByteBuf || byteBuf instanceof VertxUnsafeHeapByteBuf);
  }

  @Test
  public void testUnreleasable() {
    BufferImpl buffer = new BufferImpl();
    ByteBuf byteBuf = buffer.byteBuf();
    assertEquals(1, byteBuf.refCnt());
    byteBuf.release();
    assertEquals(1, byteBuf.refCnt());
  }

  @Test
  public void testDuplicate() {
    BufferImpl buffer = new BufferImpl();
    buffer.appendString("Hello World");
    ByteBuf byteBuf = buffer.byteBuf();
    ByteBuf duplicate = buffer.getByteBuf();
    assertEquals(1, byteBuf.refCnt());
    duplicate.release();
    assertEquals(1, duplicate.refCnt());
    assertEquals(1, byteBuf.refCnt());
    duplicate.readerIndex(3);
    assertEquals(3, duplicate.readerIndex());
    assertEquals(0, byteBuf.readerIndex());
    ByteBuf duplicateSlice = duplicate.slice(0, 5);
    duplicateSlice.release();
    assertEquals(1, duplicateSlice.refCnt());
    assertEquals(1, duplicate.refCnt());
    assertEquals(1, byteBuf.refCnt());
    duplicateSlice.readerIndex(1);
    assertEquals(1, duplicateSlice.readerIndex());
    assertEquals(3, duplicate.readerIndex());
    assertEquals(0, byteBuf.readerIndex());
  }

  @Test
  public void testSafeBuffer() {
    assertCopyAndRelease(AdaptiveByteBufAllocator.DEFAULT.heapBuffer().writeByte('A'));
    assertCopyAndRelease(AdaptiveByteBufAllocator.DEFAULT.directBuffer().writeByte('A'));
    assertCopyAndRelease(PooledByteBufAllocator.DEFAULT.heapBuffer().writeByte('A'));
    assertCopyAndRelease(PooledByteBufAllocator.DEFAULT.directBuffer().writeByte('A'));
    assertCopyAndRelease(new CompositeByteBuf(UnpooledByteBufAllocator.DEFAULT, false, 10).writeByte('A'));
    assertWrap(Unpooled.buffer().writeByte('A'));
    assertWrap(VertxByteBufAllocator.DEFAULT.heapBuffer().writeByte('A'));
    assertWrap(VertxByteBufAllocator.DEFAULT.directBuffer().writeByte('A'));
    assertWrap(UnpooledByteBufAllocator.DEFAULT.heapBuffer().writeByte('A'));
    assertWrap(UnpooledByteBufAllocator.DEFAULT.directBuffer().writeByte('A'));
  }

  private static void assertCopyAndRelease(ByteBuf bbuf) {
    BufferImpl buffer = (BufferImpl) BufferInternal.safeBuffer(bbuf);
    assertNotSame(bbuf, buffer.byteBuf());
    assertEquals(0, bbuf.refCnt());
  }

  private static void assertWrap(ByteBuf bbuf) {
    BufferImpl buffer = (BufferImpl) BufferInternal.safeBuffer(bbuf);
    assertSame(bbuf, buffer.byteBuf());
    assertEquals(1, bbuf.refCnt());
    bbuf.release();
  }
}
