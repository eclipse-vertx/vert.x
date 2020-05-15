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

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.PoolMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WorkerExecutorImpl implements MetricsProvider, WorkerExecutorInternal {

  private final VertxInternal vertx;
  private final CloseHooks closeHooks;
  private final VertxImpl.SharedWorkerPool pool;
  private boolean closed;

  public WorkerExecutorImpl(VertxInternal vertx, CloseHooks closeHooks, VertxImpl.SharedWorkerPool pool) {
    this.vertx = vertx;
    this.closeHooks = closeHooks;
    this.pool = pool;
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
  public WorkerPool getPool() {
    return pool;
  }

  @Override
  public <T> Future<@Nullable T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered) {
    if (closed) {
      throw new IllegalStateException("Worker executor closed");
    }
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    ContextImpl impl = context instanceof ContextImpl.Duplicated ? ((ContextImpl.Duplicated)context).delegate : (ContextImpl) context;
    return ContextImpl.executeBlocking(context, blockingCodeHandler, pool, ordered ? impl.orderedTasks : null);
  }

  public synchronized <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> asyncResultHandler) {
    Future<T> fut = executeBlocking(blockingCodeHandler, ordered);
    if (asyncResultHandler != null) {
      fut.onComplete(asyncResultHandler);
    }
  }

  @Override
  public Future<Void> close() {
    PromiseInternal<Void> promise = vertx.promise();
    close(promise);
    return promise.future();
  }

  @Override
  public void close(Promise<Void> completion) {
    synchronized (this) {
      if (!closed) {
        closed = true;
        closeHooks.remove(this);
        pool.release();
      }
    }
    completion.complete();
  }
}
