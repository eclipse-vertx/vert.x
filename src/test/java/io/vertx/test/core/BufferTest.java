/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static io.vertx.test.core.TestUtils.*;
import static org.junit.Assert.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BufferTest {

  private static ByteBuf paddedByteBuf(int padding, byte[] bytes) {
    byte[] data = new byte[padding + bytes.length];
    System.arraycopy(bytes, 0, data, padding, bytes.length);
    return Unpooled.copiedBuffer(data).slice(padding, bytes.length);
  }

  private static final int MEDIUM_MAX_VALUE = 2 << 23;
  private static final Function<byte[], Buffer> PADDED_BUFFER_FACTORY = arr -> Buffer.buffer(paddedByteBuf(5, arr));

  @Test
  public void testConstructorArguments() throws Exception {
    assertIllegalArgumentException(() -> Buffer.buffer(-1));
    assertNullPointerException(() -> Buffer.buffer((byte[]) null));
    assertNullPointerException(() -> Buffer.buffer((String) null));
    assertNullPointerException(() -> Buffer.buffer((ByteBuf) null));
    assertNullPointerException(() -> Buffer.buffer(null, "UTF-8"));
    assertNullPointerException(() -> Buffer.buffer("", null));
  }

  //https://github.com/vert-x/vert.x/issues/561
  @Test
  public void testSetGetInt() throws Exception {
    final int size = 10;
    Buffer buffer = Buffer.buffer(size);
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

    Buffer b = Buffer.buffer();
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

    Buffer b = Buffer.buffer();
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

    Buffer b = Buffer.buffer();
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
    Buffer b = Buffer.buffer();
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

    Buffer b = Buffer.buffer();
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
    Buffer b = Buffer.buffer(TestUtils.randomByteArray(bytesLen));
    b.setByte(b.length(), (byte) 9);

  }

  @Test
  public void testAppendUnsignedByte() {
    Buffer b = Buffer.buffer(TestUtils.randomByteArray(100));
    b.appendUnsignedByte((short) (Byte.MAX_VALUE + Byte.MAX_VALUE / 2));
    assertEquals(101, b.length());
  }

  @Test
  public void testAppendShort() {
    Buffer b = Buffer.buffer(TestUtils.randomByteArray(100));
    b.appendShort(Short.MAX_VALUE);
    assertEquals(102, b.length());
    b.appendShortLE(Short.MAX_VALUE);
    assertEquals(104, b.length());
  }

  @Test
  public void testAppendUnsignedShort() {
    Buffer b = Buffer.buffer(TestUtils.randomByteArray(100));
    b.appendUnsignedShort(Short.MAX_VALUE + Short.MAX_VALUE / 2);
    assertEquals(102, b.length());
    b.appendUnsignedShortLE(Short.MAX_VALUE + Short.MAX_VALUE / 2);
    assertEquals(104, b.length());
  }

  @Test
  public void testAppendInt() {
    Buffer b = Buffer.buffer(TestUtils.randomByteArray(100));
    b.appendInt(Integer.MAX_VALUE);
    assertEquals(104, b.length());
    b.appendIntLE(Integer.MAX_VALUE);
    assertEquals(108, b.length());
  }

  @Test
  public void testAppendUnsignedInt() {
    Buffer b = Buffer.buffer(TestUtils.randomByteArray(100));
    b.appendUnsignedInt(Integer.MAX_VALUE + (long) Integer.MAX_VALUE / 2);
    assertEquals(104, b.length());
    b.appendUnsignedIntLE(Integer.MAX_VALUE + (long) Integer.MAX_VALUE / 2);
    assertEquals(108, b.length());
  }

  @Test
  public void testAppendMedium() {
    Buffer b = Buffer.buffer(TestUtils.randomByteArray(100));
    b.appendMedium(MEDIUM_MAX_VALUE);
    assertEquals(103, b.length());
    b.appendMediumLE(MEDIUM_MAX_VALUE);
    assertEquals(106, b.length());
  }

  @Test
  public void testAppendLong() {
    Buffer b = Buffer.buffer(TestUtils.randomByteArray(100));
    b.appendLong(Long.MAX_VALUE);
    assertEquals(108, b.length());
    b.appendLongLE(Long.MAX_VALUE);
    assertEquals(116, b.length());
  }

  @Test
  public void testAppendString1() throws Exception {

    String str = TestUtils.randomUnicodeString(100);
    byte[] sb = str.getBytes("UTF-8");

    Buffer b = Buffer.buffer();
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
  public void testLE() {
    checkBEAndLE(2, Buffer.buffer().appendShort(Short.MAX_VALUE), Buffer.buffer().appendShortLE(Short.MAX_VALUE));
    checkBEAndLE(2, Buffer.buffer().appendUnsignedShort(Short.MAX_VALUE), Buffer.buffer().appendUnsignedShortLE(Short.MAX_VALUE));
    checkBEAndLE(3, Buffer.buffer().appendMedium(Integer.MAX_VALUE / 2), Buffer.buffer().appendMediumLE(Integer.MAX_VALUE / 2));
    checkBEAndLE(4, Buffer.buffer().appendInt(Integer.MAX_VALUE), Buffer.buffer().appendIntLE(Integer.MAX_VALUE));
    checkBEAndLE(4, Buffer.buffer().appendUnsignedInt(Integer.MAX_VALUE), Buffer.buffer().appendUnsignedIntLE(Integer.MAX_VALUE));
    checkBEAndLE(8, Buffer.buffer().appendLong(Long.MAX_VALUE), Buffer.buffer().appendLongLE(Long.MAX_VALUE));
  }

  private void checkBEAndLE(int size, Buffer big, Buffer little) {
    for (int i = 0; i < size; i++) {
      byte bigByte = big.getByte(i);
      byte littleByte = little.getByte(size - 1 - i);
      assertEquals(bigByte, littleByte);
    }
  }

  @Test
  public void testGetOutOfBounds() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);

    Buffer b = Buffer.buffer(bytes);
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
    Buffer b = Buffer.buffer(numSets);

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

    Buffer b = Buffer.buffer(bytes);
    for (int i = 0; i < bytesLen; i++) {
      assertEquals(bytes[i], b.getByte(i));
    }
  }

  @Test
  public void testGetUnsignedByte() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);

    Buffer b = Buffer.buffer(bytes);
    for (int i = 0; i < bytesLen; i++) {
      assertEquals(Byte.toUnsignedLong(bytes[i]), b.getUnsignedByte(i));
    }
  }

  private void testGetSetInt(boolean isLE) throws Exception {
    int numInts = 100;
    Buffer b = Buffer.buffer(numInts * 4);
    for (int i = 0; i < numInts; i++) {
      if (isLE) {
        b.setIntLE(i * 4, i);
      } else {
        b.setInt(i * 4, i);
      }
    }

    for (int i = 0; i < numInts; i++) {
      if (isLE) {
        assertEquals(i, b.getIntLE(i * 4));
      } else {
        assertEquals(i, b.getInt(i * 4));
      }
    }
  }

  @Test
  public void testGetInt() throws Exception {
    testGetSetInt(false);
  }

  @Test
  public void testGetIntLE() throws Exception {
    testGetSetInt(true);
  }

  private void testGetSetUnsignedInt(boolean isLE) throws Exception {
    int numInts = 100;
    Buffer b = Buffer.buffer(numInts * 4);
    for (int i = 0; i < numInts; i++) {
      if (isLE) {
        b.setUnsignedIntLE(i * 4, (int) (Integer.MAX_VALUE + (long) i));
      } else {
        b.setUnsignedInt(i * 4, (int) (Integer.MAX_VALUE + (long) i));
      }
    }

    for (int i = 0; i < numInts; i++) {
      if (isLE) {
        assertEquals(Integer.toUnsignedLong(Integer.MAX_VALUE + i), b.getUnsignedIntLE(i * 4));
      } else {
        assertEquals(Integer.toUnsignedLong(Integer.MAX_VALUE + i), b.getUnsignedInt(i * 4));
      }
    }
  }

  @Test
  public void testGetUnsignedInt() throws Exception {
    testGetSetUnsignedInt(false);
  }

  @Test
  public void testGetUnsignedIntLE() throws Exception {
    testGetSetUnsignedInt(true);
  }

  private void testGetSetMedium(boolean isLE) throws Exception {
    int numInts = 100;
    Buffer b = Buffer.buffer(numInts * 3);
    for (int i = 0; i < numInts; i++) {
      if (isLE) {
        b.setMediumLE(i * 3, MEDIUM_MAX_VALUE + i);
      } else {
        b.setMedium(i * 3, MEDIUM_MAX_VALUE + i);
      }
    }

    for (int i = 0; i < numInts; i++) {
      if (isLE) {
        assertEquals((MEDIUM_MAX_VALUE + i - MEDIUM_MAX_VALUE), b.getMediumLE(i * 3));
      } else {
        assertEquals((MEDIUM_MAX_VALUE + i - MEDIUM_MAX_VALUE), b.getMedium(i * 3));
      }
    }
  }

  @Test
  public void testGetMedium() throws Exception {
    testGetSetMedium(false);
  }

  @Test
  public void testGetMediumLE() throws Exception {
    testGetSetMedium(true);
  }

  private void testGetSetUnsignedMedium(boolean isLE) throws Exception {
    int numInts = 100;
    int MEDIUM_MAX_VALUE = BufferTest.MEDIUM_MAX_VALUE - numInts;
    Buffer b = Buffer.buffer(numInts * 3);
    for (int i = 0; i < numInts; i++) {
      if (isLE) {
        b.setMediumLE(i * 3, (MEDIUM_MAX_VALUE + i));
      } else {
        b.setMedium(i * 3, (MEDIUM_MAX_VALUE + i));
      }
    }

    for (int i = 0; i < numInts; i++) {
      if (isLE) {
        assertEquals(Integer.toUnsignedLong(MEDIUM_MAX_VALUE + i), b.getUnsignedMediumLE(i * 3));
      } else {
        assertEquals(Integer.toUnsignedLong(MEDIUM_MAX_VALUE + i), b.getUnsignedMedium(i * 3));
      }
    }
  }

  @Test
  public void testGetUnsignedMedium() throws Exception {
    testGetSetUnsignedMedium(false);
  }

  @Test
  public void testGetUnsignedMediumLE() throws Exception {
    testGetSetUnsignedMedium(true);
  }


  private void testGetSetLong(boolean isLE) throws Exception {
    int numLongs = 100;
    Buffer b = Buffer.buffer(numLongs * 8);
    for (int i = 0; i < numLongs; i++) {
      if (isLE) {
        b.setLongLE(i * 8, i);
      } else {
        b.setLong(i * 8, i);
      }
    }

    for (int i = 0; i < numLongs; i++) {
      if (isLE) {
        assertEquals(i, b.getLongLE(i * 8));
      } else {
        assertEquals(i, b.getLong(i * 8));
      }
    }
  }

  @Test
  public void testGetLong() throws Exception {
    testGetSetLong(false);
  }

  @Test
  public void testGetLongLE() throws Exception {
    testGetSetLong(true);
  }

  @Test
  public void testGetFloat() throws Exception {
    int numFloats = 100;
    Buffer b = Buffer.buffer(numFloats * 4);
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
    Buffer b = Buffer.buffer(numDoubles * 8);
    for (int i = 0; i < numDoubles; i++) {
      b.setDouble(i * 8, i);
    }

    for (int i = 0; i < numDoubles; i++) {
      assertEquals((double) i, b.getDouble(i * 8), 0);
    }
  }

  private void testGetSetShort(boolean isLE) throws Exception {
    int numShorts = 100;
    Buffer b = Buffer.buffer(numShorts * 2);
    for (short i = 0; i < numShorts; i++) {
      if (isLE) {
        b.setShortLE(i * 2, i);
      } else {
        b.setShort(i * 2, i);
      }
    }

    for (short i = 0; i < numShorts; i++) {
      if (isLE) {
        assertEquals(i, b.getShortLE(i * 2));
      } else {
        assertEquals(i, b.getShort(i * 2));
      }
    }
  }

  @Test
  public void testGetShort() throws Exception {
    testGetSetShort(false);
  }

  @Test
  public void testGetShortLE() throws Exception {
    testGetSetShort(true);
  }

  private void testGetSetUnsignedShort(boolean isLE) throws Exception {
    int numShorts = 100;
    Buffer b = Buffer.buffer(numShorts * 2);
    for (short i = 0; i < numShorts; i++) {
      if (isLE) {
        b.setUnsignedShortLE(i * 2, (short) (Short.MAX_VALUE + (int) i));
      } else {
        b.setUnsignedShort(i * 2, (short) (Short.MAX_VALUE + (int) i));
      }
    }

    for (short i = 0; i < numShorts; i++) {
      if (isLE) {
        assertEquals(Integer.toUnsignedLong(Short.MAX_VALUE + i), b.getUnsignedShortLE(i * 2));
      } else {
        assertEquals(Integer.toUnsignedLong(Short.MAX_VALUE + i), b.getUnsignedShort(i * 2));
      }
    }
  }

  @Test
  public void testGetUnsignedShort() throws Exception {
    testGetSetUnsignedShort(false);
  }

  @Test
  public void testGetUnsignedShortLE() throws Exception {
    testGetSetUnsignedShort(true);
  }

  @Test
  public void testGetString() throws Exception {
    String str = TestUtils.randomAlphaString(100);
    Buffer b = Buffer.buffer(str, "UTF-8"); // encode ascii as UTF-8 so one byte per char
    assertEquals(100, b.length());
    String substr = b.getString(10, 20);
    assertEquals(str.substring(10, 20), substr);
    substr = b.getString(10, 20, "UTF-8");
    assertEquals(str.substring(10, 20), substr);
  }

  @Test
  public void testGetBytes() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = Buffer.buffer(bytes);

    assertTrue(TestUtils.byteArraysEqual(bytes, b.getBytes()));
  }

  @Test
  public void testGetBytes2() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = Buffer.buffer(bytes);

    byte[] sub = new byte[bytes.length / 2];
    System.arraycopy(bytes, bytes.length / 4, sub, 0, bytes.length / 2);
    assertTrue(TestUtils.byteArraysEqual(sub, b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2)));
  }

  @Test
  public void testGetBytesWithByteArray() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = Buffer.buffer(bytes);

    byte[] sub = new byte[bytes.length / 2];
    System.arraycopy(bytes, bytes.length / 4, sub, 0, bytes.length / 2);

    byte[] result = new byte[bytes.length / 2];
    b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2, result);

    assertTrue(TestUtils.byteArraysEqual(sub, result));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testGetBytesWithTooSmallByteArray() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = Buffer.buffer(bytes);
    byte[] result = new byte[bytes.length / 4];
    b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2, result);
  }

  @Test
  public void testGetBytesWithByteArrayFull() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = Buffer.buffer(bytes);

    byte[] sub = new byte[bytes.length];
    System.arraycopy(bytes, bytes.length / 4, sub, 12, bytes.length / 2);

    byte[] result = new byte[bytes.length];
    b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2, result, 12);

    assertTrue(TestUtils.byteArraysEqual(sub, result));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testGetBytesWithBadOffset() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = Buffer.buffer(bytes);
    byte[] result = new byte[bytes.length / 2];
    b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2, result, -1);
  }

  private final int numSets = 100;

  @Test
  public void testSetInt() throws Exception {
    testSetInt(Buffer.buffer(numSets * 4));
  }

  @Test
  public void testSetIntExpandBuffer() throws Exception {
    testSetInt(Buffer.buffer());
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
    testSetUnsignedInt(Buffer.buffer(numSets * 4));
  }

  @Test
  public void testSetUnsignedIntExpandBuffer() throws Exception {
    testSetUnsignedInt(Buffer.buffer());
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
    testSetLong(Buffer.buffer(numSets * 8));
  }

  @Test
  public void testSetLongExpandBuffer() throws Exception {
    testSetLong(Buffer.buffer());
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
    testSetByte(Buffer.buffer(numSets));
  }

  @Test
  public void testSetByteExpandBuffer() throws Exception {
    testSetByte(Buffer.buffer());
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
    testSetUnsignedByte(Buffer.buffer(numSets));
  }

  @Test
  public void testSetUnsignedByteExpandBuffer() throws Exception {
    testSetUnsignedByte(Buffer.buffer());
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
    testSetFloat(Buffer.buffer(numSets * 4));
  }

  @Test
  public void testSetFloatExpandBuffer() throws Exception {
    testSetFloat(Buffer.buffer());
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
    testSetDouble(Buffer.buffer(numSets * 8));
  }

  @Test
  public void testSetDoubleExpandBuffer() throws Exception {
    testSetDouble(Buffer.buffer());
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
    testSetShort(Buffer.buffer(numSets * 2));
  }

  @Test
  public void testSetShortExpandBuffer() throws Exception {
    testSetShort(Buffer.buffer());
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
    testSetUnsignedShort(Buffer.buffer(numSets * 2));
  }

  @Test
  public void testSetUnsignedShortExpandBuffer() throws Exception {
    testSetUnsignedShort(Buffer.buffer());
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
    testSetBytesBuffer(Buffer.buffer(150), Buffer::buffer);
    assertNullPointerException(() -> Buffer.buffer(150).setBytes(0, (ByteBuffer) null));
  }

  @Test
  public void testSetBytesBuffer2() throws Exception {
    testSetBytesBuffer(Buffer.buffer(150), PADDED_BUFFER_FACTORY);
    assertNullPointerException(() -> Buffer.buffer(150).setBytes(0, (ByteBuffer) null));
  }

  private void testSetBytesBuffer(Buffer buff, Function<byte[], Buffer> bufferFactory) throws Exception {
    Buffer b = bufferFactory.apply(TestUtils.randomByteArray(100));
    buff.setBuffer(50, b);
    byte[] b2 = buff.getBytes(50, 150);
    assertEquals(b, Buffer.buffer(b2));

    byte[] b3 = TestUtils.randomByteArray(100);
    buff.setBytes(50, b3);
    byte[] b4 = buff.getBytes(50, 150);
    assertEquals(Buffer.buffer(b3), Buffer.buffer(b4));
  }

  @Test
  public void testSetBytesBufferExpandBuffer() throws Exception {
    testSetShort(Buffer.buffer());
  }

  @Test
  public void testSetBytesWithOffsetAndLen() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    int len = bytesLen - 2;

    Buffer b = Buffer.buffer();
    b.setByte(0, (byte) '0');
    b.setBytes(1, bytes, 1, len);
    assertEquals(b.length(), len + 1);
    byte[] copy = new byte[len];
    System.arraycopy(bytes, 1, copy, 0, len);

    assertTrue(TestUtils.byteArraysEqual(copy, b.getBytes(1, b.length())));

    b.setBytes(b.length(), bytes, 1, len);
    assertEquals(b.length(), 2 * len + 1);

    assertNullPointerException(() -> Buffer.buffer(150).setBytes(0, (byte[]) null));
    assertNullPointerException(() -> Buffer.buffer(150).setBytes(0, null, 1, len));
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

    Buffer b = Buffer.buffer();
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
    testSetBytesString(Buffer.buffer(150));
  }

  @Test
  public void testSetBytesStringExpandBuffer() throws Exception {
    testSetBytesString(Buffer.buffer());
  }

  private void testSetBytesString(Buffer buff) throws Exception {

    String str = TestUtils.randomUnicodeString(100);
    buff.setString(50, str);

    byte[] b1 = buff.getBytes(50, buff.length());
    String str2 = new String(b1, "UTF-8");

    assertEquals(str, str2);

    assertNullPointerException(() -> Buffer.buffer(150).setString(0, null));
    assertNullPointerException(() -> Buffer.buffer(150).setString(0, null, "UTF-8"));

    //TODO setString with encoding
  }

  @Test
  public void testToString() throws Exception {
    String str = TestUtils.randomUnicodeString(100);
    Buffer buff = Buffer.buffer(str);
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
    Buffer buff = Buffer.buffer(1000);
    assertEquals(0, buff.length());

    String str = TestUtils.randomUnicodeString(100);
    buff = Buffer.buffer(str);
    assertEquals(buff.length(), str.getBytes("UTF-8").length);
    assertEquals(str, buff.toString());

    // TODO create with string with encoding

    byte[] bytes = TestUtils.randomByteArray(100);
    buff = Buffer.buffer(bytes);
    assertEquals(buff.length(), bytes.length);
    assertEquals(Buffer.buffer(bytes), Buffer.buffer(buff.getBytes()));
  }

  @Test
  public void testSlice1() throws Exception {
    Buffer buff = TestUtils.randomBuffer(100);
    Buffer sliced = buff.slice();
    assertEquals(buff, sliced);
    long rand = TestUtils.randomLong();
    sliced.setLong(0, rand);
    assertEquals(rand, buff.getLong(0));
    buff.appendString(TestUtils.randomUnicodeString(100));
    assertEquals(100, sliced.length());
  }

  @Test
  public void testSlice2() throws Exception {
    Buffer buff = TestUtils.randomBuffer(100);
    Buffer sliced = buff.slice(10, 20);
    for (int i = 0; i < 10; i++) {
      assertEquals(buff.getByte(10 + i), sliced.getByte(i));
    }
    long rand = TestUtils.randomLong();
    sliced.setLong(0, rand);
    assertEquals(rand, buff.getLong(10));
    buff.appendString(TestUtils.randomUnicodeString(100));
    assertEquals(10, sliced.length());
  }

  @Test
  public void testToJsonObject() throws Exception {
    JsonObject obj = new JsonObject();
    obj.put("wibble", "wibble_value");
    obj.put("foo", 5);
    obj.put("bar", true);
    Buffer buff = Buffer.buffer(obj.encode());
    assertEquals(obj, buff.toJsonObject());

    buff = Buffer.buffer(TestUtils.randomAlphaString(10));
    try {
      buff.toJsonObject();
      fail();
    } catch (DecodeException ignore) {
    }
  }

  @Test
  public void testToJsonArray() throws Exception {
    JsonArray arr = new JsonArray();
    arr.add("wibble");
    arr.add(5);
    arr.add(true);
    Buffer buff = Buffer.buffer(arr.encode());
    assertEquals(arr, buff.toJsonArray());

    buff = Buffer.buffer(TestUtils.randomAlphaString(10));
    try {
      buff.toJsonObject();
      fail();
    } catch (DecodeException ignore) {
    }
  }

  @Test
  public void testLength() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer buffer = Buffer.buffer(bytes);
    assertEquals(100, Buffer.buffer(buffer.getByteBuf()).length());
  }

  @Test
  public void testLength2() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    assertEquals(90, Buffer.buffer(Unpooled.copiedBuffer(bytes).slice(10, 90)).length());
  }

  @Test
  public void testAppendDoesNotModifyByteBufIndex() throws Exception {
    ByteBuf buf = Unpooled.copiedBuffer("foobar".getBytes());
    assertEquals(0, buf.readerIndex());
    assertEquals(6, buf.writerIndex());
    Buffer buffer = Buffer.buffer(buf);
    Buffer other = Buffer.buffer("prefix");
    other.appendBuffer(buffer);
    assertEquals(0, buf.readerIndex());
    assertEquals(6, buf.writerIndex());
    assertEquals(other.toString(), "prefixfoobar");
  }
}
