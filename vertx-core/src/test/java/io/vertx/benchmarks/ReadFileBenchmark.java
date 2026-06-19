/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.benchmarks;

import io.netty.util.ResourceLeakDetector;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(5)
public class ReadFileBenchmark {

  static {
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
  }

  @Param({"128", "4096", "65536", "1048576"})
  private int fileSize;

  private Path tempFile;

  @Setup
  public void setup() throws IOException {
    tempFile = Files.createTempFile("vertx-bench-", ".dat");
    byte[] data = new byte[fileSize];
    ThreadLocalRandom.current().nextBytes(data);
    Files.write(tempFile, data);
  }

  @TearDown
  public void tearDown() throws IOException {
    Files.deleteIfExists(tempFile);
  }

  @Benchmark
  public Buffer readAllBytes() throws IOException {
    byte[] bytes = Files.readAllBytes(tempFile);
    return Buffer.buffer(bytes);
  }

  @Benchmark
  public Buffer fileChannel() throws IOException {
    try (FileChannel fc = FileChannel.open(tempFile, StandardOpenOption.READ)) {
      int len = (int) fc.size();
      BufferInternal res = BufferInternal.buffer(len);
      res.unwrap().writeBytes(fc, 0, len);
      return res;
    }
  }
}
