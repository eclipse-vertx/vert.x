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

import io.vertx.core.internal.pool.CombinerExecutor;
import io.vertx.core.internal.pool.Executor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Thomas Segismont
 * @author slinkydeveloper
 */
@State(Scope.Benchmark)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = MILLISECONDS)
@Threads(2)
public class CombinerExecutorBenchmark extends BenchmarkBase {

  private Executor<Object> exec;
  private Executor.Action<Object> action;

  private CountDownLatch latch = new CountDownLatch(1);

  @Setup
  public void setup() throws Exception {
    exec = new CombinerExecutor<>(new Object());
    action = state -> {
      Blackhole.consumeCPU(0);
      return null;
    };
    CountDownLatch l = new CountDownLatch(2);
    new Thread(() -> {
      exec.submit(state -> {
        l.countDown();
        try {
          latch.await();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        return null;
      });
    }).start();
    l.await(20, TimeUnit.SECONDS);
  }

  @TearDown
  public void tearDown() {
    latch.countDown();
  }

  @Benchmark
  public void impl() {
    exec.submit(action);
  }
}
