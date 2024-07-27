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

package io.vertx.core.internal;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Executor service + metrics.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class WorkerPool {

  private final ExecutorService executor;
  private final PoolMetrics metrics;

  public WorkerPool(ExecutorService executor, PoolMetrics metrics) {
    this.executor = executor;
    this.metrics = metrics;
  }

  /**
   * @return the bare executor
   */
  public ExecutorService executor() {
    return executor;
  }

  /**
   * @return the metrics
   */
  public PoolMetrics metrics() {
    return metrics;
  }

  /**
   * Close the pool
   */
  public void close() {
    if (metrics != null) {
      metrics.close();
    }
    executor.shutdownNow();
  }

  public <T> Future<T> executeBlocking(
    ContextInternal context,
    Callable<T> blockingCodeHandler,
    TaskQueue queue) {
    return internalExecuteBlocking(context, promise -> {
      T result;
      try {
        result = blockingCodeHandler.call();
      } catch (Throwable e) {
        promise.fail(e);
        return;
      }
      promise.complete(result);
    }, this, queue);
  }

  private static <T> Future<T> internalExecuteBlocking(ContextInternal context, Handler<Promise<T>> blockingCodeHandler,
                                                       WorkerPool workerPool, TaskQueue queue) {
    PoolMetrics metrics = workerPool.metrics();
    Object queueMetric = metrics != null ? metrics.enqueue() : null;
    Promise<T> promise = context.promise();
    Future<T> fut = promise.future();
    try {
      Runnable command = () -> {
        Object execMetric = null;
        if (metrics != null) {
          metrics.dequeue(queueMetric);
          execMetric = metrics.begin();
        }
        context.dispatch(promise, blockingCodeHandler);
        if (metrics != null) {
          metrics.end(execMetric);
        }
      };
      Executor exec = workerPool.executor();
      if (queue != null) {
        queue.execute(command, exec);
      } else {
        exec.execute(command);
      }
    } catch (RejectedExecutionException e) {
      // Pool is already shut down
      if (metrics != null) {
        metrics.dequeue(queueMetric);
      }
      throw e;
    }
    return fut;
  }
}
