/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.benchmarks;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.openjdk.jmh.annotations.*;

import static org.openjdk.jmh.annotations.CompilerControl.Mode.DONT_INLINE;

@State(Scope.Thread)
public class FutureListenerBenchmark extends BenchmarkBase {

  @Param({"2", "4", "6", "8"})
  int listenerCount;

  @CompilerControl(DONT_INLINE)
  public static void consume(AsyncResult<Void> ar) {
  }

  @Benchmark
  public void addListeners() throws Exception {
    Promise<Void> promise = Promise.promise();
    Future<Void> future = promise.future();
    for (int i = 0; i < listenerCount; i++) {
      future.onComplete(FutureListenerBenchmark::consume);
    }
    promise.complete();
  }
}
