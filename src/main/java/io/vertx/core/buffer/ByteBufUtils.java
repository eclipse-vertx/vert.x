/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.buffer;

import io.netty.buffer.*;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;

import java.nio.charset.Charset;
import java.util.Objects;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static io.netty.util.internal.StringUtil.isSurrogate;

public final class ByteBufUtils {
  private ByteBufUtils() {

  }

  // this wrapper is used to save copying byte[]
  private static final class UnpooledWrappedByteBuf extends UnpooledHeapByteBuf {

    protected UnpooledWrappedByteBuf(ByteBufAllocator alloc, byte[] initialArray, int maxCapacity) {
      super(alloc, initialArray, maxCapacity);
    }
  }

  public static UnpooledHeapByteBuf unpooledNotInstrumentedHeapByteBuf(int initialCapacity, int maxCapacity) {
    checkPositiveOrZero(initialCapacity, "initialCapacity");
    // this save instrumented heap ByteBuf allocation that would save statistics collection
    return PlatformDependent.hasUnsafe() ?
      new UnpooledUnsafeHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, initialCapacity, maxCapacity) :
      new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, initialCapacity, maxCapacity);
  }

  public static ByteBuf unpooledBufferOf(String str, String enc) {
    final Charset charset = Charset.forName(Objects.requireNonNull(enc));
    final byte[] bytes;
    if (charset.equals(CharsetUtil.UTF_8)) {
      bytes = new byte[ByteBufUtil.utf8Bytes(str)];
      writeUtf8(bytes, 0, str, 0, str.length());
    } else if (charset.equals(CharsetUtil.US_ASCII) || charset.equals(CharsetUtil.ISO_8859_1)) {
      bytes = new byte[str.length()];
      writeAscii(bytes, 0, str, str.length());
    } else {
      bytes = str.getBytes(charset);
    }
    return new UnpooledWrappedByteBuf(UnpooledByteBufAllocator.DEFAULT, bytes, Integer.MAX_VALUE);
  }

  public static ByteBuf unpooledBufferOf(String str, Charset charset) {
    final byte[] bytes;
    if (charset.equals(CharsetUtil.UTF_8)) {
      bytes = new byte[ByteBufUtil.utf8Bytes(str)];
      writeUtf8(bytes, 0, str, 0, str.length());
    } else if (charset.equals(CharsetUtil.US_ASCII) || charset.equals(CharsetUtil.ISO_8859_1)) {
      bytes = new byte[str.length()];
      writeAscii(bytes, 0, str, str.length());
    } else {
      bytes = str.getBytes(charset);
    }
    return new UnpooledWrappedByteBuf(UnpooledByteBufAllocator.DEFAULT, bytes, Integer.MAX_VALUE);
  }

  public static ByteBuf utf8UnpooledBufferOf(String str) {
    byte[] bytes = new byte[ByteBufUtil.utf8Bytes(str)];
    writeUtf8(bytes, 0, str, 0, str.length());
    return new UnpooledWrappedByteBuf(UnpooledByteBufAllocator.DEFAULT, bytes, Integer.MAX_VALUE);
  }

  private static final byte WRITE_UTF_UNKNOWN = (byte) '?';

  static int writeAscii(byte[] buffer, int writerIndex, CharSequence seq, int len) {
    for (int i = 0; i < len; i++) {
      buffer[writerIndex++] = AsciiString.c2b(seq.charAt(i));
    }
    return len;
  }

  // Fast-Path implementation
  private static int writeUtf8(byte[] buffer, int writerIndex, CharSequence seq, int start, int end) {
    int oldWriterIndex = writerIndex;
    for (int i = start; i < end; i++) {
      char c = seq.charAt(i);
      if (c < 0x80) {
        buffer[writerIndex++] = (byte) c;
      } else if (c < 0x800) {
        buffer[writerIndex++] = (byte) (0xc0 | (c >> 6));
        buffer[writerIndex++] = (byte) (0x80 | (c & 0x3f));
      } else if (isSurrogate(c)) {
        if (!Character.isHighSurrogate(c)) {
          buffer[writerIndex++] = WRITE_UTF_UNKNOWN;
          continue;
        }
        // Surrogate Pair consumes 2 characters.
        if (++i == end) {
          buffer[writerIndex++] = WRITE_UTF_UNKNOWN;
          break;
        }
        // Extra method to allow inlining the rest of writeUtf8 which is the most likely code path.
        writerIndex = writeUtf8Surrogate(buffer, writerIndex, c, seq.charAt(i));
      } else {
        buffer[writerIndex++] = (byte) (0xe0 | (c >> 12));
        buffer[writerIndex++] = (byte) (0x80 | ((c >> 6) & 0x3f));
        buffer[writerIndex++] = (byte) (0x80 | (c & 0x3f));
      }
    }
    return writerIndex - oldWriterIndex;
  }

  private static int writeUtf8Surrogate(byte[] buffer, int writerIndex, char c, char c2) {
    if (!Character.isLowSurrogate(c2)) {
      buffer[writerIndex++] = WRITE_UTF_UNKNOWN;
      buffer[writerIndex++] = (byte) (Character.isHighSurrogate(c2) ? WRITE_UTF_UNKNOWN : c2);
      return writerIndex;
    }
    int codePoint = Character.toCodePoint(c, c2);
    // See http://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
    buffer[writerIndex++] = (byte) (0xf0 | (codePoint >> 18));
    buffer[writerIndex++] = (byte) (0x80 | ((codePoint >> 12) & 0x3f));
    buffer[writerIndex++] = (byte) (0x80 | ((codePoint >> 6) & 0x3f));
    buffer[writerIndex++] = (byte) (0x80 | (codePoint & 0x3f));
    return writerIndex;
  }

}
