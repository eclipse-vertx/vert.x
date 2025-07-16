/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
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
import io.netty.util.ResourceLeakDetector;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class BufferReadBenchmark {

  static {
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
  }

  private ByteBuf nettyByteBuf;
  private Buffer vertxBuffer;
  @Param({"64"})
  private int batchSize;

  @Setup
  public void setup() {
      nettyByteBuf = VertxByteBufAllocator.DEFAULT.heapBuffer(batchSize);
      nettyByteBuf.ensureWritable(batchSize);
      vertxBuffer = Buffer.buffer(batchSize);
      for (int i = 0; i < batchSize; i++) {
          nettyByteBuf.setByte(i, (byte) i);
          vertxBuffer.setByte(i, (byte) i);
      }
  }

  @Benchmark
  public void getByteBatchNetty(Blackhole bh) {
    ByteBuf buffer = this.nettyByteBuf;
    for (int i = 0, size = batchSize; i < size; i++) {
      bh.consume(buffer.getByte(i));
    }
  }

  @Benchmark
  public void getByteBatchVertx(Blackhole bh) {
    Buffer buffer = this.vertxBuffer;
    for (int i = 0, size = batchSize; i < size; i++) {
      bh.consume(buffer.getByte(i));
    }
  }

  @Benchmark
  public void getBytesNetty(Blackhole bh) {
    ByteBuf buffer = this.nettyByteBuf;
    int readerIndex = buffer.readerIndex();
    bh.consume(buffer.getByte(readerIndex));
    readerIndex += 1;
    bh.consume(buffer.getShortLE(readerIndex));
    readerIndex += 2;
    bh.consume(buffer.getIntLE(readerIndex));
    readerIndex += 4;
    bh.consume(buffer.getLongLE(readerIndex));
  }

  @Benchmark
  public void getBytesVertx(Blackhole bh) {
    Buffer buffer = this.vertxBuffer;
    int readerIndex = 0;
    bh.consume(buffer.getByte(readerIndex));
    readerIndex += 1;
    bh.consume(buffer.getShortLE(readerIndex));
    readerIndex += 2;
    bh.consume(buffer.getIntLE(readerIndex));
    readerIndex += 4;
    bh.consume(buffer.getLongLE(readerIndex));
  }

  @Benchmark
  public void getBytesConstantOffsetNetty(Blackhole bh) {
    ByteBuf buffer = this.nettyByteBuf;
    bh.consume(buffer.getByte(0));
    bh.consume(buffer.getShortLE(1));
    bh.consume(buffer.getIntLE(3));
    bh.consume(buffer.getLongLE(7));
  }

  @Benchmark
  public void getBytesConstantOffsetVertx(Blackhole bh) {
    Buffer buffer = this.vertxBuffer;
    bh.consume(buffer.getByte(0));
    bh.consume(buffer.getShortLE(1));
    bh.consume(buffer.getIntLE(3));
    bh.consume(buffer.getLongLE(7));
  }
}
