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
package io.vertx.core.buffer;


import io.netty.buffer.ByteBuf;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.ServiceHelper;
import io.vertx.core.shareddata.impl.ClusterSerializable;
import io.vertx.core.spi.BufferFactory;

import java.nio.ByteBuffer;

/**
 * Most data is shuffled around inside Vert.x using buffers.
 * <p>
 * A buffer is a sequence of zero or more bytes that can read from or written to and which expands automatically as
 * necessary to accommodate any bytes written to it. You can perhaps think of a buffer as smart byte array.
 * <p>
 * Please consult the documentation for more information on buffers.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Buffer extends ClusterSerializable {

  /**
   * Create a new, empty buffer.
   *
   * @return the buffer
   */
  static Buffer buffer() {
    return factory.buffer();
  }

  /**
   * Create a new buffer given the initial size hint.
   * <p>
   * If you know the buffer will require a certain size, providing the hint can prevent unnecessary re-allocations
   * as the buffer is written to and resized.
   *
   * @param initialSizeHint  the hint, in bytes
   * @return the buffer
   */
  static Buffer buffer(int initialSizeHint) {
    return factory.buffer(initialSizeHint);
  }

  /**
   * Create a new buffer from a string. The string will be UTF-8 encoded into the buffer.
   *
   * @param string  the string
   * @return  the buffer
   */
  static Buffer buffer(String string) {
    return factory.buffer(string);
  }

  /**
   * Create a new buffer from a string and using the specified encoding.
   * The string will be encoded into the buffer using the specified encoding.
   *
   * @param string  the string
   * @return  the buffer
   */
  static Buffer buffer(String string, String enc) {
    return factory.buffer(string, enc);
  }

  /**
   * Create a new buffer from a byte[]. The byte[] will be copied to form the buffer.
   *
   * @param bytes  the byte array
   * @return  the buffer
   */
  @GenIgnore
  static Buffer buffer(byte[] bytes) {
    return factory.buffer(bytes);
  }

  /**
   * Create a new buffer from a Netty {@code ByteBuf}.
   *
   * @param byteBuf  the Netty ByteBuf
   * @return the buffer
   */
  @GenIgnore
  static Buffer buffer(ByteBuf byteBuf) {
    return factory.buffer(byteBuf);
  }

  /**
   * Returns a {@code String} representation of the Buffer with the encoding specified by {@code enc}
   */
  String toString(String enc);

  /**
   * Returns the {@code byte} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 1} is greater than the length of the Buffer.
   */
  byte getByte(int pos);

  /**
   * Returns the {@code int} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 4} is greater than the length of the Buffer.
   */
  int getInt(int pos);

  /**
   * Returns the {@code long} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 8} is greater than the length of the Buffer.
   */
  long getLong(int pos);

  /**
   * Returns the {@code double} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 8} is greater than the length of the Buffer.
   */
  double getDouble(int pos);

  /**
   * Returns the {@code float} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 4} is greater than the length of the Buffer.
   */
  float getFloat(int pos);

  /**
   * Returns the {@code short} at position {@code pos} in the Buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code pos} is less than {@code 0} or {@code pos + 2} is greater than the length of the Buffer.
   */
  short getShort(int pos);

  /**
   * Returns a copy of the entire Buffer as a {@code byte[]}
   */
  @GenIgnore
  byte[] getBytes();

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@code byte[]} starting at position {@code start}
   * and ending at position {@code end - 1}
   */
  @GenIgnore
  byte[] getBytes(int start, int end);

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@link io.vertx.core.buffer.Buffer} starting at position {@code start}
   * and ending at position {@code end - 1}
   */
  Buffer getBuffer(int start, int end);

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@code String} starting at position {@code start}
   * and ending at position {@code end - 1} interpreted as a String in the specified encoding
   */
  String getString(int start, int end, String enc);

  /**
   * Returns a copy of a sub-sequence the Buffer as a {@code String} starting at position {@code start}
   * and ending at position {@code end - 1} interpreted as a String in UTF-8 encoding
   */
  String getString(int start, int end);

  /**
   * Appends the specified {@code Buffer} to the end of this Buffer. The buffer will expand as necessary to accommodate
   * any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  @Fluent
  Buffer appendBuffer(Buffer buff);

  /**
   * Appends the specified {@code Buffer} starting at the {@code offset} using {@code len} to the end of this Buffer. The buffer will expand as necessary to accommodate
   * any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  @Fluent
  Buffer appendBuffer(Buffer buff, int offset, int len);

  /**
   * Appends the specified {@code byte[]} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  @GenIgnore
  @Fluent
  Buffer appendBytes(byte[] bytes);

  /**
   * Appends the specified number of bytes from {@code byte[]} to the end of the Buffer, starting at the given offset.
   * The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  @GenIgnore
  @Fluent
  Buffer appendBytes(byte[] bytes, int offset, int len);

  /**
   * Appends the specified {@code byte} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  @Fluent
  Buffer appendByte(byte b);

  /**
   * Appends the specified {@code int} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  @Fluent
  Buffer appendInt(int i);

  /**
   * Appends the specified {@code long} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  @Fluent
  Buffer appendLong(long l);

  /**
   * Appends the specified {@code short} to the end of the Buffer.The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  @Fluent
  Buffer appendShort(short s);

  /**
   * Appends the specified {@code float} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  @Fluent
  Buffer appendFloat(float f);

  /**
   * Appends the specified {@code double} to the end of the Buffer. The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.
   */
  @Fluent
  Buffer appendDouble(double d);

  /**
   * Appends the specified {@code String} to the end of the Buffer with the encoding as specified by {@code enc}.<p>
   * The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together.<p>
   */
  @Fluent
  Buffer appendString(String str, String enc);

  /**
   * Appends the specified {@code String str} to the end of the Buffer with UTF-8 encoding.<p>
   * The buffer will expand as necessary to accommodate any bytes written.<p>
   * Returns a reference to {@code this} so multiple operations can be appended together<p>
   */
  @Fluent
  Buffer appendString(String str);

  /**
   * Sets the {@code byte} at position {@code pos} in the Buffer to the value {@code b}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @Fluent
  Buffer setByte(int pos, byte b);

  /**
   * Sets the {@code int} at position {@code pos} in the Buffer to the value {@code i}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @Fluent
  Buffer setInt(int pos, int i);

  /**
   * Sets the {@code long} at position {@code pos} in the Buffer to the value {@code l}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @Fluent
  Buffer setLong(int pos, long l);

  /**
   * Sets the {@code double} at position {@code pos} in the Buffer to the value {@code d}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @Fluent
  Buffer setDouble(int pos, double d);

  /**
   * Sets the {@code float} at position {@code pos} in the Buffer to the value {@code f}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @Fluent
  Buffer setFloat(int pos, float f);

  /**
   * Sets the {@code short} at position {@code pos} in the Buffer to the value {@code s}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @Fluent
  Buffer setShort(int pos, short s);

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the bytes represented by the {@code Buffer b}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @Fluent
  Buffer setBuffer(int pos, Buffer b);

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the bytes represented by the {@code Buffer b} on the given {@code offset} and {@code len}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @Fluent
  Buffer setBuffer(int pos, Buffer b, int offset, int len);

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the bytes represented by the {@code ByteBuffer b}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @GenIgnore
  @Fluent
  Buffer setBytes(int pos, ByteBuffer b);

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the bytes represented by the {@code byte[] b}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @GenIgnore
  @Fluent
  Buffer setBytes(int pos, byte[] b);

  /**
   * Sets the given number of bytes at position {@code pos} in the Buffer to the bytes represented by the {@code byte[] b}.<p></p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @GenIgnore
  @Fluent
  Buffer setBytes(int pos, byte[] b, int offset, int len);

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the value of {@code str} encoded in UTF-8.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @Fluent
  Buffer setString(int pos, String str);

  /**
   * Sets the bytes at position {@code pos} in the Buffer to the value of {@code str} encoded in encoding {@code enc}.<p>
   * The buffer will expand as necessary to accommodate any value written.
   */
  @Fluent
  Buffer setString(int pos, String str, String enc);

  /**
   * Returns the length of the buffer, measured in bytes.
   * All positions are indexed from zero.
   */
  int length();

  /**
   * Returns a copy of the entire Buffer.
   */
  Buffer copy();

  /**
   * Returns a slice of this buffer. Modifying the content
   * of the returned buffer or this buffer affects each other's content
   * while they maintain separate indexes and marks.
   */
  Buffer slice();

  /**
   * Returns a slice of this buffer. Modifying the content
   * of the returned buffer or this buffer affects each other's content
   * while they maintain separate indexes and marks.
   */
  Buffer slice(int start, int end);

  /**
   * Returns the Buffer as a Netty {@code ByteBuf}.<p>
   * This method is meant for internal use only.
   */
  @GenIgnore
  ByteBuf getByteBuf();

  static final BufferFactory factory = ServiceHelper.loadFactory(BufferFactory.class);

}
