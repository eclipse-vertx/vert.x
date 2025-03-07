/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for context worker tasks.
 *
 * The cancellation / after task uses an atomic integer to avoid race when executing the optional cancel continuation
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class WorkerTask extends AtomicInteger implements Runnable {

  private final PoolMetrics metrics;
  private final Object queueMetric;
  private Runnable onComplete;

  public WorkerTask(PoolMetrics metrics, Object queueMetric) {
    this.metrics = metrics;
    this.queueMetric = queueMetric;
  }

  /**
   * Set a continuation to be execution on task completion.
   *
   * @param continuation the command execute when the task completes
   */
  void onCompletion(Runnable continuation) {
    // Rely on atomic integer happens-before to publish waiter
    onComplete = continuation;
    if (addAndGet(1) > 1) {
      continuation.run();
    }
  }

  @Override
  public void run() {
    Object execMetric = null;
    if (metrics != null) {
      metrics.dequeue(queueMetric);
      execMetric = metrics.begin();
    }
    try {
      try {
        execute();
      } finally {
        if (addAndGet(1) > 1) {
          // > 1 => onComplete continuation needs to be executed
          Runnable cont = onComplete;
          cont.run();
        }
      }
    } finally {
      if (metrics != null) {
        metrics.end(execMetric);
      }
    }
  }

  /**
   * Reject the task.
   */
  public void reject() {
  }

  protected abstract void execute();

}
