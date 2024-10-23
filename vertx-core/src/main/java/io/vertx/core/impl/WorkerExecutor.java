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

import io.vertx.core.Future;
import io.vertx.core.ThreadingModel;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

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
   * Suspend the current task execution until the task is resumed, the next task in the queue will be executed
   * when there is one.
   *
   * <p>The {@code resumeAcceptor} argument is passed the continuation to resume the current task, this acceptor
   * is guaranteed to be called before the task is actually suspended so the task can be eagerly resumed and avoid
   * suspending the current task.
   *
   * @return the latch to wait for until the current task can resume
   */
  public CountDownLatch suspend(Consumer<Continuation> resumeAcceptor) {
    return orderedTasks.suspend(resumeAcceptor);
  }

  public interface Continuation {

    /**
     * Resume the task, the {@code callback} will be executed when the task is resumed, before the task thread
     * is un-parked.
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
  }
}
