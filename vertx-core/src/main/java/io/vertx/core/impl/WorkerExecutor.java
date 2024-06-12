/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import io.vertx.core.Vertx;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Execute events on a worker pool.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class WorkerExecutor implements EventExecutor {

  public static io.vertx.core.impl.WorkerExecutor unwrapWorkerExecutor() {
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      ctx = ctx.unwrap();
      Executor executor = ctx.executor();
      if (executor instanceof io.vertx.core.impl.WorkerExecutor) {
        return (io.vertx.core.impl.WorkerExecutor) executor;
      } else {
        throw new IllegalStateException("Cannot be called on a Vert.x event-loop thread");
      }
    }
    // Technically it works also for worker threads but we don't want to encourage this
    throw new IllegalStateException("Not running from a Vert.x virtual thread");
  }

  private final WorkerPool workerPool;
  private final TaskQueue orderedTasks;
  private final ThreadLocal<Boolean> inThread = new ThreadLocal<>();

  public WorkerExecutor(WorkerPool workerPool, TaskQueue orderedTasks) {
    this.workerPool = workerPool;
    this.orderedTasks = orderedTasks;
  }

  @Override
  public boolean inThread() {
    return inThread.get() == Boolean.TRUE;
  }

  @Override
  public void execute(Runnable command) {
    PoolMetrics metrics = workerPool.metrics();
    Object queueMetric = metrics != null ? metrics.submitted() : null;
    orderedTasks.execute(() -> {
      Object execMetric = null;
      if (metrics != null) {
        execMetric = metrics.begin(queueMetric);
      }
      try {
        inThread.set(true);
        try {
          command.run();
        } finally {
          inThread.remove();
        }
      } finally {
        if (metrics != null) {
          metrics.end(execMetric, true);
        }
      }
    }, workerPool.executor());
  }

  /**
   * See {@link TaskQueue#current()}.
   */
  public TaskController current() {
    return orderedTasks.current();
  }

  public interface TaskController {

    /**
     * Resume the task, the {@code callback} will be executed when the task is resumed, before the task thread
     * is unparked.
     *
     * @param callback called when the task is resumed
     */
    void resume(Runnable callback);

    /**
     * Like {@link #resume(Runnable)}.
     */
    default void resume() {
      resume(() -> {});
    }

    /**
     * Suspend the task execution and park the current thread until the task is resumed.
     * The next task in the queue will be executed, when there is one.
     *
     * <p>When the task wants to be resumed, it should call {@link #resume}, this will be executed immediately if there
     * is no other tasks being executed, otherwise it will be added first in the queue.
     */
    default void suspendAndAwaitResume() throws InterruptedException {
      suspend().await();
    }

    /**
     * Like {@link #suspendAndAwaitResume()} but does not await the task to be resumed.
     *
     * @return the latch to await
     */
    CountDownLatch suspend();

  }
}
