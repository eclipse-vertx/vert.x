/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.tests.core.buffer;

import org.nodex.java.core.buffer.Buffer;
import org.nodex.tests.Utils;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.Test;

public class BufferTest extends TestBase {


  @Test
  public void testAppendBuff() throws Exception {

    int bytesLen = 100;
    byte[] bytes = Utils.generateRandomByteArray(bytesLen);
    Buffer toAppend = Buffer.create(bytes);

    Buffer b = Buffer.create(0);
    b.appendBuffer(toAppend);
    azzert(b.length() == bytes.length);

    azzert(Utils.byteArraysEqual(bytes, b.getBytes()));
    b.appendBuffer(toAppend);
    azzert(b.length() == 2 * bytes.length);
  }

  @Test
  public void testAppendBytes() throws Exception {

    int bytesLen = 100;
    byte[] bytes = Utils.generateRandomByteArray(bytesLen);

    Buffer b = Buffer.create(0);
    b.appendBytes(bytes);
    azzert(b.length() == bytes.length);
    azzert(Utils.byteArraysEqual(bytes, b.getBytes()));

    b.appendBytes(bytes);
    azzert(b.length() == 2 * bytes.length);
  }

  @Test
  public void testAppendByte() throws Exception {
    int bytesLen = 100;
    byte[] bytes = Utils.generateRandomByteArray(bytesLen);

    Buffer b = Buffer.create(0);
    for (int i = 0; i < bytesLen; i++) {
      b.appendByte(bytes[i]);
    }
    azzert(b.length() == bytes.length);
    azzert(Utils.byteArraysEqual(bytes, b.getBytes()));

    for (int i = 0; i < bytesLen; i++) {
      b.appendByte(bytes[i]);
    }
    azzert(b.length() == 2 * bytes.length);
  }

  @Test
  public void testAppendByte2() throws Exception {
    int bytesLen = 100;
    Buffer b = Buffer.create(Utils.generateRandomByteArray(bytesLen));
    b.setByte(b.length(), (byte) 9);

  }

  @Test
  public void testAppendString1() throws Exception {

    String str = Utils.randomAlphaString(100);
    byte[] sb = str.getBytes("UTF-8");

    Buffer b = Buffer.create(0);
    b.appendString(str);
    azzert(b.length() == sb.length);
    azzert(str.equals(b.toString("UTF-8")));
  }

  @Test
  public void testAppendString2() throws Exception {
    //TODO
  }

  @Test
  public void testGetOutOfBounds() throws Exception {
    int bytesLen = 100;
    byte[] bytes = Utils.generateRandomByteArray(bytesLen);

    Buffer b = Buffer.create(bytes);
    try {
      b.getByte(bytesLen);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getByte(bytesLen + 1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getByte(bytesLen + 100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getByte(-1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getByte(-100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

    try {
      b.getInt(bytesLen);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getInt(bytesLen + 1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getInt(bytesLen + 100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getInt(-1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getInt(-100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

    try {
      b.getLong(bytesLen);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getLong(bytesLen + 1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getLong(bytesLen + 100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getLong(-1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getLong(-100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

    try {
      b.getFloat(bytesLen);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getFloat(bytesLen + 1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getFloat(bytesLen + 100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getFloat(-1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getFloat(-100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

    try {
      b.getDouble(bytesLen);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getDouble(bytesLen + 1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getDouble(bytesLen + 100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getDouble(-1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getDouble(-100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }


    try {
      b.getShort(bytesLen);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getShort(bytesLen + 1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getShort(bytesLen + 100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getShort(-1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getShort(-100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

    try {
      b.getBytes(bytesLen + 1, bytesLen + 1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getBytes(bytesLen + 100, bytesLen + 100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getBytes(-1, -1);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }
    try {
      b.getBytes(-100, -100);
      azzert(false);
    } catch (IndexOutOfBoundsException e) {
      //expected
    }

  }

  @Test
  public void testGetByte() throws Exception {
    int bytesLen = 100;
    byte[] bytes = Utils.generateRandomByteArray(bytesLen);

    Buffer b = Buffer.create(bytes);
    for (int i = 0; i < bytesLen; i++) {
      azzert(bytes[i] == b.getByte(i));
    }
  }

  @Test
  public void testGetInt() throws Exception {
    int numInts = 100;
    Buffer b = Buffer.create(numInts * 4);
    for (int i = 0; i < numInts; i++) {
      b.setInt(i * 4, i);
    }

    for (int i = 0; i < numInts; i++) {
      azzert(i == b.getInt(i * 4));
    }
  }

  @Test
  public void testGetLong() throws Exception {
    int numLongs = 100;
    Buffer b = Buffer.create(numLongs * 8);
    for (int i = 0; i < numLongs; i++) {
      b.setLong(i * 8, i);
    }

    for (int i = 0; i < numLongs; i++) {
      azzert(i == b.getLong(i * 8));
    }
  }

  @Test
  public void testGetFloat() throws Exception {
    int numFloats = 100;
    Buffer b = Buffer.create(numFloats * 4);
    for (int i = 0; i < numFloats; i++) {
      b.setFloat(i * 4, i);
    }

    for (int i = 0; i < numFloats; i++) {
      azzert(i == b.getFloat(i * 4));
    }
  }

  @Test
  public void testGetDouble() throws Exception {
    int numDoubles = 100;
    Buffer b = Buffer.create(numDoubles * 8);
    for (int i = 0; i < numDoubles; i++) {
      b.setDouble(i * 8, i);
    }

    for (int i = 0; i < numDoubles; i++) {
      azzert(i == b.getDouble(i * 8));
    }
  }

  @Test
  public void testGetShort() throws Exception {
    int numShorts = 100;
    Buffer b = Buffer.create(numShorts * 2);
    for (short i = 0; i < numShorts; i++) {
      b.setShort(i * 2, i);
    }

    for (short i = 0; i < numShorts; i++) {
      azzert(i == b.getShort(i * 2));
    }
  }

  @Test
  public void testGetBytes() throws Exception {
    byte[] bytes = Utils.generateRandomByteArray(100);
    Buffer b = Buffer.create(bytes);

    azzert(Utils.byteArraysEqual(bytes, b.getBytes()));
  }

  @Test
  public void testGetBytes2() throws Exception {
    byte[] bytes = Utils.generateRandomByteArray(100);
    Buffer b = Buffer.create(bytes);

    byte[] sub = new byte[bytes.length / 2];
    System.arraycopy(bytes, bytes.length / 4, sub, 0, bytes.length / 2);
    azzert(Utils.byteArraysEqual(sub, b.getBytes(bytes.length / 4, bytes.length / 4 + bytes.length / 2)));
  }

  private final int numSets = 100;

  @Test
  public void testSetInt() throws Exception {
    testSetInt(Buffer.create(numSets * 4));
  }

  @Test
  public void testSetIntExpandBuffer() throws Exception {
    testSetInt(Buffer.create(0));
  }

  private void testSetInt(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setInt(i * 4, i);
    }
    for (int i = 0; i < numSets; i++) {
      azzert(i == buff.getInt(i * 4));
    }
  }

  @Test
  public void testSetLong() throws Exception {
    testSetLong(Buffer.create(numSets * 8));
  }

  @Test
  public void testSetLongExpandBuffer() throws Exception {
    testSetLong(Buffer.create(0));
  }

  private void testSetLong(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setLong(i * 8, i);
    }
    for (int i = 0; i < numSets; i++) {
      azzert(i == buff.getLong(i * 8));
    }
  }

  @Test
  public void testSetByte() throws Exception {
    testSetByte(Buffer.create(numSets));
  }

  @Test
  public void testSetByteExpandBuffer() throws Exception {
    testSetByte(Buffer.create(0));
  }

  private void testSetByte(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setByte(i, (byte) i);
    }
    for (int i = 0; i < numSets; i++) {
      azzert(i == buff.getByte(i));
    }
  }

  @Test
  public void testSetFloat() throws Exception {
    testSetFloat(Buffer.create(numSets * 4));
  }

  @Test
  public void testSetFloatExpandBuffer() throws Exception {
    testSetFloat(Buffer.create(0));
  }

  private void testSetFloat(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setFloat(i * 4, (float) i);
    }
    for (int i = 0; i < numSets; i++) {
      azzert(i == buff.getFloat(i * 4));
    }
  }

  @Test
  public void testSetDouble() throws Exception {
    testSetDouble(Buffer.create(numSets * 8));
  }

  @Test
  public void testSetDoubleExpandBuffer() throws Exception {
    testSetDouble(Buffer.create(0));
  }

  private void testSetDouble(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setDouble(i * 8, (double) i);
    }
    for (int i = 0; i < numSets; i++) {
      azzert(i == buff.getDouble(i * 8));
    }
  }


  @Test
  public void testSetShort() throws Exception {
    testSetShort(Buffer.create(numSets * 2));
  }

  @Test
  public void testSetShortExpandBuffer() throws Exception {
    testSetShort(Buffer.create(0));
  }

  private void testSetShort(Buffer buff) throws Exception {
    for (int i = 0; i < numSets; i++) {
      buff.setShort(i * 2, (short) i);
    }
    for (int i = 0; i < numSets; i++) {
      azzert(i == buff.getShort(i * 2));
    }
  }


  @Test
  public void testSetBytesBuffer() throws Exception {
    testSetBytesBuffer(Buffer.create(150));
  }

  @Test
  public void testSetBytesBufferExpandBuffer() throws Exception {
    testSetShort(Buffer.create(0));
  }

  private void testSetBytesBuffer(Buffer buff) throws Exception {

    Buffer b = Utils.generateRandomBuffer(100);
    buff.setBuffer(50, b);
    byte[] b2 = buff.getBytes(50, 150);
    azzert(Utils.buffersEqual(b, Buffer.create(b2)));

    byte[] b3 = Utils.generateRandomByteArray(100);
    buff.setBytes(50, b3);
    byte[] b4 = buff.getBytes(50, 150);
    azzert(Utils.buffersEqual(Buffer.create(b3), Buffer.create(b4)));
  }


  @Test
  public void testSetBytesString() throws Exception {
    testSetBytesString(Buffer.create(150));
  }

  @Test
  public void testSetBytesStringExpandBuffer() throws Exception {
    testSetBytesString(Buffer.create(0));
  }

  private void testSetBytesString(Buffer buff) throws Exception {

    String str = Utils.randomAlphaString(100);
    buff.setBytes(50, str);

    byte[] b1 = buff.getBytes(50, buff.length());
    String str2 = new String(b1, "UTF-8");

    azzert(str.equals(str2));

    //TODO setString with encoding
  }

  @Test
  public void testToString() throws Exception {
    String str = Utils.randomAlphaString(100);
    Buffer buff = Buffer.create(str);
    azzert(str.equals(buff.toString()));

    //TODO toString with encoding
  }

  @Test
  public void testCopy() throws Exception {
    Buffer buff = Utils.generateRandomBuffer(100);
    azzert(Utils.buffersEqual(buff, buff.copy()));

    Buffer copy = buff.copy(0, buff.length());
    azzert(Utils.buffersEqual(buff, copy));

    //Make sure they don't share underlying buffer
    buff.setInt(0, 1);
    azzert(!Utils.buffersEqual(buff, copy));
  }

  @Test
  public void testCreateBuffers() throws Exception {
    Buffer buff = Buffer.create(1000);
    azzert(buff.length() == 0);

    String str = Utils.randomAlphaString(100);
    buff = Buffer.create(str);
    azzert(buff.length() == str.getBytes("UTF-8").length);
    azzert(str.equals(buff.toString()));

    // TODO create with string with encoding

    byte[] bytes = Utils.generateRandomByteArray(100);
    buff = Buffer.create(bytes);
    azzert(buff.length() == bytes.length);
    azzert(Utils.buffersEqual(Buffer.create(bytes), Buffer.create(buff.getBytes())));

  }

}
