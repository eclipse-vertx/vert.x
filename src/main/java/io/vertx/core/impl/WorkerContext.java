/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.metrics.PoolMetrics;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class WorkerContext extends ContextImpl {

  WorkerContext(VertxInternal vertx, WorkerPool internalBlockingPool, WorkerPool workerPool, String deploymentID,
                       JsonObject config, ClassLoader tccl) {
    super(vertx, internalBlockingPool, workerPool, deploymentID, config, tccl);
  }

  final <T> Runnable wrapTask(T arg, Handler<T> hTask, PoolMetrics metrics) {
    Object metric = metrics != null ? metrics.submitted() : null;
    return () -> {
      if (metrics != null) {
        metrics.begin(metric);
      }
      boolean succeeded = executeTask(arg, hTask);
      if (metrics != null) {
        metrics.end(metric, succeeded);
      }
    };
  }

  @Override
  void executeAsync(Handler<Void> task) {
    execute(null, task);
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }

  @Override
  public boolean isMultiThreadedWorkerContext() {
    return false;
  }

  // In the case of a worker context, the IO will always be provided on an event loop thread, not a worker thread
  // so we need to execute it on the worker thread
  @Override
  <T> void execute(T value, Handler<T> task) {
    orderedTasks.execute(wrapTask(value, task, workerPool.metrics()), workerPool.executor());
  }
}
