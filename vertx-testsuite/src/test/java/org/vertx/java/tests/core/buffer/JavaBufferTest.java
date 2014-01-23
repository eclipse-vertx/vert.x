/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.tests.core.buffer;

import junit.framework.TestCase;
import org.junit.Test;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.testframework.TestUtils;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaBufferTest extends TestCase {

  //https://github.com/vert-x/vert.x/issues/561
  @Test
  public void testSetGetInt() throws Exception {
    final int size = 10;
    Buffer buffer = new Buffer(size);
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
    byte[] bytes = TestUtils.generateRandomByteArray(bytesLen);
    Buffer toAppend = new Buffer(bytes);

    Buffer b = new Buffer();
    b.appendBuffer(toAppend);
    assertEquals(b.length(), bytes.length);

    assertTrue(TestUtils.byteArraysEqual(bytes, b.getBytes()));
    b.appendBuffer(toAppend);
    assertEquals(b.length(), 2 * bytes.length);
  }

  @Test
  public void testAppendBytes() throws Exception {

    int bytesLen = 100;
    byte[] bytes = TestUtils.generateRandomByteArray(bytesLen);

    Buffer b = new Buffer();
    b.appendBytes(bytes);
    assertEquals(b.length(), bytes.length);
    assertTrue(TestUtils.byteArraysEqual(bytes, b.getBytes()));

    b.appendBytes(bytes);
    assertEquals(b.length(), 2 * bytes.length);
  }

  @Test
  public void testAppendBytesWithOffsetAndLen() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.generateRandomByteArray(bytesLen);
    int len = bytesLen - 2;

    Buffer b = new Buffer();
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
    byte[] bytes = TestUtils.generateRandomByteArray(bytesLen);
    Buffer src = new Buffer(bytes);
    int len = bytesLen - 2;

    Buffer b = new Buffer();
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
    byte[] bytes = TestUtils.generateRandomByteArray(bytesLen);

    Buffer b = new Buffer();
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
    Buffer b = new Buffer(TestUtils.generateRandomByteArray(bytesLen));
    b.setByte(b.length(), (byte) 9);

  }

  @Test
  public void testAppendString1() throws Exception {

    String str = TestUtils.randomUnicodeString(100);
    byte[] sb = str.getBytes("UTF-8");

    Buffer b = new Buffer();
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
    byte[] bytes = TestUtils.generateRandomByteArray(bytesLen);

    Buffer b = new Buffer(bytes);
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
    byte[] bytes = TestUtils.generateRandomByteArray(bytesLen);

    Buffer b = new Buffer(bytes);
    for (int i = 0; i < bytesLen; i++) {
      assertEquals(bytes[i], b.getByte(i));
    }
  }

  @Test
  public void testGetInt() throws Exception {
    int numInts = 100;
    Buffer b = new Buffer(numInts * 4);
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
    Buffer b = new Buffer(numLongs * 8);
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
    Buffer b = new Buffer(numFloats * 4);
    for (int i = 0; i < numFloats; i++) {
      b.setFloat(i * 4, i);
    }

    for (int i = 0; i < numFloats; i++) {
      assertEquals((float) i, b.getFloat(i * 4));
    }
  }

  @Test
  public void testGetDouble() throws Exception {
    int numDoubles = 100;
    Buffer b = new Buffer(numDoubles * 8);
    for (int i = 0; i < numDoubles; i++) {
      b.setDouble(i * 8, i);
    }

    for (int i = 0; i < numDoubles; i++) {
      assertEquals((double) i, b.getDouble(i * 8));
    }
  }

  @Test
  public void testGetShort() throws Exception {
    int numShorts = 100;
    Buffer b = new Buffer(numShorts * 2);
    for (short i = 0; i < numShorts; i++) {
      b.setShort(i * 2, i);
    }

    for (short i = 0; i < numShorts; i++) {
      assertEquals(i, b.getShort(i * 2));
    }
  }

  @Test
  public void testGetBytes() throws Exception {
    byte[] bytes = TestUtils.generateRandomByteArray(100);
    Buffer b = new Buffer(bytes);

    assertTrue(TestUtils.byteArraysEqual(bytes, b.getBytes()));
  }

  @Test
  public void testGetBytes2() throws Exception {
    byte[] bytes = TestUtils.generateRandomByteArray(100);
    Buffer b = new Buffer(bytes);

    byte[] sub = new byte[bytes.length / 2];
    System.arraycopy(bytes, bytes.length / 4, sub, 0, bytes.length / 2);
    assertTrue(TestUtils.byteArraysEqual(sub, b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2)));
  }

  private final int numSets = 100;

  @Test
  public void testSetInt() throws Exception {
    testSetInt(new Buffer(numSets * 4));
  }

  @Test
  public void testSetIntExpandBuffer() throws Exception {
    testSetInt(new Buffer());
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
    testSetLong(new Buffer(numSets * 8));
  }

  @Test
  public void testSetLongExpandBuffer() throws Exception {
    testSetLong(new Buffer());
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
    testSetByte(new Buffer(numSets));
  }

  @Test
  public void testSetByteExpandBuffer() throws Exception {
    testSetByte(new Buffer());
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
    testSetFloat(new Buffer(numSets * 4));
  }

  @Test
  public void testSetFloatExpandBuffer() throws Exception {
    testSetFloat(new Buffer());
  }

  private void testSetFloat(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setFloat(i * 4, (float) i);
    }
    for (int i = 0; i < numSets; i++) {
      assertEquals((float) i, buff.getFloat(i * 4));
    }
  }

  @Test
  public void testSetDouble() throws Exception {
    testSetDouble(new Buffer(numSets * 8));
  }

  @Test
  public void testSetDoubleExpandBuffer() throws Exception {
    testSetDouble(new Buffer());
  }

  private void testSetDouble(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setDouble(i * 8, (double) i);
    }
    for (int i = 0; i < numSets; i++) {
      assertEquals((double) i, buff.getDouble(i * 8));
    }
  }


  @Test
  public void testSetShort() throws Exception {
    testSetShort(new Buffer(numSets * 2));
  }

  @Test
  public void testSetShortExpandBuffer() throws Exception {
    testSetShort(new Buffer());
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
    testSetBytesBuffer(new Buffer(150));
  }

  @Test
  public void testSetBytesBufferExpandBuffer() throws Exception {
    testSetShort(new Buffer());
  }

  private void testSetBytesBuffer(Buffer buff) throws Exception {

    Buffer b = TestUtils.generateRandomBuffer(100);
    buff.setBuffer(50, b);
    byte[] b2 = buff.getBytes(50, 150);
    assertTrue(TestUtils.buffersEqual(b, new Buffer(b2)));

    byte[] b3 = TestUtils.generateRandomByteArray(100);
    buff.setBytes(50, b3);
    byte[] b4 = buff.getBytes(50, 150);
    assertTrue(TestUtils.buffersEqual(new Buffer(b3), new Buffer(b4)));
  }

  @Test
  public void testSetBytesWithOffsetAndLen() throws Exception {
    int bytesLen = 100;
    byte[] bytes = TestUtils.generateRandomByteArray(bytesLen);
    int len = bytesLen - 2;

    Buffer b = new Buffer();
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
    byte[] bytes = TestUtils.generateRandomByteArray(bytesLen);
    Buffer src = new Buffer(bytes);
    int len = bytesLen - 2;

    Buffer b = new Buffer();
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
    testSetBytesString(new Buffer(150));
  }

  @Test
  public void testSetBytesStringExpandBuffer() throws Exception {
    testSetBytesString(new Buffer());
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
    Buffer buff = new Buffer(str);
    assertEquals(str, buff.toString());

    //TODO toString with encoding
  }

  @Test
  public void testCopy() throws Exception {
    Buffer buff = TestUtils.generateRandomBuffer(100);
    assertTrue(TestUtils.buffersEqual(buff, buff.copy()));

    Buffer copy = buff.getBuffer(0, buff.length());
    assertTrue(TestUtils.buffersEqual(buff, copy));

    //Make sure they don't share underlying buffer
    buff.setInt(0, 1);
    assertTrue(!TestUtils.buffersEqual(buff, copy));
  }

  @Test
  public void testCreateBuffers() throws Exception {
    Buffer buff = new Buffer(1000);
    assertEquals(0, buff.length());

    String str = TestUtils.randomUnicodeString(100);
    buff = new Buffer(str);
    assertEquals(buff.length(), str.getBytes("UTF-8").length);
    assertEquals(str, buff.toString());

    // TODO create with string with encoding

    byte[] bytes = TestUtils.generateRandomByteArray(100);
    buff = new Buffer(bytes);
    assertEquals(buff.length(), bytes.length);
    assertTrue(TestUtils.buffersEqual(new Buffer(bytes), new Buffer(buff.getBytes())));

  }

}
