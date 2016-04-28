/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.spi.metrics.ThreadPoolMetrics;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WorkerPool {

  protected final ThreadPoolMetrics workerMetrics;
  protected final ThreadPoolMetrics internalBlockingPoolMetrics;
  protected final Executor orderedInternalPoolExec;
  protected final Executor workerExec;
  protected final Executor workerPool;

  WorkerPool(Executor orderedInternalPoolExec, Executor workerExec, Executor workerPool,
                    ThreadPoolMetrics internalBlockingPoolMetrics, ThreadPoolMetrics workerMetrics) {
    this.workerMetrics = workerMetrics;
    this.internalBlockingPoolMetrics = internalBlockingPoolMetrics;
    this.orderedInternalPoolExec = orderedInternalPoolExec;
    this.workerExec = workerExec;
    this.workerPool = workerPool;
  }

  <T> void executeBlocking(ContextImpl context, Action<T> action, Handler<Future<T>> blockingCodeHandler, boolean internal,
                                   boolean ordered, Handler<AsyncResult<T>> resultHandler) {
    ThreadPoolMetrics metrics = internal ? internalBlockingPoolMetrics : workerMetrics;
    Object metric = metrics != null ? metrics.taskSubmitted() : null;
    try {
      Executor exec = internal ? orderedInternalPoolExec : (ordered ? workerExec : workerPool);
      exec.execute(() -> {
        if (metrics != null) {
          metrics.taskExecuting(metric);
        }
        Future<T> res = Future.future();
        try {
          if (blockingCodeHandler != null) {
            ContextImpl.setContext(context);
            blockingCodeHandler.handle(res);
          } else {
            T result = action.perform();
            res.complete(result);
          }
        } catch (Throwable e) {
          res.fail(e);
        }
        if (metrics != null) {
          metrics.taskCompleted(metric, res.succeeded());
        }
        if (resultHandler != null) {
          context.runOnContext(v -> res.setHandler(resultHandler));
        }
      });
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
      if (metrics != null) {
        metrics.taskRejected(metric);
      }
    }
  }
}
