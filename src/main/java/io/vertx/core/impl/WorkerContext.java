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

package io.vertx.core.impl;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WorkerContext extends ContextImpl {

  WorkerContext(VertxInternal vertx,
                WorkerPool internalBlockingPool,
                WorkerPool workerPool,
                Deployment deployment,
                CloseFuture closeFuture,
                ClassLoader tccl) {
    super(vertx, vertx.getEventLoopGroup().next(), internalBlockingPool, workerPool, deployment, closeFuture, tccl);
  }

  @Override
  void runOnContext(AbstractContext ctx, Handler<Void> action) {
    try {
      run(ctx, orderedTasks, null, action);
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
    }
  }

  /**
   * <ul>
   *   <li>When the current thread is a worker thread of this context the implementation will execute the {@code task} directly</li>
   *   <li>Otherwise the task will be scheduled on the worker thread for execution</li>
   * </ul>
   */
  @Override
  <T> void execute(AbstractContext ctx, T argument, Handler<T> task) {
    execute(orderedTasks, argument, task);
  }

  @Override
  <T> void emit(AbstractContext ctx, T argument, Handler<T> task) {
    execute(orderedTasks, argument, arg -> {
      ctx.dispatch(arg, task);
    });
  }

  @Override
  <T> void execute(AbstractContext ctx, Runnable task) {
    execute(this, task, Runnable::run);
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }

  private <T> void run(ContextInternal ctx, TaskQueue queue, T value, Handler<T> task) {
    Objects.requireNonNull(task, "Task handler must not be null");
    PoolMetrics metrics = workerPool.metrics();
    Object queueMetric = metrics != null ? metrics.submitted() : null;
    queue.execute(() -> {
      Object execMetric = null;
      if (metrics != null) {
        execMetric = metrics.begin(queueMetric);
      }
      try {
        ctx.dispatch(value, task);
      } finally {
        if (metrics != null) {
          metrics.end(execMetric, true);
        }
      }
    }, workerPool.executor());
  }

  private <T> void execute(TaskQueue queue, T argument, Handler<T> task) {
    if (Context.isOnWorkerThread()) {
      task.handle(argument);
    } else {
      PoolMetrics metrics = workerPool.metrics();
      Object queueMetric = metrics != null ? metrics.submitted() : null;
      queue.execute(() -> {
        Object execMetric = null;
        if (metrics != null) {
          execMetric = metrics.begin(queueMetric);
        }
        try {
          task.handle(argument);
        } finally {
          if (metrics != null) {
            metrics.end(execMetric, true);
          }
        }
      }, workerPool.executor());
    }
  }

  @Override
  boolean inThread() {
    return Context.isOnWorkerThread();
  }
}
