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

package io.vertx.core.buffer.impl;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * This buffer implementation ignores the wrapped {@code ByteBuf} reader index, assuming that
 * the first logical byte of this buffer maps to the first logical byte of the wrapped buffer.
 *
 * If a {@code ByteBuf} is wrapped and requires a reader index, then the buffer should be
 * sliced or copied at the given index.
 *
 * This buffer implementation maps its length to the wrapped {@code ByteBuf} writer index.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BufferImpl implements Buffer {

  public static Buffer buffer(int initialSizeHint) {
    return new BufferImpl(initialSizeHint);
  }

  public static Buffer buffer() {
    return new BufferImpl();
  }

  public static Buffer buffer(String str) {
    return new BufferImpl(str);
  }

  public static Buffer buffer(String str, String enc) {
    return new BufferImpl(str, enc);
  }

  public static Buffer buffer(byte[] bytes) {
    return new BufferImpl(bytes);
  }

  public static Buffer buffer(ByteBuf byteBuffer) {
    return new BufferImpl(byteBuffer);
  }

  private ByteBuf buffer;

  public BufferImpl() {
    this(0);
  }

  BufferImpl(int initialSizeHint) {
    buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(initialSizeHint, Integer.MAX_VALUE);
  }

  BufferImpl(byte[] bytes) {
    buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(bytes.length, Integer.MAX_VALUE).writeBytes(bytes);
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
    this.buffer = buffer;
  }

  public String toString() {
    return buffer.toString(StandardCharsets.UTF_8);
  }

  public String toString(String enc) {
    return buffer.toString(Charset.forName(enc));
  }

  public String toString(Charset enc) {
    return buffer.toString(enc);
  }


  @Override
  public JsonObject toJsonObject() {
    return new JsonObject(this);
  }

  @Override
  public JsonArray toJsonArray() {
    return new JsonArray(this);
  }

  public byte getByte(int pos) {
    checkUpperBound(pos, 1);
    return buffer.getByte(pos);
  }

  public short getUnsignedByte(int pos) {
    checkUpperBound(pos, 1);
    return buffer.getUnsignedByte(pos);
  }

  public int getInt(int pos) {
    checkUpperBound(pos, 4);
    return buffer.getInt(pos);
  }

  public int getIntLE(int pos) {
    checkUpperBound(pos, 4);
    return buffer.getIntLE(pos);
  }

  public long getUnsignedInt(int pos) {
    checkUpperBound(pos, 4);
    return buffer.getUnsignedInt(pos);
  }

  public long getUnsignedIntLE(int pos) {
    checkUpperBound(pos, 4);
    return buffer.getUnsignedIntLE(pos);
  }

  public long getLong(int pos) {
    checkUpperBound(pos, 8);
    return buffer.getLong(pos);
  }

  public long getLongLE(int pos) {
    checkUpperBound(pos, 8);
    return buffer.getLongLE(pos);
  }

  public double getDouble(int pos) {
    checkUpperBound(pos, 8);
    return buffer.getDouble(pos);
  }

  public float getFloat(int pos) {
    checkUpperBound(pos, 4);
    return buffer.getFloat(pos);
  }

  public short getShort(int pos) {
    checkUpperBound(pos, 2);
    return buffer.getShort(pos);
  }

  public short getShortLE(int pos) {
    checkUpperBound(pos, 2);
    return buffer.getShortLE(pos);
  }

  public int getUnsignedShort(int pos) {
    checkUpperBound(pos, 2);
    return buffer.getUnsignedShort(pos);
  }

  public int getUnsignedShortLE(int pos) {
    checkUpperBound(pos, 2);
    return buffer.getUnsignedShortLE(pos);
  }

  public int getMedium(int pos) {
    checkUpperBound(pos, 3);
    return buffer.getMedium(pos);
  }

  public int getMediumLE(int pos) {
    checkUpperBound(pos, 3);
    return buffer.getMediumLE(pos);
  }

  public int getUnsignedMedium(int pos) {
    checkUpperBound(pos, 3);
    return buffer.getUnsignedMedium(pos);
  }

  public int getUnsignedMediumLE(int pos) {
    checkUpperBound(pos, 3);
    return buffer.getUnsignedMediumLE(pos);
  }

  private void checkUpperBound(int index, int size) {
    int length = buffer.writerIndex();
    if ((index | length - (index + size)) < 0) {
      throw new IndexOutOfBoundsException(index + " + " + size + " > " + length);
    }
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

  @Override
  public Buffer getBytes(byte[] dst) {
    return getBytes(dst, 0);
  }

  @Override
  public Buffer getBytes(byte[] dst, int dstIndex) {
    return getBytes(0, buffer.writerIndex(), dst, dstIndex);
  }

  @Override
  public Buffer getBytes(int start, int end, byte[] dst) {
    return getBytes(start, end, dst, 0);
  }

  @Override
  public Buffer getBytes(int start, int end, byte[] dst, int dstIndex) {
    Arguments.require(end >= start, "end must be greater or equal than start");
    buffer.getBytes(start, dst, dstIndex, end - start);
    return this;
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
    BufferImpl impl = (BufferImpl) buff;
    ByteBuf byteBuf = impl.buffer;
    buffer.writeBytes(impl.buffer, byteBuf.readerIndex(), impl.buffer.readableBytes());
    return this;
  }

  public Buffer appendBuffer(Buffer buff, int offset, int len) {
    BufferImpl impl = (BufferImpl) buff;
    ByteBuf byteBuf = impl.buffer;
    int from = byteBuf.readerIndex() + offset;
    buffer.writeBytes(byteBuf, from, len);
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

  public Buffer appendUnsignedByte(short b) {
    buffer.writeByte(b);
    return this;
  }

  public Buffer appendInt(int i) {
    buffer.writeInt(i);
    return this;
  }

  public Buffer appendIntLE(int i) {
    buffer.writeIntLE(i);
    return this;
  }

  public Buffer appendUnsignedInt(long i) {
    buffer.writeInt((int) i);
    return this;
  }

  public Buffer appendUnsignedIntLE(long i) {
    buffer.writeIntLE((int) i);
    return this;
  }

  public Buffer appendMedium(int i) {
    buffer.writeMedium(i);
    return this;
  }

  public Buffer appendMediumLE(int i) {
    buffer.writeMediumLE(i);
    return this;
  }

  public Buffer appendLong(long l) {
    buffer.writeLong(l);
    return this;
  }

  public Buffer appendLongLE(long l) {
    buffer.writeLongLE(l);
    return this;
  }

  public Buffer appendShort(short s) {
    buffer.writeShort(s);
    return this;
  }

  public Buffer appendShortLE(short s) {
    buffer.writeShortLE(s);
    return this;
  }

  public Buffer appendUnsignedShort(int s) {
    buffer.writeShort(s);
    return this;
  }

  public Buffer appendUnsignedShortLE(int s) {
    buffer.writeShortLE(s);
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
    ensureLength(pos + 1);
    buffer.setByte(pos, b);
    return this;
  }

  public Buffer setUnsignedByte(int pos, short b) {
    ensureLength(pos + 1);
    buffer.setByte(pos, b);
    return this;
  }

  public Buffer setInt(int pos, int i) {
    ensureLength(pos + 4);
    buffer.setInt(pos, i);
    return this;
  }

  public Buffer setIntLE(int pos, int i) {
    ensureLength(pos + 4);
    buffer.setIntLE(pos, i);
    return this;
  }

  public Buffer setUnsignedInt(int pos, long i) {
    ensureLength(pos + 4);
    buffer.setInt(pos, (int) i);
    return this;
  }

  public Buffer setUnsignedIntLE(int pos, long i) {
    ensureLength(pos + 4);
    buffer.setIntLE(pos, (int) i);
    return this;
  }

  public Buffer setMedium(int pos, int i) {
    ensureLength(pos + 3);
    buffer.setMedium(pos, i);
    return this;
  }

  public Buffer setMediumLE(int pos, int i) {
    ensureLength(pos + 3);
    buffer.setMediumLE(pos, i);
    return this;
  }

  public Buffer setLong(int pos, long l) {
    ensureLength(pos + 8);
    buffer.setLong(pos, l);
    return this;
  }

  public Buffer setLongLE(int pos, long l) {
    ensureLength(pos + 8);
    buffer.setLongLE(pos, l);
    return this;
  }

  public Buffer setDouble(int pos, double d) {
    ensureLength(pos + 8);
    buffer.setDouble(pos, d);
    return this;
  }

  public Buffer setFloat(int pos, float f) {
    ensureLength(pos + 4);
    buffer.setFloat(pos, f);
    return this;
  }

  public Buffer setShort(int pos, short s) {
    ensureLength(pos + 2);
    buffer.setShort(pos, s);
    return this;
  }

  public Buffer setShortLE(int pos, short s) {
    ensureLength(pos + 2);
    buffer.setShortLE(pos, s);
    return this;
  }

  public Buffer setUnsignedShort(int pos, int s) {
    ensureLength(pos + 2);
    buffer.setShort(pos, s);
    return this;
  }

  public Buffer setUnsignedShortLE(int pos, int s) {
    ensureLength(pos + 2);
    buffer.setShortLE(pos, s);
    return this;
  }

  public Buffer setBuffer(int pos, Buffer buff) {
    ensureLength(pos + buff.length());
    BufferImpl impl = (BufferImpl) buff;
    ByteBuf byteBuf = impl.buffer;
    buffer.setBytes(pos, byteBuf, byteBuf.readerIndex(), byteBuf.readableBytes());
    return this;
  }

  public Buffer setBuffer(int pos, Buffer buffer, int offset, int len) {
    ensureLength(pos + len);
    BufferImpl impl = (BufferImpl) buffer;
    ByteBuf byteBuf = impl.buffer;
    this.buffer.setBytes(pos, byteBuf, byteBuf.readerIndex() + offset, len);
    return this;
  }

  public BufferImpl setBytes(int pos, ByteBuffer b) {
    ensureLength(pos + b.limit());
    buffer.setBytes(pos, b);
    return this;
  }

  public Buffer setBytes(int pos, byte[] b) {
    ensureLength(pos + b.length);
    buffer.setBytes(pos, b);
    return this;
  }

  public Buffer setBytes(int pos, byte[] b, int offset, int len) {
    ensureLength(pos + len);
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

  /**
   * @return the buffer as is
   */
  public ByteBuf byteBuf() {
    return buffer;
  }

  public ByteBuf getByteBuf() {
    ByteBuf duplicate = buffer.duplicate();
    if (buffer.getClass() != VertxHeapByteBuf.class && buffer.getClass() != VertxUnsafeHeapByteBuf.class) {
      duplicate = Unpooled.unreleasableBuffer(duplicate);
    }
    return duplicate;
  }

  private Buffer append(String str, Charset charset) {
    byte[] bytes = str.getBytes(charset);
    ensureExpandableBy(bytes.length);
    buffer.writeBytes(bytes);
    return this;
  }

  private Buffer setBytes(int pos, String str, Charset charset) {
    byte[] bytes = str.getBytes(charset);
    ensureLength(pos + bytes.length);
    buffer.setBytes(pos, bytes);
    return this;
  }

  /**
   * Ensure buffer length is at least the provided {@code newLength}.
   */
  private void ensureLength(int newLength) {
    int capacity = buffer.capacity();
    int over = newLength - capacity;
    int writerIndex = buffer.writerIndex();
    // Resize the buffer
    if (over > 0) {
      // Expand if we reach max capacity
      int maxCapacity = buffer.maxCapacity();
      if (capacity + over > maxCapacity) {
        setFullMaxCapacity(capacity + over);
      }
      // Allocate extra space
      buffer.ensureWritable(newLength - writerIndex);
    }
    // Set new length
    if (newLength > writerIndex) {
      buffer.writerIndex(newLength);
    }
  }

  /**
   * Make sure that the underlying buffer can be expanded by {@code amount} bytes.
   */
  private void ensureExpandableBy(int amount) {
    int minMaxCapa = buffer.writerIndex() + amount;
    if (minMaxCapa > buffer.maxCapacity()) {
      setFullMaxCapacity(minMaxCapa);
    }
  }

  private void setFullMaxCapacity(int capacity) {
    ByteBuf tmp = buffer.alloc().heapBuffer(capacity, Integer.MAX_VALUE);
    tmp.writeBytes(buffer);
    buffer = tmp;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BufferImpl buffer1 = (BufferImpl) o;
    return Objects.equals(buffer, buffer1.buffer);
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
    BufferImpl impl = (BufferImpl)buffer.getBuffer(pos + 4, pos + 4 + len);
    this.buffer = impl.getByteBuf();
    return pos + 4 + len;
  }
}
