/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.benchmarks;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.ByteBufUtils;
import org.openjdk.jmh.annotations.*;

@State(Scope.Benchmark)
public class BufferCreationBenchmark extends BenchmarkBase {

  @Param({"1", "4", "8", "16", "64"})
  private int length;
  private String endString;

  @Setup
  public void setup() {
    final StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      // single ASCII char
      builder.append('a');
    }
    endString = builder.toString();
  }

  @Benchmark
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public Buffer utf8Buffer() {
    return Buffer.buffer(endString);
  }

  @Benchmark
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public Buffer utf8CharsetLookupBuffer() {
    return Buffer.buffer(endString, CharsetUtil.UTF_8.name());
  }

  @Benchmark
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public ByteBuf utf8ByteBuffer() {
    return ByteBufUtils.utf8UnpooledBufferOf(endString);
  }
}
