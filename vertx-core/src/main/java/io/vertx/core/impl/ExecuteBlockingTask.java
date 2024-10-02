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

import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;

class ExecuteBlockingTask<T> implements Runnable {

  final Promise<T> promise;
  final ContextInternal context;
  final Object queueMetric;
  final PoolMetrics metrics;
  final Callable<T> blockingCodeHandler;

  public ExecuteBlockingTask(Promise<T> promise, ContextInternal context, Object queueMetric, PoolMetrics metrics, Callable<T> blockingCodeHandler) {
    this.promise = promise;
    this.context = context;
    this.queueMetric = queueMetric;
    this.metrics = metrics;
    this.blockingCodeHandler = blockingCodeHandler;
  }

  @Override
  public void run() {
    Object execMetric = null;
    if (metrics != null) {
      metrics.dequeue(queueMetric);
      execMetric = metrics.begin();
    }
    ContextInternal prev = context.beginDispatch();
    T result;
    try {
      result = blockingCodeHandler.call();
    } catch (Throwable t) {
      promise.fail(t);
      return;
    } finally {
      context.endDispatch(prev);
      if (metrics != null) {
        metrics.end(execMetric);
      }
    }
    promise.complete(result);
  }

  void reject() {
    promise.tryFail(new RejectedExecutionException());
    if (metrics != null) {
      metrics.dequeue(queueMetric);
    }
  }
}
