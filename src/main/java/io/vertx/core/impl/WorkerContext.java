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

import io.netty.channel.EventLoopGroup;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WorkerContext extends ContextBase {

  WorkerContext(VertxInternal vertx,
                WorkerPool internalBlockingPool,
                WorkerPool workerPool,
                Deployment deployment,
                CloseFuture closeFuture,
                ClassLoader tccl) {
    super(vertx, vertx.<EventLoopGroup>getEventLoopGroup().next(), internalBlockingPool, workerPool, deployment, closeFuture, tccl);
  }

  @Override
  protected void runOnContext(ContextInternal ctx, Handler<Void> action) {
    try {
      run(ctx, null, action);
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
  protected <T> void execute(ContextInternal ctx, T argument, Handler<T> task) {
    execute(orderedTasks, argument, task);
  }

  @Override
  protected <T> void emit(ContextInternal ctx, T argument, Handler<T> task) {
    execute(orderedTasks, argument, arg -> {
      ctx.dispatch(arg, task);
    });
  }

  @Override
  protected <T> void execute(ContextInternal ctx, Runnable task) {
    execute(this, task, Runnable::run);
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }

  @Override
  public boolean isWorkerContext() {
    return true;
  }

  private Executor executor;

  @Override
  public Executor executor() {
    if (executor == null) {
      executor = command -> {
        PoolMetrics metrics = workerPool.metrics();
        Object queueMetric = metrics != null ? metrics.submitted() : null;
        orderedTasks.execute(() -> {
          Object execMetric = null;
          if (metrics != null) {
            execMetric = metrics.begin(queueMetric);
          }
          try {
            command.run();
          } finally {
            if (metrics != null) {
              metrics.end(execMetric, true);
            }
          }
        }, workerPool.executor());
      };
    }
    return executor;
  }

  private <T> void run(ContextInternal ctx, T value, Handler<T> task) {
    Objects.requireNonNull(task, "Task handler must not be null");
    executor().execute(() -> ctx.dispatch(value, task));
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
  public boolean inThread() {
    return Context.isOnWorkerThread();
  }
}
