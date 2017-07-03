/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.benchmarks;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.BenchmarkContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@State(Scope.Thread)
public class RunOnContextBenchmark extends BenchmarkBase {

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public static void consume(final String buf) {
  }

  @State(Scope.Thread)
  public static class BaselineState {

    Vertx vertx;
    BenchmarkContext context;
    Handler<Void> task;

    @Setup
    public void setup() {
      vertx = Vertx.vertx();
      context = BenchmarkContext.create(vertx);
      task = v -> consume("the-string");
    }
  }

  @Benchmark
  public void baseline(BaselineState state) {
    state.context.runDirect(state.task);
  }

  @Benchmark
  @Fork(jvmArgsAppend = { "-Dvertx.threadChecks=false", "-Dvertx.disableContextTimings=true", "-Dvertx.disableTCCL=true" })
  public void noChecks(BaselineState state) {
    state.context.runDirect(state.task);
  }
}
