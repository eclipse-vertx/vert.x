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
import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;

import java.nio.charset.Charset;
import java.util.Objects;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

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
    return unpooledBufferOf(str, charset);
  }

  public static ByteBuf unpooledBufferOf(String str, Charset charset) {
    if (charset.equals(CharsetUtil.UTF_8)) {
      return utf8UnpooledBufferOf(str);
    }
    if (charset.equals(CharsetUtil.US_ASCII) || charset.equals(CharsetUtil.ISO_8859_1)) {
      return usAsciiUnpooledBufferOf(str);
    }
    final byte[] bytes = str.getBytes(charset);
    return new UnpooledWrappedByteBuf(UnpooledByteBufAllocator.DEFAULT, bytes, Integer.MAX_VALUE);
  }

  public static ByteBuf utf8UnpooledBufferOf(String str) {
    final int utf8Bytes = ByteBufUtil.utf8Bytes(str);
    final UnpooledHeapByteBuf buffer = unpooledNotInstrumentedHeapByteBuf(utf8Bytes, Integer.MAX_VALUE);
    ByteBufUtil.reserveAndWriteUtf8(buffer, str, utf8Bytes);
    return buffer;
  }

  public static ByteBuf usAsciiUnpooledBufferOf(String str) {
    final int asciiBytes = str.length();
    final UnpooledHeapByteBuf buffer = unpooledNotInstrumentedHeapByteBuf(asciiBytes, Integer.MAX_VALUE);
    ByteBufUtil.writeAscii(buffer, str);
    return buffer;
  }

}
