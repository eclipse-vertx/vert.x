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

import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.WorkerPool;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.concurrent.CountDownLatch;

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
    if (ctx != null && ctx.inThread()) {
      // It can only be a Vert.x virtual thread
      return (io.vertx.core.impl.WorkerExecutor) ctx.executor();
    } else {
      return null;
    }
  }

  private final WorkerPool workerPool;
  private final WorkerTaskQueue orderedTasks;
  private final ThreadLocal<Boolean> inThread = new ThreadLocal<>();

  public WorkerExecutor(WorkerPool workerPool, WorkerTaskQueue orderedTasks) {
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
    // Todo : collapse WorkerTask with context submitted task object
    WorkerTask task = new WorkerTask(metrics, queueMetric) {
      @Override
      protected void execute() {
        inThread.set(true);
        try {
          command.run();
        } finally {
          inThread.remove();
        }
      }
    };
    orderedTasks.execute(task, workerPool.executor());
  }

  WorkerTaskQueue taskQueue() {
    return orderedTasks;
  }

  /**
   * Get the current execution of a task, so it can be suspended.
   *
   * @return the current task
   */
  public Execution currentExecution() {
    return orderedTasks.current();
  }

  public interface Execution {

    /**
     * Try to suspend the execution.
     *
     * <ul>
     *  <li>When the operation succeeds, any queued task will be executed and a latch is returned, this latch should
     *  be used to park the thread until it can resume the task.</li>
     *  <li>When the operation fails, {@code null} is returned, it means that the task was already resumed.</li>
     * </ul>
     *
     * @return the latch to wait for until the current task can resume or {@code null}
     */
    CountDownLatch trySuspend();

    /**
     * Like {@link #resume(Runnable)}.
     */
    void resume();

    /**
     * Resume the task, the {@code callback} will be executed when the task is resumed, before the task thread
     * is un-parked.
     *
     * @param callback called after the task is resumed
     */
    void resume(Runnable callback);

  }
}
