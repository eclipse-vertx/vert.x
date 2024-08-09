/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.WorkerExecutorInternal;
import io.vertx.core.internal.WorkerPool;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.lang.ref.Cleaner;
import java.util.concurrent.Callable;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WorkerExecutorImpl implements MetricsProvider, WorkerExecutorInternal {

  private final VertxInternal vertx;
  private final WorkerPool pool;
  private final Cleaner.Cleanable cleanable;

  public WorkerExecutorImpl(VertxInternal vertx, Cleaner cleaner, WorkerPool pool) {
    this.vertx = vertx;
    this.pool = pool;
    this.cleanable = cleaner.register(this, pool::close);
  }

  @Override
  public Metrics getMetrics() {
    return pool.metrics();
  }

  @Override
  public boolean isMetricsEnabled() {
    PoolMetrics metrics = pool.metrics();
    return metrics != null;
  }

  @Override
  public Vertx vertx() {
    return vertx;
  }

  @Override
  public WorkerPool pool() {
    return pool;
  }

  @Override
  public <T> Future<@Nullable T> executeBlocking(Callable<T> blockingCodeHandler, boolean ordered) {
    ContextInternal context = vertx.getOrCreateContext();
    TaskQueue orderedTasks;
    if (ordered) {
      if (context instanceof ShadowContext) {
        orderedTasks = ((ShadowContext)context).orderedTasks;
      } else {
        ContextImpl impl = context instanceof DuplicatedContext ? ((DuplicatedContext)context).delegate : (ContextImpl) context;
        orderedTasks = impl.executeBlockingTasks;
      }
    } else {
      orderedTasks = null;
    }
    return ExecuteBlocking.executeBlocking(pool, context, blockingCodeHandler, orderedTasks);
  }

  @Override
  public Future<Void> close() {
    cleanable.clean();
    return vertx.getOrCreateContext().succeededFuture();
  }
}
