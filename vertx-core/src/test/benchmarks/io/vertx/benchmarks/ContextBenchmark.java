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

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.internal.ContextInternal;
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
public class ContextBenchmark extends BenchmarkBase {

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public static void consume(final String buf) {
  }

  @State(Scope.Thread)
  public static class BaselineState {

    Vertx vertx;
    ContextInternal context;
    Handler<Void> task;

    @Setup
    public void setup() {
      vertx = Vertx.vertx(new VertxOptions().setDisableTCCL(true));
      context = BenchmarkContext.create(vertx);
      task = v -> consume("the-string");
    }
  }

  @Benchmark
  public void runOnContext(BaselineState state) {
    state.context.runOnContext(state.task);
  }

  @Benchmark
  @Fork(jvmArgsAppend = { "-Dvertx.threadChecks=false", "-Dvertx.disableContextTimings=true" })
  public void runOnContextNoChecks(BaselineState state) {
    state.context.runOnContext(state.task);
  }

}
