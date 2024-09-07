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

import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Execute events on a worker pool.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class WorkerExecutor implements EventExecutor {

  public static io.vertx.core.impl.WorkerExecutor unwrapWorkerExecutor() {
    Thread thread = Thread.currentThread();
    if (thread instanceof VertxThread) {
      VertxThread vertxThread = (VertxThread) thread;
      String msg = vertxThread.isWorker() ? "Cannot be called on a Vert.x worker thread" :
        "Cannot be called on a Vert.x event-loop thread";
      throw new IllegalStateException(msg);
    }
    ContextInternal ctx = VertxImpl.currentContext(thread);
    if (ctx != null && ctx.threadingModel() == ThreadingModel.VIRTUAL_THREAD) {
      return (io.vertx.core.impl.WorkerExecutor) ctx.executor();
    } else {
      return null;
    }
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
    Object queueMetric = metrics != null ? metrics.enqueue() : null;
    orderedTasks.execute(() -> {
      Object execMetric = null;
      if (metrics != null) {
        metrics.dequeue(queueMetric);
        execMetric = metrics.begin();
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
          metrics.end(execMetric);
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
