/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.buffer.Buffer;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BufferTest {

  //https://github.com/vert-x/vert.x/issues/561
  @Test
  public void testSetGetInt() throws Exception {
    final int size = 10;
    Buffer buffer = Buffer.newBuffer(size);
    for (int i = 0; i < size; i++) {
      buffer.setInt(i * 4, (i + 1) * 10);
    }
    for (int i = 0; i < size; i++) {
      assertEquals((i + 1) * 10, buffer.getInt(i * 4));
    }
  }

  @Test
  public void testAppendBuff() throws Exception {

    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    Buffer toAppend = Buffer.newBuffer(bytes);

    Buffer b = Buffer.newBuffer();
    b.appendBuffer(toAppend);
    assertEquals(b.length(), bytes.length);

    assertTrue(TestUtils.byteArraysEqual(bytes, b.getBytes()));
    b.appendBuffer(toAppend);
    assertEquals(b.length(), 2 * bytes.length);
  }

  @Test
  public void testAppendBytes() throws Exception {

    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);

    Buffer b = Buffer.newBuffer();
    b.appendBytes(bytes);
    assertEquals(b.length(), bytes.length);
    assertTrue(TestUtils.byteArraysEqual(bytes, b.getBytes()));

    b.appendBytes(bytes);
    assertEquals(b.length(), 2 * bytes.length);
  }

  @Test
  public void testAppendBytesWithOffsetAndLen() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    int len = bytesLen - 2;

    Buffer b = Buffer.newBuffer();
    b.appendBytes(bytes, 1, len);
    assertEquals(b.length(), len);
    byte[] copy = new byte[len];
    System.arraycopy(bytes, 1, copy, 0, len);
    assertTrue(TestUtils.byteArraysEqual(copy, b.getBytes()));

    b.appendBytes(bytes, 1, len);
    assertEquals(b.length(), 2 * len);
  }

  @Test
  public void testAppendBufferWithOffsetAndLen() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    Buffer src = Buffer.newBuffer(bytes);
    int len = bytesLen - 2;

    Buffer b = Buffer.newBuffer();
    b.appendBuffer(src, 1, len);
    assertEquals(b.length(), len);
    byte[] copy = new byte[len];
    System.arraycopy(bytes, 1, copy, 0, len);
    assertTrue(TestUtils.byteArraysEqual(copy, b.getBytes()));

    b.appendBuffer(src, 1, len);
    assertEquals(b.length(), 2 * len);
  }

  @Test
  public void testAppendByte() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);

    Buffer b = Buffer.newBuffer();
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
    Buffer b = Buffer.newBuffer(TestUtils.randomByteArray(bytesLen));
    b.setByte(b.length(), (byte) 9);

  }

  @Test
  public void testAppendString1() throws Exception {

    String str = TestUtils.randomUnicodeString(100);
    byte[] sb = str.getBytes("UTF-8");

    Buffer b = Buffer.newBuffer();
    b.appendString(str);
    assertEquals(b.length(), sb.length);
    assertTrue(str.equals(b.toString("UTF-8")));
  }

  @Test
  public void testAppendString2() throws Exception {
    //TODO
  }

  @Test
  public void testGetOutOfBounds() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);

    Buffer b = Buffer.newBuffer(bytes);
    try {
      b.getByte(bytesLen);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getByte(bytesLen + 1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getByte(bytesLen + 100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getByte(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getByte(-100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

    try {
      b.getInt(bytesLen);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getInt(bytesLen + 1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getInt(bytesLen + 100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getInt(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getInt(-100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

    try {
      b.getLong(bytesLen);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getLong(bytesLen + 1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getLong(bytesLen + 100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getLong(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getLong(-100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

    try {
      b.getFloat(bytesLen);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getFloat(bytesLen + 1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getFloat(bytesLen + 100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getFloat(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getFloat(-100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

    try {
      b.getDouble(bytesLen);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getDouble(bytesLen + 1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getDouble(bytesLen + 100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getDouble(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getDouble(-100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }


    try {
      b.getShort(bytesLen);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getShort(bytesLen + 1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getShort(bytesLen + 100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getShort(-1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getShort(-100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

    try {
      b.getBytes(bytesLen + 1, bytesLen + 1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getBytes(bytesLen + 100, bytesLen + 100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getBytes(-1, -1);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getBytes(-100, -100);
      fail();
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

  }

  @Test
  public void testGetByte() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);

    Buffer b = Buffer.newBuffer(bytes);
    for (int i = 0; i < bytesLen; i++) {
      assertEquals(bytes[i], b.getByte(i));
    }
  }

  @Test
  public void testGetInt() throws Exception {
    int numInts = 100;
    Buffer b = Buffer.newBuffer(numInts * 4);
    for (int i = 0; i < numInts; i++) {
      b.setInt(i * 4, i);
    }

    for (int i = 0; i < numInts; i++) {
      assertEquals(i, b.getInt(i * 4));
    }
  }

  @Test
  public void testGetLong() throws Exception {
    int numLongs = 100;
    Buffer b = Buffer.newBuffer(numLongs * 8);
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
    Buffer b = Buffer.newBuffer(numFloats * 4);
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
    Buffer b = Buffer.newBuffer(numDoubles * 8);
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
    Buffer b = Buffer.newBuffer(numShorts * 2);
    for (short i = 0; i < numShorts; i++) {
      b.setShort(i * 2, i);
    }

    for (short i = 0; i < numShorts; i++) {
      assertEquals(i, b.getShort(i * 2));
    }
  }

  @Test
  public void testGetString() throws Exception {
    String str = TestUtils.randomAlphaString(100);
    Buffer b = Buffer.newBuffer(str, "UTF-8"); // encode ascii as UTF-8 so one byte per char
    assertEquals(100, b.length());
    String substr = b.getString(10, 20);
    assertEquals(str.substring(10, 20), substr);
    substr = b.getString(10, 20, "UTF-8");
    assertEquals(str.substring(10, 20), substr);
  }

  @Test
  public void testGetBytes() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = Buffer.newBuffer(bytes);

    assertTrue(TestUtils.byteArraysEqual(bytes, b.getBytes()));
  }

  @Test
  public void testGetBytes2() throws Exception {
    byte[] bytes = TestUtils.randomByteArray(100);
    Buffer b = Buffer.newBuffer(bytes);

    byte[] sub = new byte[bytes.length / 2];
    System.arraycopy(bytes, bytes.length / 4, sub, 0, bytes.length / 2);
    assertTrue(TestUtils.byteArraysEqual(sub, b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2)));
  }

  private final int numSets = 100;

  @Test
  public void testSetInt() throws Exception {
    testSetInt(Buffer.newBuffer(numSets * 4));
  }

  @Test
  public void testSetIntExpandBuffer() throws Exception {
    testSetInt(Buffer.newBuffer());
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
  public void testSetLong() throws Exception {
    testSetLong(Buffer.newBuffer(numSets * 8));
  }

  @Test
  public void testSetLongExpandBuffer() throws Exception {
    testSetLong(Buffer.newBuffer());
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
    testSetByte(Buffer.newBuffer(numSets));
  }

  @Test
  public void testSetByteExpandBuffer() throws Exception {
    testSetByte(Buffer.newBuffer());
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
  public void testSetFloat() throws Exception {
    testSetFloat(Buffer.newBuffer(numSets * 4));
  }

  @Test
  public void testSetFloatExpandBuffer() throws Exception {
    testSetFloat(Buffer.newBuffer());
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
    testSetDouble(Buffer.newBuffer(numSets * 8));
  }

  @Test
  public void testSetDoubleExpandBuffer() throws Exception {
    testSetDouble(Buffer.newBuffer());
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
    testSetShort(Buffer.newBuffer(numSets * 2));
  }

  @Test
  public void testSetShortExpandBuffer() throws Exception {
    testSetShort(Buffer.newBuffer());
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
  public void testSetBytesBuffer() throws Exception {
    testSetBytesBuffer(Buffer.newBuffer(150));
  }

  @Test
  public void testSetBytesBufferExpandBuffer() throws Exception {
    testSetShort(Buffer.newBuffer());
  }

  private void testSetBytesBuffer(Buffer buff) throws Exception {

    Buffer b = TestUtils.randomBuffer(100);
    buff.setBuffer(50, b);
    byte[] b2 = buff.getBytes(50, 150);
    assertTrue(TestUtils.buffersEqual(b, Buffer.newBuffer(b2)));

    byte[] b3 = TestUtils.randomByteArray(100);
    buff.setBytes(50, b3);
    byte[] b4 = buff.getBytes(50, 150);
    assertTrue(TestUtils.buffersEqual(Buffer.newBuffer(b3), Buffer.newBuffer(b4)));
  }

  @Test
  public void testSetBytesWithOffsetAndLen() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    int len = bytesLen - 2;

    Buffer b = Buffer.newBuffer();
    b.setByte(0, (byte) '0');
    b.setBytes(1, bytes, 1, len);
    assertEquals(b.length(), len + 1);
    byte[] copy = new byte[len];
    System.arraycopy(bytes, 1, copy, 0, len);

    assertTrue(TestUtils.byteArraysEqual(copy, b.getBytes(1, b.length())));

    b.setBytes(b.length(), bytes, 1, len);
    assertEquals(b.length(), 2 * len + 1);
  }


  @Test
  public void testSetBufferWithOffsetAndLen() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    Buffer src = Buffer.newBuffer(bytes);
    int len = bytesLen - 2;

    Buffer b = Buffer.newBuffer();
    b.setByte(0, (byte) '0');
    b.setBuffer(1, src, 1, len);
    assertEquals(b.length(), len + 1);
    byte[] copy = new byte[len];
    System.arraycopy(bytes, 1, copy, 0, len);

    assertTrue(TestUtils.byteArraysEqual(copy, b.getBytes(1, b.length())));

    b.setBuffer(b.length(), src, 1, len);
    assertEquals(b.length(), 2 * len + 1);
  }

  @Test
  public void testSetBytesString() throws Exception {
    testSetBytesString(Buffer.newBuffer(150));
  }

  @Test
  public void testSetBytesStringExpandBuffer() throws Exception {
    testSetBytesString(Buffer.newBuffer());
  }

  private void testSetBytesString(Buffer buff) throws Exception {

    String str = TestUtils.randomUnicodeString(100);
    buff.setString(50, str);

    byte[] b1 = buff.getBytes(50, buff.length());
    String str2 = new String(b1, "UTF-8");

    assertEquals(str, str2);

    //TODO setString with encoding
  }

  @Test
  public void testToString() throws Exception {
    String str = TestUtils.randomUnicodeString(100);
    Buffer buff = Buffer.newBuffer(str);
    assertEquals(str, buff.toString());

    //TODO toString with encoding
  }

  @Test
  public void testCopy() throws Exception {
    Buffer buff = TestUtils.randomBuffer(100);
    assertTrue(TestUtils.buffersEqual(buff, buff.copy()));

    Buffer copy = buff.getBuffer(0, buff.length());
    assertTrue(TestUtils.buffersEqual(buff, copy));

    //Make sure they don't share underlying buffer
    buff.setInt(0, 1);
    assertFalse(TestUtils.buffersEqual(buff, copy));
  }

  @Test
  public void testCreateBuffers() throws Exception {
    Buffer buff = Buffer.newBuffer(1000);
    assertEquals(0, buff.length());

    String str = TestUtils.randomUnicodeString(100);
    buff = Buffer.newBuffer(str);
    assertEquals(buff.length(), str.getBytes("UTF-8").length);
    assertEquals(str, buff.toString());

    // TODO create with string with encoding

    byte[] bytes = TestUtils.randomByteArray(100);
    buff = Buffer.newBuffer(bytes);
    assertEquals(buff.length(), bytes.length);
    assertTrue(TestUtils.buffersEqual(Buffer.newBuffer(bytes), Buffer.newBuffer(buff.getBytes())));
  }

  @Test
  public void testSlice1() throws Exception {
    Buffer buff = TestUtils.randomBuffer(100);
    Buffer sliced = buff.slice();
    assertTrue(TestUtils.buffersEqual(buff, sliced));
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
}
