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
package org.vertx.java.core.buffer;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * A Buffer represents a sequence of zero or more bytes that can be written to or read from, and which expands as
 * necessary to accommodate any bytes written to it.<p>
 * There are two ways to write data to a Buffer: The first method involves methods that take the form {@code setXXX}.
 * These methods write data into the buffer starting at the specified position. The position does not have to be inside data that
 * has already been written to the buffer; the buffer will automatically expand to encompass the position plus any data that needs
 * to be written. All positions are measured in bytes and start with zero.<p>
 * The second method involves methods that take the form {@code appendXXX}; these methods append data
 * at the end of the buffer.<p>
 * Methods exist to both {@code set} and {@code append} all primitive types, {@link java.lang.String}, {@link java.nio.ByteBuffer} and
 * other instances of Buffer.<p>
 * Data can be read from a buffer by invoking methods which take the form {@code getXXX}. These methods take a parameter
 * representing the position in the Buffer from where to read data.<p>
 * Once a buffer has been written to a socket or other write stream, the same buffer instance can't be written again to another WriteStream.<p>
 * Instances of this class are not thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Buffer {

  private final ByteBuf buffer;

  /**
   * Create an empty buffer
   */
  public Buffer() {
    this(0);
  }

  /**
   * Creates a new empty Buffer that is expected to have a size of {@code initialSizeHint} after data has been
   * written to it.<p>
   * Please note that {@code length} of the Buffer immediately after creation will be zero.<p>
   * The {@code initialSizeHint} is merely a hint to the system for how much memory to initially allocate to the buffer to prevent excessive
   * automatic re-allocations as data is written to it.
   */
  public Buffer(int initialSizeHint) {
    buffer = Unpooled.unreleasableBuffer(Unpooled.buffer(initialSizeHint, Integer.MAX_VALUE));
  }

  /**
   * Create a new Buffer that contains the contents of a {@code byte[]}
   */
  public Buffer(byte[] bytes) {
    buffer = Unpooled.unreleasableBuffer(Unpooled.buffer(bytes.length, Integer.MAX_VALUE)).writeBytes(bytes);
  }

  /**
   * Create a new Buffer that contains the contents of a {@code String str} encoded according to the encoding {@code enc}
   */
  public Buffer(String str, String enc) {
    this(str.getBytes(Charset.forName(enc)));
  }

  /**
   * Create a new Buffer that contains the contents of {@code String str} encoded with UTF-8 encoding
   */
  public Buffer(String str) {
    this(str, "UTF-8");
  }

  /**
   * Create a new Buffer from a Netty {@code ByteBuf} instance.
   * This method is meant for internal use only.
   */
  public Buffer(ByteBuf buffer) {
    this.buffer = Unpooled.unreleasableBuffer(buffer);
  }

  /**
   * Returns a {@code String} representation of the Buffer assuming it contains a {@code String} encoding in UTF-8
   */
  public String toString() {
    return buffer.toString(Charset.forName("UTF-8"));
  }

  /**
   * Returns a {@code String} representation of the Buffer with the encoding specified by {@code enc}
   */
  public String toString(String enc) {
    return buffer.toString(Charset.forName(enc));
  }

  /**
   * Returns the {@code byte} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 1} is greater than the length of the Buffer.
   */
  public byte getByte(int pos) {
    return buffer.getByte(pos);
  }

  /**
   * Returns the {@code int} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 4} is greater than the length of the Buffer.
   */
  public int getInt(int pos) {
    return buffer.getInt(pos);
  }

  /**
   * Returns the {@code long} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 8} is greater than the length of the Buffer.
   */
  public long getLong(int pos) {
    return buffer.getLong(pos);
  }

  /**
   * Returns the {@code double} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 8} is greater than the length of the Buffer.
   */
  public double getDouble(int pos) {
    return buffer.getDouble(pos);
  }

  /**
   * Returns the {@code float} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 4} is greater than the length of the Buffer.
   */
  public float getFloat(int pos) {
    return buffer.getFloat(pos);
  }

  /**
   * Returns the {@code short} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 2} is greater than the length of the Buffer.
   */
  public short getShort(int pos) {
    return buffer.getShort(pos);
  }

  /**
   * Returns a copy of the entire Buffer as a {@code byte[]}
   */
  public byte[] getBytes() {
    byte[] arr = new byte[buffer.writerIndex()];
    buffer.getBytes(0, arr);
    return arr;
  }

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@code byte[]} starting at position {@code start}
   * and ending at position {@code end - 1}
   */
  public byte[] getBytes(int start, int end) {
    byte[] arr = new byte[end - start];
    buffer.getBytes(start, arr, 0, end - start);
    return arr;
  }

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@link Buffer} starting at position {@code start}
   * and ending at position {@code end - 1}
   */
  public Buffer getBuffer(int start, int end) {
    return new Buffer(getBytes(start, end));
  }

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@code String} starting at position {@code start}
   * and ending at position {@code end - 1} interpreted as a String in the specified encoding
   */
  public String getString(int start, int end, String enc) {
    byte[] bytes = getBytes(start, end);
    Charset cs = Charset.forName(enc);
    return new String(bytes, cs);
  }

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@code String} starting at position {@code start}
   * and ending at position {@code end - 1} interpreted as a String in UTF-8 encoding
   */
  public String getString(int start, int end) {
    byte[] bytes = getBytes(start, end);
    Charset cs = Charset.forName("UTF-8");
    return new String(bytes, cs);
  }

  /**
   * Appends the specified {@code Buffer} to the end of this Buffer. The buffer will expand as necessary to accommodate
   * any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  public Buffer appendBuffer(Buffer buff) {
    ByteBuf cb = buff.getByteBuf();
    buffer.writeBytes(buff.getByteBuf());
    cb.readerIndex(0); // Need to reset readerindex since Netty write modifies readerIndex of source!
    return this;
  }

  /**
   * Appends the specified {@code Buffer} starting at the {@code offset} using {@code len} to the end of this Buffer. The buffer will expand as necessary to accommodate
   * any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  public Buffer appendBuffer(Buffer buff, int offset,int len) {
    buffer.writeBytes(buff.getByteBuf(), offset, len);
    return this;
  }

  /**
   * Appends the specified {@code byte[]} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  public Buffer appendBytes(byte[] bytes) {
    buffer.writeBytes(bytes);
    return this;
  }

  /**
   * Appends the specified number of bytes from {@code byte[]} to the end of the Buffer, starting at the given offset.
   * The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  public Buffer appendBytes(byte[] bytes, int offset, int len) {
    buffer.writeBytes(bytes, offset, len);
    return this;
  }

  /**
   * Appends the specified {@code byte} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  public Buffer appendByte(byte b) {
    buffer.writeByte(b);
    return this;
  }

  /**
   * Appends the specified {@code int} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  public Buffer appendInt(int i) {
    buffer.writeInt(i);
    return this;
  }

  /**
   * Appends the specified {@code long} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  public Buffer appendLong(long l) {
    buffer.writeLong(l);
    return this;
  }

  /**
   * Appends the specified {@code short} to the end of the Buffer.The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  public Buffer appendShort(short s) {
    buffer.writeShort(s);
    return this;
  }

  /**
   * Appends the specified {@code float} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  public Buffer appendFloat(float f) {
    buffer.writeFloat(f);
    return this;
  }

  /**
   * Appends the specified {@code double} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  public Buffer appendDouble(double d) {
    buffer.writeDouble(d);
    return this;
  }

  /**
   * Appends the specified {@code String} to the end of the Buffer with the encoding as specified by {@code enc}.<p>
   * The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.<p>
   */
  public Buffer appendString(String str, String enc) {
    return append(str, Charset.forName(enc));
  }

  /**
   * Appends the specified {@code String str} to the end of the Buffer with UTF-8 encoding.<p>
   * The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together<p>
   */
  public Buffer appendString(String str) {
    return append(str, CharsetUtil.UTF_8);
  }

  /**
   * Sets the {@code byte} at position {@code pos} in the Buffer to the value {@code b}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setByte(int pos, byte b) {
    ensureWritable(pos, 1);
    buffer.setByte(pos, b);
    return this;
  }

  /**
   * Sets the {@code int} at position {@code pos} in the Buffer to the value {@code i}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setInt(int pos, int i) {
    ensureWritable(pos, 4);
    buffer.setInt(pos, i);
    return this;
  }

  /**
   * Sets the {@code long} at position {@code pos} in the Buffer to the value {@code l}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setLong(int pos, long l) {
    ensureWritable(pos, 8);
    buffer.setLong(pos, l);
    return this;
  }

  /**
   * Sets the {@code double} at position {@code pos} in the Buffer to the value {@code d}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setDouble(int pos, double d) {
    ensureWritable(pos, 8);
    buffer.setDouble(pos, d);
    return this;
  }

  /**
   * Sets the {@code float} at position {@code pos} in the Buffer to the value {@code f}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setFloat(int pos, float f) {
    ensureWritable(pos, 4);
    buffer.setFloat(pos, f);
    return this;
  }

  /**
   * Sets the {@code short} at position {@code pos} in the Buffer to the value {@code s}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setShort(int pos, short s) {
    ensureWritable(pos, 2);
    buffer.setShort(pos, s);
    return this;
  }

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the bytes represented by the {@code Buffer b}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setBuffer(int pos, Buffer b) {
    ensureWritable(pos, b.length());
    buffer.setBytes(pos, b.getByteBuf());
    return this;
  }

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the bytes represented by the {@code Buffer b} on the given {@code offset} and {@code len}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setBuffer(int pos, Buffer b, int offset, int len) {
    ensureWritable(pos, len);
    buffer.setBytes(pos, b.getByteBuf(), offset, len);
    return this;
  }

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the bytes represented by the {@code ByteBuffer b}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setBytes(int pos, ByteBuffer b) {
    ensureWritable(pos, b.limit());
    buffer.setBytes(pos, b);
    return this;
  }

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the bytes represented by the {@code byte[] b}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setBytes(int pos, byte[] b) {
    ensureWritable(pos, b.length);
    buffer.setBytes(pos, b);
    return this;
  }


  /**
   * Sets the given number of bytes at position {@code pos} in the Buffer to the bytes represented by the {@code byte[] b}.<p></p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setBytes(int pos, byte[] b, int offset, int len) {
    ensureWritable(pos, len);
    buffer.setBytes(pos, b, offset, len);
    return this;
  }

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the value of {@code str} encoded in UTF-8.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setString(int pos, String str) {
    return setBytes(pos, str, CharsetUtil.UTF_8);
  }

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the value of {@code str} encoded in encoding {@code enc}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  public Buffer setString(int pos, String str, String enc) {
    return setBytes(pos, str, Charset.forName(enc));
  }

  /**
   * Returns the length of the buffer, measured in bytes.
   * All positions are indexed from zero.
   */
  public int length() {
    return buffer.writerIndex();
  }

  /**
   * Returns a copy of the entire Buffer.
   */
  public Buffer copy() {
    return new Buffer(buffer.copy());
  }

  /**
   * Returns the Buffer as a Netty {@code ByteBuf}.<p>
   * This method is meant for internal use only.
   */
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
    Buffer buffer1 = (Buffer) o;
    return buffer.equals(buffer1.buffer);
  }
}
