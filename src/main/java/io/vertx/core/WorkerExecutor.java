/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.metrics.Measured;

/**
 * An executor for executing blocking code in Vert.x .<p>
 *
 * It provides the same <code>executeBlocking</code> operation than {@link io.vertx.core.Context} and
 * {@link io.vertx.core.Vertx} but on a separate worker pool.<p>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface WorkerExecutor extends Measured {

  /**
   * Safely execute some blocking code.
   * <p>
   * Executes the blocking code in the handler {@code blockingCodeHandler} using a thread from the worker pool.
   * <p>
   * When the code is complete the handler {@code resultHandler} will be called with the result on the original context
   * (i.e. on the original event loop of the caller).
   * <p>
   * A {@code Future} instance is passed into {@code blockingCodeHandler}. When the blocking code successfully completes,
   * the handler should call the {@link Future#complete} or {@link Future#complete(Object)} method, or the {@link Future#fail}
   * method if it failed.
   * <p>
   * In the {@code blockingCodeHandler} the current context remains the original context and therefore any task
   * scheduled in the {@code blockingCodeHandler} will be executed on the this context and not on the worker thread.
   *
   * @param blockingCodeHandler  handler representing the blocking code to run
   * @param resultHandler  handler that will be called when the blocking code is complete
   * @param ordered  if true then if executeBlocking is called several times on the same context, the executions
   *                 for that context will be executed serially, not in parallel. if false then they will be no ordering
   *                 guarantees
   * @param <T> the type of the result
   */
  <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<@Nullable T>> resultHandler);

  /**
   * Like {@link #executeBlocking(Handler, boolean, Handler)} called with ordered = true.
   */
  default <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, Handler<AsyncResult<@Nullable T>> resultHandler) {
    executeBlocking(blockingCodeHandler, true, resultHandler);
  }

  /**
   * Close the executor.
   */
  default void close() {
  }

}
