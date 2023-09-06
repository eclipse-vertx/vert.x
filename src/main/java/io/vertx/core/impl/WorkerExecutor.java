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

import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.function.Consumer;

/**
 * Execute events on a worker pool.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class WorkerExecutor implements EventExecutor {

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
   * See {@link TaskQueue#unschedule()}.
   */
  public Consumer<Runnable> unschedule() {
    return orderedTasks.unschedule();
  }
}
