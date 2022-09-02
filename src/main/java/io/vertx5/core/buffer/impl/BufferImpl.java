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

package io.vertx5.core.buffer.impl;

import io.netty.util.CharsetUtil;
import io.vertx5.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 */
public class BufferImpl implements Buffer {

  private BufferOwnershipStrategy strategy;
  private io.netty5.buffer.api.Buffer buffer;

  public BufferImpl(BufferOwnershipStrategy strategy, io.netty5.buffer.api.Buffer buffer) {
    this.strategy = strategy;
    this.buffer = strategy.wrap(buffer);
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

  public byte getByte(int pos) {
    checkUpperBound(pos, 1);
    return buffer.getByte(pos);
  }

  public int getUnsignedByte(int pos) {
    checkUpperBound(pos, 1);
    return buffer.getUnsignedByte(pos);
  }

  public int getInt(int pos) {
    checkUpperBound(pos, 4);
    return buffer.getInt(pos);
  }

  public long getUnsignedInt(int pos) {
    checkUpperBound(pos, 4);
    return buffer.getUnsignedInt(pos);
  }

  public long getLong(int pos) {
    checkUpperBound(pos, 8);
    return buffer.getLong(pos);
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

  public int getUnsignedShort(int pos) {
    checkUpperBound(pos, 2);
    return buffer.getUnsignedShort(pos);
  }

  public int getMedium(int pos) {
    checkUpperBound(pos, 3);
    return buffer.getMedium(pos);
  }

  public int getUnsignedMedium(int pos) {
    checkUpperBound(pos, 3);
    return buffer.getUnsignedMedium(pos);
  }

  private void checkUpperBound(int index, int size) {
    int length = buffer.writerOffset();
    if ((index | length - (index + size)) < 0) {
      throw new IndexOutOfBoundsException(index + " + " + size + " > " + length);
    }
  }

  public byte[] getBytes() {
    byte[] arr = new byte[buffer.writerOffset()];
    buffer.copyInto(buffer.readerOffset(), arr, 0, arr.length);
    return arr;
  }

  public byte[] getBytes(int start, int end) {
    Arguments.require(end >= start, "end must be greater or equal than start");
    byte[] arr = new byte[end - start];
    buffer.copyInto(start, arr, 0, end - start);
    return arr;
  }

  @Override
  public Buffer getBytes(byte[] dst) {
    return getBytes(dst, 0);
  }

  @Override
  public Buffer getBytes(byte[] dst, int dstIndex) {
    return getBytes(0, buffer.writerOffset(), dst, dstIndex);
  }

  @Override
  public Buffer getBytes(int start, int end, byte[] dst) {
    return getBytes(start, end, dst, 0);
  }

  @Override
  public Buffer getBytes(int start, int end, byte[] dst, int dstIndex) {
    Arguments.require(end >= start, "end must be greater or equal than start");
    buffer.copyInto(start, dst, dstIndex, end - start);
    return this;
  }

  public Buffer getBuffer(int start, int end) {
    return strategy.buffer(getBytes(start, end));
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
    io.netty5.buffer.api.Buffer byteBuf = impl.buffer;
    int readerOffset = byteBuf.readerOffset();
    try {
      buffer.writeBytes(impl.buffer);
    } finally {
      byteBuf.readerOffset(readerOffset);
    }
    return this;
  }

  public Buffer appendBuffer(Buffer buff, int offset, int len) {
    BufferImpl impl = (BufferImpl) buff;
    io.netty5.buffer.api.Buffer byteBuf = impl.buffer;
    int readerOffset = byteBuf.readerOffset();
    int writerOffset = byteBuf.writerOffset();
    int from = readerOffset + offset;
    try {
      byteBuf.readerOffset(from);
      byteBuf.writerOffset(from + len);
      buffer.writeBytes(byteBuf);
    } finally {
      byteBuf.readerOffset(readerOffset);
      byteBuf.writerOffset(writerOffset);
    }
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
    buffer.writeUnsignedByte(b);
    return this;
  }

  public Buffer appendInt(int i) {
    buffer.writeInt(i);
    return this;
  }

  public Buffer appendUnsignedInt(long i) {
    buffer.writeUnsignedInt((int) i);
    return this;
  }

  public Buffer appendMedium(int i) {
    buffer.writeMedium(i);
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

  public Buffer appendUnsignedShort(int s) {
    buffer.writeUnsignedShort(s);
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
    buffer.setUnsignedByte(pos, b);
    return this;
  }

  public Buffer setInt(int pos, int i) {
    ensureLength(pos + 4);
    buffer.setInt(pos, i);
    return this;
  }

  public Buffer setUnsignedInt(int pos, long i) {
    ensureLength(pos + 4);
    buffer.setUnsignedInt(pos, i);
    return this;
  }

  public Buffer setMedium(int pos, int i) {
    ensureLength(pos + 3);
    buffer.setMedium(pos, i);
    return this;
  }

  public Buffer setLong(int pos, long l) {
    ensureLength(pos + 8);
    buffer.setLong(pos, l);
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

  public Buffer setUnsignedShort(int pos, int s) {
    ensureLength(pos + 2);
    buffer.setUnsignedShort(pos, s);
    return this;
  }

  public Buffer setBuffer(int pos, Buffer buff) {
    return setBuffer(pos, buff, 0, buff.length());
  }

  public Buffer setBuffer(int pos, Buffer buff, int offset, int len) {
    if (len == -1) {
      throw new IndexOutOfBoundsException();
    }
    if (offset == -1) {
      throw new IndexOutOfBoundsException();
    }
    if (pos < 0) {
      throw new IndexOutOfBoundsException();
    }
    ensureLength(pos + len);
    BufferImpl impl = (BufferImpl) buff;
    io.netty5.buffer.api.Buffer byteBuf = impl.buffer;
    int a = this.buffer.writerOffset();
    int b = byteBuf.readerOffset();
    int c = byteBuf.writerOffset();
    this.buffer.writerOffset(pos);
    byteBuf.readerOffset(b + offset);
    byteBuf.writerOffset(b + offset + len);
    try {
      this.buffer.writeBytes(byteBuf);
    } finally {
      this.buffer.writerOffset(a);
      byteBuf.readerOffset(b);
      byteBuf.writerOffset(c);
    }
    return this;
  }

  public BufferImpl setBytes(int pos, ByteBuffer b) {
    ensureLength(pos + b.limit());
    throw new UnsupportedOperationException();
  }

  public Buffer setBytes(int pos, byte[] b) {
    return setBytes(pos, b, 0, b.length);
  }

  public Buffer setBytes(int pos, byte[] b, int offset, int len) {
    if (pos < 0) {
      throw new IndexOutOfBoundsException();
    }
    if (offset < 0) {
      throw new IndexOutOfBoundsException();
    }
    if (len < 0) {
      throw new IndexOutOfBoundsException();
    }
    ensureLength(pos + len);
    int writerOffset = buffer.writerOffset();
    buffer.writerOffset(pos);
    try {
      buffer.writeBytes(b, offset, len);
    } finally {
      buffer.writerOffset(writerOffset);
    }
    return this;
  }

  public Buffer setString(int pos, String str) {
    return setBytes(pos, str, CharsetUtil.UTF_8);
  }

  public Buffer setString(int pos, String str, String enc) {
    return setBytes(pos, str, Charset.forName(enc));
  }

  public int length() {
    return buffer.readableBytes();
  }

  public Buffer copy() {
    return buffer.readOnly() ? this : new BufferImpl(strategy, buffer.copy());
  }

  /**
   * @return the buffer as is
   */
  public io.netty5.buffer.api.Buffer unwrap() {
    return strategy.unwrap(buffer);
  }

  private Buffer append(String str, Charset charset) {
    byte[] bytes = str.getBytes(charset);
    ensureExpandableBy(bytes.length);
    buffer.writeBytes(bytes);
    return this;
  }

  private Buffer setBytes(int pos, String str, Charset charset) {
    byte[] bytes = str.getBytes(charset);
    return setBytes(pos, bytes, 0, bytes.length);
  }

  /**
   * Ensure buffer length is at least the provided {@code newLength}.
   */
  private void ensureLength(int newLength) {
    int capacity = buffer.capacity();
    int over = newLength - capacity;
    int writerOffset = buffer.writerOffset();
    if (over > 0) {
      buffer.ensureWritable(newLength - writerOffset);
    }
    if (newLength > writerOffset) {
      buffer.writerOffset(newLength);
    }
  }

  /**
   * Make sure that the underlying buffer can be expanded by {@code amount} bytes.
   */
  private void ensureExpandableBy(int amount) {
    buffer.ensureWritable(amount);
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

}
