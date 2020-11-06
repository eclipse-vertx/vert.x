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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.IllegalReferenceCountException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;
import static io.vertx.test.core.TestUtils.assertIndexOutOfBoundsException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
}
