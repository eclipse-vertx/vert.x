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

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class WorkerPool {

  private final ExecutorService pool;
  private final PoolMetrics metrics;

  public WorkerPool(ExecutorService pool, PoolMetrics metrics) {
    this.pool = pool;
    this.metrics = metrics;
  }

  public ExecutorService executor() {
    return pool;
  }

  public PoolMetrics metrics() {
    return metrics;
  }

  public void close() {
    if (metrics != null) {
      metrics.close();
    }
    pool.shutdownNow();
  }

  public <T> Future<T> executeBlocking(ContextInternal context, Callable<T> blockingCodeHandler, TaskQueue queue) {
    Promise<T> promise = context.promise();
    Future<T> fut = promise.future();
    Object queueMetric = metrics != null ? metrics.enqueue() : null;
    WorkerTask task = new WorkerTask(metrics, queueMetric) {
      @Override
      protected void execute() {
        ContextInternal prev = context.beginDispatch();
        T result;
        try {
          result = blockingCodeHandler.call();
        } catch (Throwable t) {
          promise.fail(t);
          return;
        } finally {
          context.endDispatch(prev);
        }
        promise.complete(result);
      }
      @Override
      void reject() {
        if (metrics != null) {
          metrics.dequeue(queueMetric);
        }
        promise.fail(new RejectedExecutionException());
      }
    };
    try {
      Executor exec = executor();
      if (queue != null) {
        queue.execute(task, exec);
      } else {
        exec.execute(task);
      }
    } catch (RejectedExecutionException e) {
      // Pool is already shut down
      task.reject();
      throw e;
    }
    return fut;
  }
}
