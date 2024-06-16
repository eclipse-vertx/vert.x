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
package io.vertx.benchmarks;

import io.vertx.core.impl.utils.ConcurrentCyclicSequence;
import org.openjdk.jmh.annotations.*;

import java.util.stream.IntStream;

@State(Scope.Benchmark)
@Threads(8)
public class ConcurrentCyclicSequenceBenchmark extends BenchmarkBase {

  private ConcurrentCyclicSequence<String> seq1;
  private ConcurrentCyclicSequence<String> seq2;
  private ConcurrentCyclicSequence<String> seq4;
  private ConcurrentCyclicSequence<String> seq8;
  private ConcurrentCyclicSequence<String> seq16;

  private static ConcurrentCyclicSequence<String> gen(int size) {
    return new ConcurrentCyclicSequence<>(IntStream.range(0, size + 1).mapToObj(i -> "" + i).toArray(String[]::new));
  }

  @Setup
  public void setup() {
    seq1 = gen(1);
    seq2 = gen(2);
    seq4 = gen(4);
    seq8 = gen(8);
    seq16 = gen(16);
  }

  @Benchmark
  public String size1() {
    return seq1.next();
  }

  @Benchmark
  public String size2() {
    return seq2.next();
  }

  @Benchmark
  public String size4() {
    return seq4.next();
  }

  @Benchmark
  public String size8() {
    return seq8.next();
  }

  @Benchmark
  public String size16() {
    return seq16.next();
  }
}
