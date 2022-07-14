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

package io.vertx5.core.buffer;

import io.netty5.buffer.api.BufferAllocator;
import io.vertx5.core.buffer.impl.BufferOwnershipStrategy;
import io.vertx5.test.core.TestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;
import static io.vertx.test.core.TestUtils.assertIndexOutOfBoundsException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@RunWith(Parameterized.class)
public class BufferTest {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      { BufferOwnershipStrategy.COPY_ON_TRANSFER }, { BufferOwnershipStrategy.COPY_ON_WRITE }, { BufferOwnershipStrategy.SHARED }
    });
  }

  private static final BufferAllocator TEST_ALLOCATOR = BufferAllocator.onHeapUnpooled();
  private static final int MEDIUM_MAX_VALUE = 2 << 23;
  private static io.netty5.buffer.api.Buffer paddedByteBuf(int padding, byte[] bytes) {
    byte[] data = new byte[padding + bytes.length];
    System.arraycopy(bytes, 0, data, padding, bytes.length);
    io.netty5.buffer.api.Buffer buffer = TEST_ALLOCATOR.copyOf(data);
    buffer.readerOffset(padding);
    return buffer;
  }


  private BufferOwnershipStrategy bufferStrategy;

  public BufferTest(BufferOwnershipStrategy bufferStrategy) {
    this.bufferStrategy = bufferStrategy;
  }

  private final Function<byte[], Buffer> PADDED_BUFFER_FACTORY = arr -> bufferStrategy.buffer(paddedByteBuf(5, arr));

  @Test
  public void testConstructorArguments() throws Exception {
//    assertIllegalArgumentException(() -> factory.buffer(-1));
//    assertNullPointerException(() -> factory.buffer((byte[]) null));
//    assertNullPointerException(() -> factory.buffer((String) null));
//    assertNullPointerException(() -> factory.buffer((ByteBuf) null));
//    assertNullPointerException(() -> factory.buffer(null, "UTF-8"));
//    assertNullPointerException(() -> factory.buffer("", null));
  }

  //https://github.com/vert-x/vert.x/issues/561
  @Test
  public void testSetGetInt() throws Exception {
    final int size = 10;
    Buffer buffer = bufferStrategy.buffer(size);
    for (int i = 0; i < size; i++) {
      buffer.setInt(i * 4, (i + 1) * 10);
    }
    for (int i = 0; i < size; i++) {
      assertEquals((i + 1) * 10, buffer.getInt(i * 4));
    }
  }

  @Test
  public void testAppendBuff() throws Exception {
    testAppendBuff(Buffer::buffer);
  }

  @Test
  public void testAppendBuff2() throws Exception {
    testAppendBuff(PADDED_BUFFER_FACTORY);
  }

  public void testAppendBuff(Function<byte[], Buffer> bufferFactory) throws Exception {

    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    Buffer toAppend = bufferFactory.apply(bytes);

    Buffer b = bufferStrategy.buffer();
    b.appendBuffer(toAppend);
    assertEquals(b.length(), bytes.length);

    assertTrue(TestUtils.byteArraysEqual(bytes, b.getBytes()));
    b.appendBuffer(toAppend);
    assertEquals(b.length(), 2 * bytes.length);

    assertNullPointerException(() -> b.appendBuffer(null));
  }

  @Test
  public void testAppendBytes() throws Exception {

    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);

    Buffer b = bufferStrategy.buffer();
    b.appendBytes(bytes);
    assertEquals(b.length(), bytes.length);
    assertTrue(TestUtils.byteArraysEqual(bytes, b.getBytes()));

    b.appendBytes(bytes);
    assertEquals(b.length(), 2 * bytes.length);

    assertNullPointerException(() -> b.appendBytes(null));
  }

  @Test
  public void testAppendBytesWithOffsetAndLen() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    int len = bytesLen - 2;

    Buffer b = bufferStrategy.buffer();
    b.appendBytes(bytes, 1, len);
    assertEquals(b.length(), len);
    byte[] copy = new byte[len];
    System.arraycopy(bytes, 1, copy, 0, len);
    assertTrue(TestUtils.byteArraysEqual(copy, b.getBytes()));

    b.appendBytes(bytes, 1, len);
    assertEquals(b.length(), 2 * len);

    assertNullPointerException(() -> b.appendBytes(null, 1, len));
  }

  @Test
  public void testAppendBufferWithOffsetAndLen() throws Exception {
    testAppendBufferWithOffsetAndLen(Buffer::buffer);
  }

  @Test
  public void testAppendBufferWithOffsetAndLen2() throws Exception {
    testAppendBufferWithOffsetAndLen(PADDED_BUFFER_FACTORY);
  }

  private void testAppendBufferWithOffsetAndLen(Function<byte[], Buffer> bufferFactory) throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    Buffer src = bufferFactory.apply(bytes);

    int len = bytes.length - 2;
    Buffer b = bufferStrategy.buffer();
    b.appendBuffer(src, 1, len);
    assertEquals(b.length(), len);
    byte[] copy = new byte[len];
    System.arraycopy(bytes, 1, copy, 0, len);
    assertTrue(TestUtils.byteArraysEqual(copy, b.getBytes()));

    b.appendBuffer(src, 1, len);
    assertEquals(b.length(), 2 * len);

    assertNullPointerException(() -> b.appendBuffer(null, 1, len));
  }

  @Test
  public void testAppendByte() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);

    Buffer b = bufferStrategy.buffer();
    for (int i = 0; i < bytesLen; i++) {
      b.appendByte(bytes[i]);
    }
    assertEquals(b.length(), bytes.length);
    assertTrue(TestUtils.byteArraysEqual(bytes, b.getBytes()));

    for (int i = 0; i < bytesLen; i++) {
      b.appendByte(bytes[i]);
    }
    assertEquals(b.length(), 2 * bytes.length);
  }

  @Test
  public void testAppendByte2() throws Exception {
    int bytesLen = 100;
    Buffer b = bufferStrategy.buffer(TestUtils.randomByteArray(bytesLen));
    b.setByte(b.length(), (byte) 9);

  }

  @Test
  public void testAppendUnsignedByte() {
    Buffer b = bufferStrategy.buffer(TestUtils.randomByteArray(100));
    b.appendUnsignedByte((short) (Byte.MAX_VALUE + Byte.MAX_VALUE / 2));
    assertEquals(101, b.length());
  }

  @Test
  public void testAppendShort() {
    Buffer b = bufferStrategy.buffer(TestUtils.randomByteArray(100));
    b.appendShort(Short.MAX_VALUE);
    assertEquals(102, b.length());
  }

  @Test
  public void testAppendUnsignedShort() {
    Buffer b = bufferStrategy.buffer(TestUtils.randomByteArray(100));
    b.appendUnsignedShort(Short.MAX_VALUE + Short.MAX_VALUE / 2);
    assertEquals(102, b.length());
  }

  @Test
  public void testAppendInt() {
    Buffer b = bufferStrategy.buffer(TestUtils.randomByteArray(100));
    b.appendInt(Integer.MAX_VALUE);
    assertEquals(104, b.length());
  }

  @Test
  public void testAppendUnsignedInt() {
    Buffer b = bufferStrategy.buffer(TestUtils.randomByteArray(100));
    b.appendUnsignedInt(Integer.MAX_VALUE + (long) Integer.MAX_VALUE / 2);
    assertEquals(104, b.length());
  }

  @Test
  public void testAppendMedium() {
    Buffer b = bufferStrategy.buffer(TestUtils.randomByteArray(100));
    b.appendMedium(MEDIUM_MAX_VALUE);
    assertEquals(103, b.length());
  }

  @Test
  public void testAppendLong() {
    Buffer b = bufferStrategy.buffer(TestUtils.randomByteArray(100));
    b.appendLong(Long.MAX_VALUE);
    assertEquals(108, b.length());
  }

  @Test
  public void testAppendString1() throws Exception {

    String str = TestUtils.randomUnicodeString(100);
    byte[] sb = str.getBytes("UTF-8");

    Buffer b = bufferStrategy.buffer();
    b.appendString(str);
    assertEquals(b.length(), sb.length);
    assertTrue(str.equals(b.toString("UTF-8")));
    assertTrue(str.equals(b.toString(StandardCharsets.UTF_8)));

    assertNullPointerException(() -> b.appendString(null));
    assertNullPointerException(() -> b.appendString(null, "UTF-8"));
    assertNullPointerException(() -> b.appendString("", null));
  }

  @Test
  public void testAppendString2() throws Exception {
    //TODO
  }

  @Test
  public void testGetOutOfBounds() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);

    Buffer b = bufferStrategy.buffer(bytes);
    assertIndexOutOfBoundsException(() -> b.getByte(bytesLen));
    assertIndexOutOfBoundsException(() -> b.getByte(bytesLen + 1));
    assertIndexOutOfBoundsException(() -> b.getByte(bytesLen + 100));
    assertIndexOutOfBoundsException(() -> b.getByte(-1));
    assertIndexOutOfBoundsException(() -> b.getByte(-100));
    assertIndexOutOfBoundsException(() -> b.getInt(bytesLen));
    assertIndexOutOfBoundsException(() -> b.getInt(bytesLen + 1));
    assertIndexOutOfBoundsException(() -> b.getInt(bytesLen + 100));
    assertIndexOutOfBoundsException(() -> b.getInt(-1));
    assertIndexOutOfBoundsException(() -> b.getInt(-100));
    assertIndexOutOfBoundsException(() -> b.getLong(bytesLen));
    assertIndexOutOfBoundsException(() -> b.getLong(bytesLen + 1));
    assertIndexOutOfBoundsException(() -> b.getLong(bytesLen + 100));
    assertIndexOutOfBoundsException(() -> b.getLong(-1));
    assertIndexOutOfBoundsException(() -> b.getLong(-100));
    assertIndexOutOfBoundsException(() -> b.getFloat(bytesLen));
    assertIndexOutOfBoundsException(() -> b.getFloat(bytesLen + 1));
    assertIndexOutOfBoundsException(() -> b.getFloat(bytesLen + 100));
    assertIndexOutOfBoundsException(() -> b.getFloat(-1));
    assertIndexOutOfBoundsException(() -> b.getFloat(-100));
    assertIndexOutOfBoundsException(() -> b.getDouble(bytesLen));
    assertIndexOutOfBoundsException(() -> b.getDouble(bytesLen + 1));
    assertIndexOutOfBoundsException(() -> b.getDouble(bytesLen + 100));
    assertIndexOutOfBoundsException(() -> b.getDouble(-1));
    assertIndexOutOfBoundsException(() -> b.getDouble(-100));
    assertIndexOutOfBoundsException(() -> b.getShort(bytesLen));
    assertIndexOutOfBoundsException(() -> b.getShort(bytesLen + 1));
    assertIndexOutOfBoundsException(() -> b.getShort(bytesLen + 100));
    assertIndexOutOfBoundsException(() -> b.getShort(-1));
    assertIndexOutOfBoundsException(() -> b.getShort(-100));
    assertIndexOutOfBoundsException(() -> b.getBytes(bytesLen + 1, bytesLen + 1));
    assertIndexOutOfBoundsException(() -> b.getBytes(bytesLen + 100, bytesLen + 100));
    assertIndexOutOfBoundsException(() -> b.getBytes(-1, -1));
    assertIndexOutOfBoundsException(() -> b.getBytes(-100, -100));
    assertIndexOutOfBoundsException(() -> b.getString(-1, bytesLen));
    assertIndexOutOfBoundsException(() -> b.getString(0, bytesLen + 1));
    assertIllegalArgumentException(() -> b.getString(2, 1));
    assertIndexOutOfBoundsException(() -> b.getString(-1, bytesLen, "UTF-8"));
    assertIndexOutOfBoundsException(() -> b.getString(0, bytesLen + 1, "UTF-8"));
    assertIllegalArgumentException(() -> b.getString(2, 1, "UTF-8"));
  }

  @Test
  public void testSetOutOfBounds() throws Exception {
    Buffer b = bufferStrategy.buffer(numSets);

    assertIndexOutOfBoundsException(() -> b.setByte(-1, (byte) 0));
    assertIndexOutOfBoundsException(() -> b.setInt(-1, 0));
    assertIndexOutOfBoundsException(() -> b.setLong(-1, 0));
    assertIndexOutOfBoundsException(() -> b.setDouble(-1, 0));
    assertIndexOutOfBoundsException(() -> b.setFloat(-1, 0));
    assertIndexOutOfBoundsException(() -> b.setShort(-1, (short) 0));
    assertIndexOutOfBoundsException(() -> b.setBuffer(-1, b));
    assertIndexOutOfBoundsException(() -> b.setBuffer(0, b, -1, 0));
    assertIndexOutOfBoundsException(() -> b.setBuffer(0, b, 0, -1));
    assertIndexOutOfBoundsException(() -> b.setBytes(-1, TestUtils.randomByteArray(1)));
    assertIndexOutOfBoundsException(() -> b.setBytes(-1, TestUtils.randomByteArray(1), -1, 0));
    assertIndexOutOfBoundsException(() -> b.setBytes(-1, TestUtils.randomByteArray(1), 0, -1));
    assertIndexOutOfBoundsException(() -> b.setString(-1, ""));
    assertIndexOutOfBoundsException(() -> b.setString(-1, "", "UTF-8"));
  }

  @Test
  public void testGetByte() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);

    Buffer b = bufferStrategy.buffer(bytes);
    for (int i = 0; i < bytesLen; i++) {
      assertEquals(bytes[i], b.getByte(i));
    }
  }

  @Test
  public void testGetUnsignedByte() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);

    Buffer b = bufferStrategy.buffer(bytes);
    for (int i = 0; i < bytesLen; i++) {
      assertEquals(Byte.toUnsignedLong(bytes[i]), b.getUnsignedByte(i));
    }
  }

  @Test
  public void testGetInt() throws Exception {
    int numInts = 100;
    Buffer b = bufferStrategy.buffer(numInts * 4);
    for (int i = 0; i < numInts; i++) {
      b.setInt(i * 4, i);
    }

    for (int i = 0; i < numInts; i++) {
      assertEquals(i, b.getInt(i * 4));
    }
  }

  @Test
  public void testGetUnsignedInt() throws Exception {
    int numInts = 100;
    Buffer b = bufferStrategy.buffer(numInts * 4);
    for (int i = 0; i < numInts; i++) {
      b.setUnsignedInt(i * 4, (int) (Integer.MAX_VALUE + (long) i));
    }

    for (int i = 0; i < numInts; i++) {
      assertEquals(Integer.toUnsignedLong(Integer.MAX_VALUE + i), b.getUnsignedInt(i * 4));
    }
  }

  @Test
  public void testGetMedium() throws Exception {
    int numInts = 100;
    Buffer b = bufferStrategy.buffer(numInts * 3);
    for (int i = 0; i < numInts; i++) {
      b.setMedium(i * 3, MEDIUM_MAX_VALUE + i);
    }

    for (int i = 0; i < numInts; i++) {
      assertEquals((MEDIUM_MAX_VALUE + i - MEDIUM_MAX_VALUE), b.getMedium(i * 3));
    }
  }

  @Test
  public void testGetUnsignedMedium() throws Exception {
    int numInts = 100;
    int MEDIUM_MAX_VALUE = BufferTest.MEDIUM_MAX_VALUE - numInts;
    Buffer b = bufferStrategy.buffer(numInts * 3);
    for (int i = 0; i < numInts; i++) {
      b.setMedium(i * 3, (MEDIUM_MAX_VALUE + i));
    }

    for (int i = 0; i < numInts; i++) {
      assertEquals(Integer.toUnsignedLong(MEDIUM_MAX_VALUE + i), b.getUnsignedMedium(i * 3));
    }
  }

  @Test
  public void testGetLong() throws Exception {
    int numLongs = 100;
    Buffer b = bufferStrategy.buffer(numLongs * 8);
    for (int i = 0; i < numLongs; i++) {
      b.setLong(i * 8, i);
    }

    for (int i = 0; i < numLongs; i++) {
      assertEquals(i, b.getLong(i * 8));
    }
  }

  @Test
  public void testGetFloat() throws Exception {
    int numFloats = 100;
    Buffer b = bufferStrategy.buffer(numFloats * 4);
    for (int i = 0; i < numFloats; i++) {
      b.setFloat(i * 4, i);
    }

    for (int i = 0; i < numFloats; i++) {
      assertEquals((float) i, b.getFloat(i * 4), 0);
    }
  }

  @Test
  public void testGetDouble() throws Exception {
    int numDoubles = 100;
    Buffer b = bufferStrategy.buffer(numDoubles * 8);
    for (int i = 0; i < numDoubles; i++) {
      b.setDouble(i * 8, i);
    }

    for (int i = 0; i < numDoubles; i++) {
      assertEquals((double) i, b.getDouble(i * 8), 0);
    }
  }

  @Test
  public void testGetShort() throws Exception {
    int numShorts = 100;
    Buffer b = bufferStrategy.buffer(numShorts * 2);
    for (short i = 0; i < numShorts; i++) {
      b.setShort(i * 2, i);
    }

    for (short i = 0; i < numShorts; i++) {
      assertEquals(i, b.getShort(i * 2));
    }
  }

  @Test
  public void testGetUnsignedShort() throws Exception {
    int numShorts = 100;
    Buffer b = bufferStrategy.buffer(numShorts * 2);
    for (short i = 0; i < numShorts; i++) {
      b.setUnsignedShort(i * 2, (short) (Short.MAX_VALUE + (int) i));
    }

    for (short i = 0; i < numShorts; i++) {
      assertEquals(Integer.toUnsignedLong(Short.MAX_VALUE + i), b.getUnsignedShort(i * 2));
    }
  }

  @Test
  public void testGetString() throws Exception {
    String str = TestUtils.randomAlphaString(100);
    Buffer b = bufferStrategy.buffer(str, "UTF-8"); // encode ascii as UTF-8 so one byte per char
    assertEquals(100, b.length());
    String substr = b.getString(10, 20);
    assertEquals(str.substring(10, 20), substr);
    substr = b.getString(10, 20, "UTF-8");
    assertEquals(str.substring(10, 20), substr);
  }

  @Test
  public void testGetBytes() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = bufferStrategy.buffer(bytes);

    assertTrue(TestUtils.byteArraysEqual(bytes, b.getBytes()));
  }

  @Test
  public void testGetBytes2() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = bufferStrategy.buffer(bytes);

    byte[] sub = new byte[bytes.length / 2];
    System.arraycopy(bytes, bytes.length / 4, sub, 0, bytes.length / 2);
    assertTrue(TestUtils.byteArraysEqual(sub, b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2)));
  }

  @Test
  public void testGetBytesWithByteArray() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = bufferStrategy.buffer(bytes);

    byte[] sub = new byte[bytes.length / 2];
    System.arraycopy(bytes, bytes.length / 4, sub, 0, bytes.length / 2);

    byte[] result = new byte[bytes.length / 2];
    b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2, result);

    assertTrue(TestUtils.byteArraysEqual(sub, result));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testGetBytesWithTooSmallByteArray() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = bufferStrategy.buffer(bytes);
    byte[] result = new byte[bytes.length / 4];
    b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2, result);
  }

  @Test
  public void testGetBytesWithByteArrayFull() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = bufferStrategy.buffer(bytes);

    byte[] sub = new byte[bytes.length];
    System.arraycopy(bytes, bytes.length / 4, sub, 12, bytes.length / 2);

    byte[] result = new byte[bytes.length];
    b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2, result, 12);

    assertTrue(TestUtils.byteArraysEqual(sub, result));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testGetBytesWithBadOffset() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = bufferStrategy.buffer(bytes);
    byte[] result = new byte[bytes.length / 2];
    b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2, result, -1);
  }

  private final int numSets = 100;

  @Test
  public void testSetInt() throws Exception {
    testSetInt(bufferStrategy.buffer(numSets * 4));
  }

  @Test
  public void testSetIntExpandBuffer() throws Exception {
    testSetInt(bufferStrategy.buffer());
  }

  private void testSetInt(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setInt(i * 4, i);
    }
    for (int i = 0; i < numSets; i++) {
      assertEquals(i, buff.getInt(i * 4));
    }
  }

  @Test
  public void testSetUnsignedInt() throws Exception {
    testSetUnsignedInt(bufferStrategy.buffer(numSets * 4));
  }

  @Test
  public void testSetUnsignedIntExpandBuffer() throws Exception {
    testSetUnsignedInt(bufferStrategy.buffer());
  }

  private void testSetUnsignedInt(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      long val = Integer.toUnsignedLong(Integer.MAX_VALUE + i);
      buff.setUnsignedInt(i * 4, val);
    }
    for (int i = 0; i < numSets; i++) {
      long val = Integer.toUnsignedLong(Integer.MAX_VALUE + i);
      assertEquals(val, buff.getUnsignedInt(i * 4));
    }
  }

  @Test
  public void testSetLong() throws Exception {
    testSetLong(bufferStrategy.buffer(numSets * 8));
  }

  @Test
  public void testSetLongExpandBuffer() throws Exception {
    testSetLong(bufferStrategy.buffer());
  }

  private void testSetLong(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setLong(i * 8, i);
    }
    for (int i = 0; i < numSets; i++) {
      assertEquals(i, buff.getLong(i * 8));
    }
  }

  @Test
  public void testSetByte() throws Exception {
    testSetByte(bufferStrategy.buffer(numSets));
  }

  @Test
  public void testSetByteExpandBuffer() throws Exception {
    testSetByte(bufferStrategy.buffer());
  }

  private void testSetByte(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setByte(i, (byte) i);
    }
    for (int i = 0; i < numSets; i++) {
      assertEquals(i, buff.getByte(i));
    }
  }

  @Test
  public void testSetUnsignedByte() throws Exception {
    testSetUnsignedByte(bufferStrategy.buffer(numSets));
  }

  @Test
  public void testSetUnsignedByteExpandBuffer() throws Exception {
    testSetUnsignedByte(bufferStrategy.buffer());
  }

  private void testSetUnsignedByte(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      int val = Byte.MAX_VALUE + i;
      buff.setUnsignedByte(i, (short) val);
    }
    for (int i = 0; i < numSets; i++) {
      int val = Byte.MAX_VALUE + i;
      assertEquals(val, buff.getUnsignedByte(i));
    }
  }

  @Test
  public void testSetFloat() throws Exception {
    testSetFloat(bufferStrategy.buffer(numSets * 4));
  }

  @Test
  public void testSetFloatExpandBuffer() throws Exception {
    testSetFloat(bufferStrategy.buffer());
  }

  private void testSetFloat(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setFloat(i * 4, (float) i);
    }
    for (int i = 0; i < numSets; i++) {
      assertEquals((float) i, buff.getFloat(i * 4), 0);
    }
  }

  @Test
  public void testSetDouble() throws Exception {
    testSetDouble(bufferStrategy.buffer(numSets * 8));
  }

  @Test
  public void testSetDoubleExpandBuffer() throws Exception {
    testSetDouble(bufferStrategy.buffer());
  }

  private void testSetDouble(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setDouble(i * 8, (double) i);
    }
    for (int i = 0; i < numSets; i++) {
      assertEquals((double) i, buff.getDouble(i * 8), 0);
    }
  }

  @Test
  public void testSetShort() throws Exception {
    testSetShort(bufferStrategy.buffer(numSets * 2));
  }

  @Test
  public void testSetShortExpandBuffer() throws Exception {
    testSetShort(bufferStrategy.buffer());
  }

  private void testSetShort(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setShort(i * 2, (short) i);
    }
    for (int i = 0; i < numSets; i++) {
      assertEquals(i, buff.getShort(i * 2));
    }
  }

  @Test
  public void testSetUnsignedShort() throws Exception {
    testSetUnsignedShort(bufferStrategy.buffer(numSets * 2));
  }

  @Test
  public void testSetUnsignedShortExpandBuffer() throws Exception {
    testSetUnsignedShort(bufferStrategy.buffer());
  }

  private void testSetUnsignedShort(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      int val = Short.MAX_VALUE + i;
      buff.setUnsignedShort(i * 2, val);
    }
    for (int i = 0; i < numSets; i++) {
      int val = Short.MAX_VALUE + i;
      assertEquals(val, buff.getUnsignedShort(i * 2));
    }
  }

  @Test
  public void testSetBytesBuffer() throws Exception {
    testSetBytesBuffer(bufferStrategy.buffer(150), Buffer::buffer);
    assertNullPointerException(() -> bufferStrategy.buffer(150).setBytes(0, (ByteBuffer) null));
  }

  @Test
  public void testSetBytesBuffer2() throws Exception {
    testSetBytesBuffer(bufferStrategy.buffer(150), PADDED_BUFFER_FACTORY);
    assertNullPointerException(() -> bufferStrategy.buffer(150).setBytes(0, (ByteBuffer) null));
  }

  private void testSetBytesBuffer(Buffer buff, Function<byte[], Buffer> bufferFactory) throws Exception {
    byte[] str = TestUtils.randomAlphaString(100).getBytes();
    Buffer b = bufferFactory.apply(str);
    buff.setBuffer(10, b);
    byte[] b2 = buff.getBytes(10, 110);
    assertEquals(b, bufferStrategy.buffer(b2));

    byte[] b3 = TestUtils.randomByteArray(100);
    buff.setBytes(10, b3);
    byte[] b4 = buff.getBytes(10, 110);
    assertEquals(bufferStrategy.buffer(b3), bufferStrategy.buffer(b4));
  }

  @Test
  public void testSetBytesBufferExpandBuffer() throws Exception {
    testSetShort(bufferStrategy.buffer());
  }

  @Test
  public void testSetBytesWithOffsetAndLen() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    int len = bytesLen - 2;

    Buffer b = bufferStrategy.buffer();
    b.setByte(0, (byte) '0');
    b.setBytes(1, bytes, 1, len);
    assertEquals(b.length(), len + 1);
    byte[] copy = new byte[len];
    System.arraycopy(bytes, 1, copy, 0, len);

    assertTrue(TestUtils.byteArraysEqual(copy, b.getBytes(1, b.length())));

    b.setBytes(b.length(), bytes, 1, len);
    assertEquals(b.length(), 2 * len + 1);

    assertNullPointerException(() -> bufferStrategy.buffer(150).setBytes(0, (byte[]) null));
    assertNullPointerException(() -> bufferStrategy.buffer(150).setBytes(0, null, 1, len));
  }

  @Test
  public void testSetBufferWithOffsetAndLen() throws Exception {
    testSetBufferWithOffsetAndLen(Buffer::buffer);
  }

  @Test
  public void testSetBufferWithOffsetAndLen2() throws Exception {
    testSetBufferWithOffsetAndLen(PADDED_BUFFER_FACTORY);
  }

  private void testSetBufferWithOffsetAndLen(Function<byte[], Buffer> bufferFactory) throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    Buffer src = bufferFactory.apply(bytes);
    int len = bytesLen - 2;

    Buffer b = bufferStrategy.buffer();
    b.setByte(0, (byte) '0');
    b.setBuffer(1, src, 1, len);
    assertEquals(b.length(), len + 1);
    byte[] copy = new byte[len];
    System.arraycopy(bytes, 1, copy, 0, len);

    assertTrue(TestUtils.byteArraysEqual(copy, b.getBytes(1, b.length())));

    b.setBuffer(b.length(), src, 1, len);
    assertEquals(b.length(), 2 * len + 1);

    assertNullPointerException(() -> b.setBuffer(1, null));
    assertNullPointerException(() -> b.setBuffer(1, null, 0, len));
  }

  @Test
  public void testSetBytesString() throws Exception {
    testSetBytesString(bufferStrategy.buffer(150));
  }

  @Test
  public void testSetBytesStringExpandBuffer() throws Exception {
    testSetBytesString(bufferStrategy.buffer());
  }

  private void testSetBytesString(Buffer buff) throws Exception {

    String str = TestUtils.randomUnicodeString(100);
    buff.setString(50, str);

    byte[] b1 = buff.getBytes(50, buff.length());
    String str2 = new String(b1, "UTF-8");

    assertEquals(str, str2);

    assertNullPointerException(() -> bufferStrategy.buffer(150).setString(0, null));
    assertNullPointerException(() -> bufferStrategy.buffer(150).setString(0, null, "UTF-8"));

    //TODO setString with encoding
  }

  @Test
  public void testToString() throws Exception {
    String str = TestUtils.randomUnicodeString(100);
    Buffer buff = bufferStrategy.buffer(str);
    assertEquals(str, buff.toString());

    //TODO toString with encoding
  }

  @Test
  public void testCopy() throws Exception {
    Buffer buff = TestUtils.randomBuffer(100);
    assertEquals(buff, buff.copy());

    Buffer copy = buff.getBuffer(0, buff.length());
    assertEquals(buff, copy);

    //Make sure they don't share underlying buffer
    Buffer copy2 = buff.copy();
    buff.setInt(0, 1);
    assertEquals(copy, copy2);
  }

  @Test
  public void testCreateBuffers() throws Exception {
    Buffer buff = bufferStrategy.buffer(1000);
    assertEquals(0, buff.length());

    String str = TestUtils.randomUnicodeString(100);
    buff = bufferStrategy.buffer(str);
    assertEquals(buff.length(), str.getBytes("UTF-8").length);
    assertEquals(str, buff.toString());

    // TODO create with string with encoding

    byte[] bytes = TestUtils.randomByteArray(100);
    buff = bufferStrategy.buffer(bytes);
    assertEquals(buff.length(), bytes.length);
    assertEquals(bufferStrategy.buffer(bytes), bufferStrategy.buffer(buff.getBytes()));
  }

//  @Test
//  public void testToJsonObject() throws Exception {
//    JsonObject obj = new JsonObject();
//    obj.put("wibble", "wibble_value");
//    obj.put("foo", 5);
//    obj.put("bar", true);
//    Buffer buff = factory.buffer(obj.encode());
//    assertEquals(obj, buff.toJsonObject());
//
//    buff = factory.buffer(TestUtils.randomAlphaString(10));
//    try {
//      buff.toJsonObject();
//      fail();
//    } catch (DecodeException ignore) {
//    }
//  }
//
//  @Test
//  public void testToJsonArray() throws Exception {
//    JsonArray arr = new JsonArray();
//    arr.add("wibble");
//    arr.add(5);
//    arr.add(true);
//    Buffer buff = factory.buffer(arr.encode());
//    assertEquals(arr, buff.toJsonArray());
//
//    buff = factory.buffer(TestUtils.randomAlphaString(10));
//    try {
//      buff.toJsonObject();
//      fail();
//    } catch (DecodeException ignore) {
//    }
//  }

  @Test
  public void testLength() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer buffer = bufferStrategy.buffer(bytes);
    assertEquals(100, bufferStrategy.buffer(buffer.unwrap()).length());
  }

//  @Test
//  public void testLength2() throws Exception {
//    byte[] bytes = TestUtils.randomByteArray(100);
//    assertEquals(90, factory.buffer(Unpooled.copiedBuffer(bytes).slice(10, 90)).length());
//  }
//
//  @Test
//  public void testAppendDoesNotModifyByteBufIndex() throws Exception {
//    ByteBuf buf = Unpooled.copiedBuffer("foobar".getBytes());
//    assertEquals(0, buf.readerIndex());
//    assertEquals(6, buf.writerIndex());
//    Buffer buffer = factory.buffer(buf);
//    Buffer other = factory.buffer("prefix");
//    other.appendBuffer(buffer);
//    assertEquals(0, buf.readerIndex());
//    assertEquals(6, buf.writerIndex());
//    assertEquals(other.toString(), "prefixfoobar");
//  }
//
  @Test
  public void testAppendExpandsBufferWhenMaxCapacityReached() {
    Buffer buff = bufferStrategy.buffer(TEST_ALLOCATOR.allocate(0));
    buff.appendString("Hello World");
  }

//
//  @Test
//  public void testWriteExpandsBufferWhenMaxCapacityReached() {
//    String s = "Hello World";
//    ByteBuf byteBuf = Unpooled.buffer(0, s.length() - 1);
//    Buffer buff = factory.buffer(byteBuf);
//    int idx = 0;
//    for (byte b : s.getBytes()) {
//      buff.setByte(idx++, b);
//    }
//  }
//
//  @Test
//  public void testSetByteAfterCurrentWriterIndexWithoutExpandingCapacity() {
//    ByteBuf byteBuf = Unpooled.buffer(10, Integer.MAX_VALUE);
//    byteBuf.writerIndex(5);
//    Buffer buff = factory.buffer(byteBuf);
//    buff.setByte(7, (byte)1);
//    assertEquals(8, buff.length());
//  }
//
//  @Test
//  public void testGetByteBuf() {
//    ByteBuf byteBuf = Unpooled.buffer();
//    byteBuf.writeCharSequence("Hello World", StandardCharsets.UTF_8);
//    Buffer buff = factory.buffer(byteBuf);
//    ByteBuf duplicate = buff.getByteBuf();
//    duplicate.writerIndex(5);
//    assertEquals(11, byteBuf.writerIndex());
//    assertEquals(1, duplicate.refCnt());
//    duplicate.release();
//    assertEquals(1, duplicate.refCnt());
//  }
//
//  @Test
//  public void testGetXXXUpperBound() {
//    checkGetXXXUpperBound((buff, idx) -> buff.getByte(idx), 1);
//    checkGetXXXUpperBound((buff, idx) -> buff.getUnsignedByte(idx), 1);
//    checkGetXXXUpperBound((buff, idx) -> buff.getShort(idx), 2);
//    checkGetXXXUpperBound((buff, idx) -> buff.getShortLE(idx), 2);
//    checkGetXXXUpperBound((buff, idx) -> buff.getUnsignedShort(idx), 2);
//    checkGetXXXUpperBound((buff, idx) -> buff.getUnsignedShortLE(idx), 2);
//    checkGetXXXUpperBound((buff, idx) -> buff.getMedium(idx), 3);
//    checkGetXXXUpperBound((buff, idx) -> buff.getMediumLE(idx), 3);
//    checkGetXXXUpperBound((buff, idx) -> buff.getUnsignedMedium(idx), 3);
//    checkGetXXXUpperBound((buff, idx) -> buff.getUnsignedMediumLE(idx), 3);
//    checkGetXXXUpperBound((buff, idx) -> buff.getInt(idx), 4);
//    checkGetXXXUpperBound((buff, idx) -> buff.getIntLE(idx), 4);
//    checkGetXXXUpperBound((buff, idx) -> buff.getUnsignedInt(idx), 4);
//    checkGetXXXUpperBound((buff, idx) -> buff.getUnsignedIntLE(idx), 4);
//    checkGetXXXUpperBound((buff, idx) -> buff.getLong(idx), 8);
//    checkGetXXXUpperBound((buff, idx) -> buff.getLongLE(idx), 8);
//    checkGetXXXUpperBound((buff, idx) -> buff.getFloat(idx), 4);
//    checkGetXXXUpperBound((buff, idx) -> buff.getDouble(idx), 8);
//  }
//
//  private <T> void checkGetXXXUpperBound(BiFunction<Buffer, Integer, T> f, int size) {
//    Buffer buffer = factory.buffer();
//    for (int i = 0;i < size;i++) {
//      buffer.appendByte((byte)0);
//    }
//    assertIndexOutOfBoundsException(() -> f.apply(buffer, -1));
//    f.apply(buffer, 0);
//    assertIndexOutOfBoundsException(() -> f.apply(buffer, 1));
//  }
//
//  @Test
//  public void testReadOnlyByteBuf() {
//    String s = "Hello World";
//    ByteBuf byteBuf = Unpooled.buffer(0, s.length() - 1);
//    Buffer buff = factory.buffer(byteBuf.asReadOnly());
//    assertSame(buff, buff.copy());
//  }

  @Test
  public void testFoo() {
    Buffer buff = bufferStrategy.buffer("Hello World");
    io.netty5.buffer.api.Buffer actual1 = buff.unwrap();
    io.netty5.buffer.api.Buffer actual2 = buff.unwrap();
    assertEquals('H', actual1.readByte());
    assertEquals('H', actual2.readByte());


  }

}
