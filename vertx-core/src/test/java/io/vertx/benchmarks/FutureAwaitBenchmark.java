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

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Threads(1)
@Fork(5)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class FutureAwaitBenchmark {

  private final Future<String> succeededFuture = Future.succeededFuture("foo");
  private final Future<String> failedFuture = Future.failedFuture("foo");
  private final Future<String> ofCompletedPromise;
  private final Future<String> ofFailedPromise;

  public FutureAwaitBenchmark() {
    Promise<String> p1 = Promise.promise();
    p1.complete("foo");
    ofCompletedPromise = p1.future();

    Promise<String> p2 = Promise.promise();
    p2.fail("foo");
    ofFailedPromise = p2.future();
  }

  @Benchmark
  public String succeededFuture() {
    return succeededFuture.await();
  }

  @Benchmark
  public Exception failedFuture() {
    try {
      failedFuture.await();
      throw new AssertionError("Should have thrown exception");
    } catch (RuntimeException e) {
      return e;
    }
  }

  @Benchmark
  public String completedPromise() {
    return ofCompletedPromise.await();
  }

  @Benchmark
  public Exception failedPromise() {
    try {
      ofFailedPromise.await();
      throw new AssertionError("Should have thrown exception");
    } catch (RuntimeException e) {
      return e;
    }
  }
}
