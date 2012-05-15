/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.groovy.core.buffer

import org.vertx.java.core.buffer.Buffer as JBuffer

import java.nio.ByteBuffer

/**
 * A Buffer represents a sequence of zero or more bytes that can be written to or read from, and which expands as
 * necessary to accomodate any bytes written to it.<p>
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
 * Methods {@code putAt} and {@code getAt} are defined allowing you to use index notation to get/set bytes at a specific position in the buffer.<p>
 * Methods {@code leftShift} are defined to mean append allowing you to use the familiar Groovy << operator on buffers.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Buffer extends org.vertx.java.core.buffer.Buffer {

  private final JBuffer jBuffer

  private Buffer(JBuffer jBuffer) {
    this.jBuffer = jBuffer
  }

  /**
   * Create an empty buffer
   */
  Buffer() {
    jBuffer = new JBuffer()
  }

  /**
   * Creates a new empty Buffer that is expected to have a size of {@code initialSizeHint} after data has been
   * written to it.<p>
   * Please note that {@code length} of the Buffer immediately after creation will be zero.<p>
   * The {@code initialSizeHint} is merely a hint to the system for how much memory to initially allocate to the buffer to prevent excessive
   * automatic re-allocations as data is written to it.
   */
  Buffer(int initialSizeHint) {
    jBuffer = new JBuffer(initialSizeHint)
  }

  /**
   * Create a new Buffer that contains the contents of a {@code byte[]}
   */
  Buffer(byte[] bytes) {
    jBuffer = new JBuffer(bytes)
  }

  /**
   * Create a new Buffer that contains the contents of a {@code String str} encoded according to the encoding {@code enc}
   */
  Buffer(String str, String enc) {
    jBuffer = new JBuffer(str, enc)
  }

  /**
   * Create a new Buffer that contains the contents of {@code String str} encoded with UTF-8 encoding
   */
  Buffer(String str) {
    jBuffer = new JBuffer(str)
  }

  /**
   * Returns a {@code String} represention of the Buffer with the encoding specified by {@code enc}
   */
  String toString() {
    jBuffer.toString()
  }

  /**
   * Returns the {@code byte} at position {@code pos} in the Buffer.
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 1} is greater than the length of the Buffer.
   */
  byte getByte(int pos) {
    jBuffer.getByte(pos)
  }

  /**
   * Same as {@link #getByte(int)}
   *
   */
  byte getAt(int pos) {
    getByte(pos)
  }

  /**
   * Returns the {@code int} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 4} is greater than the length of the Buffer.
   */
  int getInt(int pos) {
    jBuffer.getInt(pos)
  }

  /**
   * Returns the {@code long} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 8} is greater than the length of the Buffer.
   */
  long getLong(int pos) {
    jBuffer.getLong(pos)
  }

  /**
   * Returns the {@code double} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 8} is greater than the length of the Buffer.
   */
  double getDouble(int pos) {
    jBuffer.getDouble(pos)
  }

  /**
   * Returns the {@code float} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 4} is greater than the length of the Buffer.
   */
  float getFloat(int pos) {
    jBuffer.getFloat(pos)
  }

  /**
   * Returns the {@code short} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 2} is greater than the length of the Buffer.
   */
  short getShort(int pos) {
    jBuffer.getShort(pos)
  }

  /**
   * Returns a copy of the entire Buffer as a {@code byte[]}
   */
  byte[] getBytes() {
    jBuffer.getBytes()
  }

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@code byte[]} starting at position {@code start}
   * and ending at position {@code end - 1}
   */
  byte[] getBytes(int start, int end) {
    jBuffer.getBytes(start, end)
  }

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@link Buffer} starting at position {@code start}
   * and ending at position {@code end - 1}
   */
  Buffer getBuffer(int start, int end) {
    new Buffer(jBuffer.getBuffer(start, end))
  }

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@code String} starting at position {@code start}
   * and ending at position {@code end - 1} interpreted as a String in the specified encoding
   */
  String getString(int start, int end, String enc) {
    jBuffer.getString(start, end, enc)
  }

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@code String} starting at position {@code start}
   * and ending at position {@code end - 1} interpreted as a String in UTF-8 encoding
   */
  String getString(int start, int end) {
    jBuffer.getString(start, end)
  }

  /**
   * Same as {@link #appendBuffer(Buffer)}
   */
  Buffer leftShift(Buffer buff) {
    appendBuffer(buff)
  }

  /**
   * Appends the specified {@code Buffer} to the end of this Buffer. The buffer will expand as necessary to accomodate
   * any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  Buffer appendBuffer(Buffer buff) {
    jBuffer.appendBuffer(buff.jBuffer)
    this
  }

  /**
   * Same as {@link #appendBytes(byte[])}
   */
  Buffer leftShift(byte[] bytes) {
    appendBytes(bytes)
  }

  /**
   * Appends the specified {@code byte[]} to the end of the Buffer. The buffer will expand as necessary to accomodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  Buffer appendBytes(byte[] bytes) {
    jBuffer.appendBytes(bytes)
    this
  }

  /**
   * Same as {@link #appendByte(byte)}
   */
  Buffer leftShift(byte b) {
    appendByte(b)
  }

  /**
   * Appends the specified {@code byte} to the end of the Buffer. The buffer will expand as necessary to accomodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  Buffer appendByte(byte b) {
    jBuffer.appendByte(b)
    this
  }

  /**
   * Same as {@link #appendInt(int)}
   */
  Buffer leftShift(int i) {
    appendInt(i)
  }

  /**
   * Appends the specified {@code int} to the end of the Buffer. The buffer will expand as necessary to accomodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  Buffer appendInt(int i) {
    jBuffer.appendInt(i)
    this
  }

  /**
   * Same as {@link #appendLong(long)}
   */
  Buffer leftShift(long l) {
    appendLong(l)
  }

  /**
   * Appends the specified {@code long} to the end of the Buffer. The buffer will expand as necessary to accomodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  Buffer appendLong(long l) {
    jBuffer.appendLong(l)
    this
  }

  /**
   * Same as {@link #appendShort(short)}
   */
  Buffer leftShift(short s) {
    appendShort(s)
  }

  /**
   * Appends the specified {@code short} to the end of the Buffer.The buffer will expand as necessary to accomodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  Buffer appendShort(short s) {
    jBuffer.appendShort(s)
    this
  }

  /**
   * Same as {@link #appendFloat(float)}
   */
  Buffer leftShift(float f) {
    appendFloat(f)
  }

  /**
   * Appends the specified {@code float} to the end of the Buffer. The buffer will expand as necessary to accomodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  Buffer appendFloat(float f) {
    jBuffer.appendFloat(f)
    this
  }

  /**
   * Same as {@link #appendDouble(double)}
   */
  Buffer leftShift(double d) {
    appendDouble(d)
  }

  /**
   * Appends the specified {@code double} to the end of the Buffer. The buffer will expand as necessary to accomodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  Buffer appendDouble(double d) {
    jBuffer.appendDouble(d)
    this
  }

  /**
   * Appends the specified {@code String} to the end of the Buffer with the encoding as specified by {@code enc}.<p>
   * The buffer will expand as necessary to accomodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.<p>
   */
  Buffer appendString(String str, String enc) {
    jBuffer.appendString(str, enc)
    this
  }

  /**
   * Same as {@link #appendString(String)}
   */
  Buffer leftShift(String s) {
    appendString(s)
  }

  /**
   * Appends the specified {@code String str} to the end of the Buffer with UTF-8 encoding.<p>
   * The buffer will expand as necessary to accomodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together<p>
   */
  Buffer appendString(String str) {
    jBuffer.appendString(str)
    this
  }

  /**
   * Sets the {@code byte} at position {@code pos} in the Buffer to the value {@code b}.<p>
   * The buffer will expand as necessary to accomodate any value written.
   */
  Buffer setByte(int pos, byte b) {
    jBuffer.setByte(pos, b)
    this
  }

  /**
   * Same as {@link #setByte(int, byte)}
   */
  void putAt(int pos, byte b) {
    setByte(pos, b)
  }

  /**
   * Sets the {@code int} at position {@code pos} in the Buffer to the value {@code i}.<p>
   * The buffer will expand as necessary to accomodate any value written.
   */
  Buffer setInt(int pos, int i) {
    jBuffer.setInt(pos, i)
    this
  }

  /**
   * Same as {@link #setInt(int, int)}
   */
  void putAt(int pos, int i) {
    setInt(pos, i)
  }

  /**
   * Sets the {@code long} at position {@code pos} in the Buffer to the value {@code l}.<p>
   * The buffer will expand as necessary to accomodate any value written.
   */
  Buffer setLong(int pos, long l) {
    jBuffer.setLong(pos, l)
    this
  }

  /**
   * Same as {@link #setLong(int, long)}
   */
  void putAt(int pos, long l) {
    setLong(pos, l)
  }

  /**
   * Sets the {@code double} at position {@code pos} in the Buffer to the value {@code d}.<p>
   * The buffer will expand as necessary to accomodate any value written.
   */
  Buffer setDouble(int pos, double d) {
    jBuffer.setDouble(pos, d)
    this
  }

  /**
   * Same as {@link #setDouble(int, double)}
   */
  void putAt(int pos, double d) {
    setDouble(pos, d)
  }

  /**
   * Sets the {@code float} at position {@code pos} in the Buffer to the value {@code f}.<p>
   * The buffer will expand as necessary to accomodate any value written.
   */
  Buffer setFloat(int pos, float f) {
    jBuffer.setFloat(pos, f)
    this
  }

  /**
   * Same as {@link #setFloat(int, float)}
   */
  void putAt(int pos, float f) {
    setFloat(pos, f)
  }

  /**
   * Sets the {@code short} at position {@code pos} in the Buffer to the value {@code s}.<p>
   * The buffer will expand as necessary to accomodate any value written.
   */
  Buffer setShort(int pos, short s) {
    jBuffer.setShort(pos, s)
    this
  }

  /**
   * Same as {@link #setShort(int, short)}
   */
  void putAt(int pos, short s) {
    setShort(pos, s)
  }

   /**
   * Sets the bytes at position {@code pos} in the Buffer to the bytes represented by the {@code Buffer b}.<p>
   * The buffer will expand as necessary to accomodate any value written.
   */
  Buffer setBuffer(int pos, Buffer b) {
    jBuffer.setBuffer(pos, b.toJavaBuffer())
    this
  }

  /**
   * Same as {@link #setBuffer(int, Buffer)}
   */
  void putAt(int pos, Buffer b) {
    setBuffer(pos, b)
  }

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the bytes represented by the {@code ByteBuffer b}.<p>
   * The buffer will expand as necessary to accomodate any value written.
   */
  Buffer setBytes(int pos, ByteBuffer b) {
    jBuffer.setBytes(pos, b)
    this
  }

  /**
   * Same as {@link #setBytes(int, ByteBuffer)}
   */
  void putAt(int pos, ByteBuffer b) {
    setBytes(pos, b)
  }

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the bytes represented by the {@code byte[] b}.<p>
   * The buffer will expand as necessary to accomodate any value written.
   */
  Buffer setBytes(int pos, byte[] b) {
    jBuffer.setBytes(pos, b)
    this
  }

  /**
   * Same as {@link #setBytes(int, byte[])}
   */
  void putAt(int pos, byte[] b) {
    setBytes(pos, b)
  }

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the value of {@code str} endoded in UTF-8.<p>
   * The buffer will expand as necessary to accomodate any value written.
   */
  Buffer setString(int pos, String str) {
    jBuffer.setString(pos, str)
    this
  }

  /**
   * Same as {@link #setString(int, String)}
   */
  void putAt(int pos, String str) {
    setString(pos, str)
  }

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the value of {@code str} encoded in encoding {@code enc}.<p>
   * The buffer will expand as necessary to accomodate any value written.
   */
  Buffer setString(int pos, String str, String enc) {
    jBuffer.setString(pos, str, enc)
    this
  }

  /**
   * Returns the length of the buffer, measured in bytes.
   * All positions are indexed from zero.
   */
  int length() {
    jBuffer.length()
  }

  /**
   * Synonym for {@link #length}
   */
  int getLength() {
    jBuffer.length()
  }

  /**
   * Returns a copy of the entire Buffer.
   */
  Buffer copy() {
    new Buffer(jBuffer.copy())
  }



  int hashCode() {
    jBuffer.hashCode()
  }

  /**
   * Returns the underlying Java buffer
   */
  JBuffer toJavaBuffer() {
    jBuffer
  }

}
