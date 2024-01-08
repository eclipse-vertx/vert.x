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

import io.netty.util.AsciiString;
import io.vertx.core.http.impl.HttpUtils;
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

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@State(Scope.Benchmark)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(NANOSECONDS)
@Fork(2)
public class HttpUtilsAsciiToLowerCaseBenchmark {

  @Param({"ascii", "string", "builder"})
  public String type;

  @Param({"false", "true"})
  public boolean lowerCase;
  private CharSequence sequence;

  @Setup
  public void init() {
    sequence = create(type, lowerCase ? "content-length" : "Content-Length");
  }

  private CharSequence create(String type, String content) {

    switch (type) {
      case "ascii":
        return new AsciiString(content);
      case "string":
        return content;
      case "builder":
        return new StringBuilder(content);
      default:
        throw new IllegalStateException();
    }
  }

  @Benchmark
  public CharSequence toLowerCase() {
    return HttpUtils.toLowerCase(sequence);
  }

}
