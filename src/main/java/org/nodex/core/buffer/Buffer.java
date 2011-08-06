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
import org.jboss.netty.util.CharsetUtil;

import java.nio.charset.Charset;

public class Buffer {

  private ChannelBuffer buffer;

  public static Buffer newFixed(int size) {
    return new Buffer(ChannelBuffers.buffer(size));
  }

  public static Buffer newDynamic(int size) {
    return new Buffer(ChannelBuffers.dynamicBuffer(size));
  }

  public static Buffer fromChannelBuffer(ChannelBuffer buffer) {
    return new Buffer(buffer);
  }

  public static Buffer newWrapped(byte[] bytes) {
    return new Buffer(ChannelBuffers.wrappedBuffer(bytes));
  }

  public static Buffer fromString(String str, String enc) {
    return new Buffer(ChannelBuffers.copiedBuffer(str, Charset.forName(enc)));
  }

  public static Buffer fromString(String str) {
    return Buffer.fromString(str, "UTF-8");
  }

  public Buffer(ChannelBuffer buffer) {
    this.buffer = buffer;
  }

  public String toString() {
    return buffer.toString(Charset.forName("UTF-8"));
  }

  public String toString(String enc) {
    return buffer.toString(Charset.forName(enc));
  }

  public byte byteAt(int pos) {
    return buffer.getByte(pos);
  }

  //Append operations add to the endHandler of the buffer

  public Buffer append(Buffer buff) {
    ChannelBuffer cb = buff._toChannelBuffer();
    buffer.writeBytes(buff._toChannelBuffer());
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
    buffer.setByte(pos, b);
    return this;
  }

  public Buffer setBytes(int pos, Buffer b) {
    buffer.setBytes(pos, b._toChannelBuffer());
    return this;
  }

  public Buffer setBytes(int pos, byte[] b) {
    buffer.setBytes(pos, b);
    return this;
  }

  public Buffer setBytes(int pos, String str) {
    return setBytes(pos, str, CharsetUtil.UTF_8);
  }

  public Buffer setBytes(int pos, String str, String enc) {
    return setBytes(pos, str, Charset.forName(enc));
  }

  public int capacity() {
    return buffer.capacity();
  }

  public int length() {
    return buffer.writerIndex();
  }

  public Buffer slice(int start, int end) {
    return new Buffer(buffer.slice(start, end - start));
  }

  public Buffer copy(int start, int end) {
    return new Buffer(buffer.copy(start, end - start));
  }

  public Buffer duplicate() {
    return new Buffer(buffer.duplicate());
  }

  public byte[] getBytes() {
    byte[] arr = new byte[buffer.writerIndex()];
    buffer.getBytes(0, arr);
    return arr;
  }

  public ChannelBuffer _toChannelBuffer() {
    return buffer;
  }

  private Buffer append(String str, Charset charset) {
    byte[] bytes = str.getBytes(charset);
    buffer.writeBytes(bytes);
    return this;
  }

  private Buffer setBytes(int pos, String str, Charset charset) {
    byte[] bytes = str.getBytes(charset);
    buffer.setBytes(pos, bytes);
    return this;
  }


}
