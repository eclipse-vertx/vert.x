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

package org.nodex.core.buffer;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.DynamicChannelBuffer;
import org.jboss.netty.util.CharsetUtil;

import java.nio.charset.Charset;

public class Buffer {

  //Node.x buffers are always dynamic
  private DynamicChannelBuffer buffer;

  public static Buffer createBuffer(int initialSize) {
    return new Buffer(ChannelBuffers.dynamicBuffer(initialSize));
  }

  public static Buffer createBuffer(byte[] bytes) {
    return new Buffer(ChannelBuffers.wrappedBuffer(bytes));
  }

  public static Buffer createBuffer(String str, String enc) {
    return new Buffer(ChannelBuffers.copiedBuffer(str, Charset.forName(enc)));
  }

  public static Buffer createBuffer(String str) {
    return Buffer.createBuffer(str, "UTF-8");
  }

  // For internal use only
  public Buffer(ChannelBuffer buffer) {
    if (buffer instanceof DynamicChannelBuffer) {
      this.buffer = (DynamicChannelBuffer)buffer;
    } else {
      //TODO - if Netty could provide a DynamicChannelBuffer constructor which took a HeapBuffer this would
      //save an extra copy
      this.buffer = (DynamicChannelBuffer)ChannelBuffers.dynamicBuffer(buffer.readableBytes());
      this.buffer.writeBytes(buffer, 0, buffer.readableBytes());
    }
  }

  public String toString() {
    return buffer.toString(Charset.forName("UTF-8"));
  }

  public String toString(String enc) {
    return buffer.toString(Charset.forName(enc));
  }

  public byte getByte(int pos) {
    return buffer.getByte(pos);
  }

  public int getInt(int pos) {
    return buffer.getInt(pos);
  }

  public long getLong(int pos) {
    return buffer.getLong(pos);
  }

  public double getDouble(int pos) {
    return buffer.getDouble(pos);
  }

  public float getFloat(int pos) {
    return buffer.getFloat(pos);
  }

  public short getShort(int pos) {
    return buffer.getShort(pos);
  }

  public byte[] getBytes() {
    byte[] arr = new byte[buffer.writerIndex()];
    buffer.getBytes(0, arr);
    return arr;
  }

  public byte[] getBytes(int start, int end) {
    byte[] arr = new byte[end - start];
    buffer.getBytes(start, arr, 0, end - start);
    return arr;
  }

  //Append operations add to the endHandler of the buffer

  public Buffer append(Buffer buff) {
    ChannelBuffer cb = buff._getChannelBuffer();
    buffer.writeBytes(buff._getChannelBuffer());
    cb.readerIndex(0); // Need to reset readerindex since Netty write modifies readerIndex of source!
    return this;
  }

  public Buffer append(byte[] bytes) {
    buffer.writeBytes(bytes);
    return this;
  }

  public Buffer append(byte b) {
    buffer.writeByte(b);
    return this;
  }

  public Buffer append(String str, String enc) {
    return append(str, Charset.forName(enc));
  }

  public Buffer append(String str) {
    return append(str, CharsetUtil.UTF_8);
  }

  // set operations write into the buffer at position pos

  public Buffer setByte(int pos, byte b) {
    ensureWritable(pos, 1);
    buffer.setByte(pos, b);
    return this;
  }

  public Buffer setInt(int pos, int i) {
    ensureWritable(pos, 4);
    buffer.setInt(pos, i);
    return this;
  }

  public Buffer setLong(int pos, long l) {
    ensureWritable(pos, 8);
    buffer.setLong(pos, l);
    return this;
  }

  public Buffer setDouble(int pos, double d) {
    ensureWritable(pos, 8);
    buffer.setDouble(pos, d);
    return this;
  }

  public Buffer setFloat(int pos, float f) {
    ensureWritable(pos, 4);
    buffer.setFloat(pos, f);
    return this;
  }

  public Buffer setShort(int pos, short s) {
    ensureWritable(pos, 2);
    buffer.setShort(pos, s);
    return this;
  }

  public Buffer setBytes(int pos, Buffer b) {
    ensureWritable(pos, b.length());
    buffer.setBytes(pos, b._getChannelBuffer());
    return this;
  }

  public Buffer setBytes(int pos, byte[] b) {
    ensureWritable(pos, b.length);
    buffer.setBytes(pos, b);
    return this;
  }

  public Buffer setBytes(int pos, String str) {
    return setBytes(pos, str, CharsetUtil.UTF_8);
  }

  public Buffer setBytes(int pos, String str, String enc) {
    return setBytes(pos, str, Charset.forName(enc));
  }

  public int length() {
    return buffer.writerIndex();
  }

  public Buffer copy(int start, int end) {
    return new Buffer(buffer.copy(start, end - start));
  }

  public Buffer copy() {
    return new Buffer(buffer.copy());
  }

  public ChannelBuffer _getChannelBuffer() {
    return buffer;
  }

  private Buffer append(String str, Charset charset) {
    byte[] bytes = str.getBytes(charset);
    buffer.writeBytes(bytes);
    return this;
  }

  private Buffer setBytes(int pos, String str, Charset charset) {
    byte[] bytes = str.getBytes(charset);
    ensureWritable(pos, bytes.length);
    buffer.setBytes(pos, bytes);
    return this;
  }

  //TODO this is all a bit of a pain - if we can just throw exceptions if people set stuff outside of the buffer
  //like Netty that would be preferable
  private void ensureWritable(int pos, int len) {
    int ni = pos + len;
    int over = ni - buffer.capacity();
    if (over > 0) {
      buffer.ensureWritableBytes(over);
    }
    //We have to make sure that the writerindex is always positioned on the last bit of data set in the buffer
    if (ni > buffer.writerIndex()) {
      buffer.writerIndex(ni);
    }
  }

}
