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
package io.vertx.core.buffer.impl;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BufferImpl implements Buffer {

  private ByteBuf buffer;

  BufferImpl() {
    this(0);
  }

  BufferImpl(int initialSizeHint) {
    buffer = Unpooled.unreleasableBuffer(Unpooled.buffer(initialSizeHint, Integer.MAX_VALUE));
  }

  BufferImpl(byte[] bytes) {
    buffer = Unpooled.unreleasableBuffer(Unpooled.buffer(bytes.length, Integer.MAX_VALUE)).writeBytes(bytes);
  }

  BufferImpl(String str, String enc) {
    this(str.getBytes(Charset.forName(Objects.requireNonNull(enc))));
  }

  BufferImpl(String str, Charset cs) {
    this(str.getBytes(cs));
  }

  BufferImpl(String str) {
    this(str, StandardCharsets.UTF_8);
  }

  BufferImpl(ByteBuf buffer) {
    this.buffer = Unpooled.unreleasableBuffer(buffer);
  }

  public String toString() {
    return buffer.toString(StandardCharsets.UTF_8);
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
    Arguments.require(end >= start, "end must be greater or equal than start");
    byte[] arr = new byte[end - start];
    buffer.getBytes(start, arr, 0, end - start);
    return arr;
  }

  public Buffer getBuffer(int start, int end) {
    return new BufferImpl(getBytes(start, end));
  }

  public String getString(int start, int end, String enc) {
    byte[] bytes = getBytes(start, end);
    Charset cs = Charset.forName(enc);
    return new String(bytes, cs);
  }

  public String getString(int start, int end) {
    byte[] bytes = getBytes(start, end);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  public Buffer appendBuffer(Buffer buff) {
    ByteBuf cb = buff.getByteBuf();
    buffer.writeBytes(buff.getByteBuf());
    cb.readerIndex(0); // Need to reset readerindex since Netty write modifies readerIndex of source!
    return this;
  }

  public Buffer appendBuffer(Buffer buff, int offset, int len) {
    buffer.writeBytes(buff.getByteBuf(), offset, len);
    return this;
  }

  public Buffer appendBytes(byte[] bytes) {
    buffer.writeBytes(bytes);
    return this;
  }

  public Buffer appendBytes(byte[] bytes, int offset, int len) {
    buffer.writeBytes(bytes, offset, len);
    return this;
  }

  public Buffer appendByte(byte b) {
    buffer.writeByte(b);
    return this;
  }

  public Buffer appendInt(int i) {
    buffer.writeInt(i);
    return this;
  }

  public Buffer appendLong(long l) {
    buffer.writeLong(l);
    return this;
  }

  public Buffer appendShort(short s) {
    buffer.writeShort(s);
    return this;
  }

  public Buffer appendFloat(float f) {
    buffer.writeFloat(f);
    return this;
  }

  public Buffer appendDouble(double d) {
    buffer.writeDouble(d);
    return this;
  }

  public Buffer appendString(String str, String enc) {
    return append(str, Charset.forName(Objects.requireNonNull(enc)));
  }

  public Buffer appendString(String str) {
    return append(str, CharsetUtil.UTF_8);
  }

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

  public Buffer setBuffer(int pos, Buffer b) {
    ensureWritable(pos, b.length());
    buffer.setBytes(pos, b.getByteBuf());
    return this;
  }

  public Buffer setBuffer(int pos, Buffer b, int offset, int len) {
    ensureWritable(pos, len);
    buffer.setBytes(pos, b.getByteBuf(), offset, len);
    return this;
  }

  public BufferImpl setBytes(int pos, ByteBuffer b) {
    ensureWritable(pos, b.limit());
    buffer.setBytes(pos, b);
    return this;
  }

  public Buffer setBytes(int pos, byte[] b) {
    ensureWritable(pos, b.length);
    buffer.setBytes(pos, b);
    return this;
  }

  public Buffer setBytes(int pos, byte[] b, int offset, int len) {
    ensureWritable(pos, len);
    buffer.setBytes(pos, b, offset, len);
    return this;
  }

  public Buffer setString(int pos, String str) {
    return setBytes(pos, str, CharsetUtil.UTF_8);
  }

  public Buffer setString(int pos, String str, String enc) {
    return setBytes(pos, str, Charset.forName(enc));
  }

  public int length() {
    return buffer.writerIndex();
  }

  public Buffer copy() {
    return new BufferImpl(buffer.copy());
  }

  public Buffer slice() {
    return new BufferImpl(buffer.slice());
  }

  public Buffer slice(int start, int end) {
    return new BufferImpl(buffer.slice(start, end - start));
  }

  public ByteBuf getByteBuf() {
    // Return a duplicate so the Buffer can be written multiple times.
    // See #648
    return buffer.duplicate();
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

  private void ensureWritable(int pos, int len) {
    int ni = pos + len;
    int cap = buffer.capacity();
    int over = ni - cap;
    if (over > 0) {
      buffer.writerIndex(cap);
      buffer.ensureWritable(over);
    }
    //We have to make sure that the writerindex is always positioned on the last bit of data set in the buffer
    if (ni > buffer.writerIndex()) {
      buffer.writerIndex(ni);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BufferImpl buffer1 = (BufferImpl) o;
    if (buffer != null ? !buffer.equals(buffer1.buffer) : buffer1.buffer != null) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return buffer != null ? buffer.hashCode() : 0;
  }

  @Override
  public void writeToBuffer(Buffer buff) {
    buff.appendInt(this.length());
    buff.appendBuffer(this);
  }

  @Override
  public int readFromBuffer(int pos, Buffer buffer) {
    int len = buffer.getInt(pos);
    Buffer b = buffer.getBuffer(pos + 4, pos + 4 + len);
    this.buffer = b.getByteBuf();
    return pos + 4 + len;
  }
}
