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
package io.vertx.core.logic;

import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;


/**
 * Asynchronous repeatedly call task.
 * <p>
 * Contains an asynchronous loop body that executes repeatedly at 1 millisecond intervals.
 * At the end of each task loop iteration, the task stop flag is checked to determine
 * whether to end the loop. If an exception is thrown during loop execution causing
 * an asynchronous failure, the loop is forcibly terminated and the root cause exception
 * is propagated.
 *
 * @see #start(Vertx, RepeatedlyCallTask, Promise)
 * @see #stop()
 * @author Sinri Edogawa
 */
public final class RepeatedlyCallTask {
  private final Function<RepeatedlyCallTask, Future<Void>> processor;
  private volatile boolean toStop = false;

  /**
   * Creates a new repeatedly call task with the given processor function.
   *
   * @param processor the function that will be called repeatedly until the task is stopped
   */
  public RepeatedlyCallTask(Function<RepeatedlyCallTask, Future<Void>> processor) {
    this.processor = processor;
  }

  /**
   * Starts the repeatedly call task execution.
   * <p>
   * This static method initiates the asynchronous loop, calling the task's processor
   * function repeatedly at 1 millisecond intervals until the task is stopped or
   * an error occurs.
   *
   * @param vertx        the Vert.x instance
   * @param thisTask     the repeatedly call task to start
   * @param finalPromise the promise that will be completed when the task finishes
   *                     (either successfully or with failure)
   */
  public static void start(Vertx vertx, RepeatedlyCallTask thisTask, Promise<Void> finalPromise) {
    Future.succeededFuture()
          .compose(v -> {
            if (thisTask.toStop) {
              return Future.succeededFuture();
            }
            return thisTask.processor.apply(thisTask);
          })
          .andThen(shouldStopAR -> {
            if (shouldStopAR.succeeded()) {
              if (thisTask.toStop) {
                finalPromise.complete();
              } else {
                vertx.setTimer(1L, x -> start(vertx, thisTask, finalPromise));
              }
            } else {
              finalPromise.fail(shouldStopAR.cause());
            }
          });
  }

  /**
   * Stops the repeatedly call task.
   * <p>
   * Sets the stop flag, which will cause the loop to terminate after the current
   * iteration completes. The task will not execute further iterations after this
   * method is called.
   */
  public void stop() {
    toStop = true;
  }
}
