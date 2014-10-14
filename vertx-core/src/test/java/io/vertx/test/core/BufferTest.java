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

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import org.junit.Test;

import java.nio.ByteBuffer;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;
import static io.vertx.test.core.TestUtils.assertIndexOutOfBoundsException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BufferTest {

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

    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    Buffer toAppend = Buffer.buffer(bytes);

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
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    Buffer src = Buffer.buffer(bytes);
    int len = bytesLen - 2;

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
  public void testAppendString1() throws Exception {

    String str = TestUtils.randomUnicodeString(100);
    byte[] sb = str.getBytes("UTF-8");

    Buffer b = Buffer.buffer();
    b.appendString(str);
    assertEquals(b.length(), sb.length);
    assertTrue(str.equals(b.toString("UTF-8")));

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
    assertIllegalArgumentException(() -> b.setBuffer(0, b, 0, -1));
    assertIndexOutOfBoundsException(() -> b.setBytes(-1, TestUtils.randomByteArray(1)));
    assertIndexOutOfBoundsException(() -> b.setBytes(-1, TestUtils.randomByteArray(1), -1, 0));
    assertIllegalArgumentException(() -> b.setBytes(-1, TestUtils.randomByteArray(1), 0, -1));
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
  public void testGetInt() throws Exception {
    int numInts = 100;
    Buffer b = Buffer.buffer(numInts * 4);
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
    Buffer b = Buffer.buffer(numLongs * 8);
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

  @Test
  public void testGetShort() throws Exception {
    int numShorts = 100;
    Buffer b = Buffer.buffer(numShorts * 2);
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
  public void testSetBytesBuffer() throws Exception {
    testSetBytesBuffer(Buffer.buffer(150));
    assertNullPointerException(() -> Buffer.buffer(150).setBytes(0, (ByteBuffer) null));
  }

  @Test
  public void testSetBytesBufferExpandBuffer() throws Exception {
    testSetShort(Buffer.buffer());
  }

  private void testSetBytesBuffer(Buffer buff) throws Exception {

    Buffer b = TestUtils.randomBuffer(100);
    buff.setBuffer(50, b);
    byte[] b2 = buff.getBytes(50, 150);
    assertEquals(b, Buffer.buffer(b2));

    byte[] b3 = TestUtils.randomByteArray(100);
    buff.setBytes(50, b3);
    byte[] b4 = buff.getBytes(50, 150);
    assertEquals(Buffer.buffer(b3), Buffer.buffer(b4));
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
    int bytesLen = 100;
    byte[] bytes = TestUtils.randomByteArray(bytesLen);
    Buffer src = Buffer.buffer(bytes);
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
}
