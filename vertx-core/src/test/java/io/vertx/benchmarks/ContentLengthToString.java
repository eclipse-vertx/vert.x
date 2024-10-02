/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.benchmarks;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

import io.vertx.core.http.impl.HttpUtils;

@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = MILLISECONDS)
@Fork(value = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(java.util.concurrent.TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ContentLengthToString {

  @Param({"255", "511", "1023" })
  public int maxContentLength;
  private int[] contentLengthIndexes;
  private long inputSequence;

  @Setup
  public void init(Blackhole bh, BenchmarkParams params) {
    final int MAX_CONTENT_LENGTH_SIZE = 1024;
    if (maxContentLength >= MAX_CONTENT_LENGTH_SIZE) {
      throw new IllegalArgumentException("maxContentLength must be < " + MAX_CONTENT_LENGTH_SIZE);
    }
    contentLengthIndexes = new int[128 * MAX_CONTENT_LENGTH_SIZE];
    if (Integer.bitCount(contentLengthIndexes.length) != 1) {
      throw new IllegalArgumentException("contentLengthIndexes must be a power of 2");
    }
    Random rnd = new Random(42);
    for (int i = 0; i < contentLengthIndexes.length; i++) {
      contentLengthIndexes[i] = rnd.nextInt(maxContentLength);
    }
  }

  private long nextContentLength() {
      int[] contentLengthIndexes = this.contentLengthIndexes;
      int nextInputIndex = (int) inputSequence & (contentLengthIndexes.length - 1);
      int contentLength = contentLengthIndexes[nextInputIndex];
      inputSequence++;
      return contentLength;
  }

  @Benchmark
  public String contentLengthToString() {
    return String.valueOf(nextContentLength());
  }

  @Benchmark
  public String contentLengthHttpUtils() {
    return HttpUtils.positiveLongToString(nextContentLength());
  }

}
