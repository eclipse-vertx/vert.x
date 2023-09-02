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
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.concurrent.Callable;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WorkerExecutorImpl implements MetricsProvider, WorkerExecutorInternal {

  private final VertxInternal vertx;
  private final CloseFuture closeFuture;
  private final VertxImpl.SharedWorkerPool pool;
  private boolean closed;

  public WorkerExecutorImpl(VertxInternal vertx, CloseFuture closeFuture, VertxImpl.SharedWorkerPool pool) {
    this.vertx = vertx;
    this.closeFuture = closeFuture;
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
    synchronized (this) {
      if (closed) {
        throw new IllegalStateException("Worker executor closed");
      }
    }
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    ContextImpl impl = context instanceof DuplicatedContext ? ((DuplicatedContext)context).delegate : (ContextImpl) context;
    return ContextImpl.executeBlocking(context, blockingCodeHandler, pool, ordered ? impl.orderedTasks : null);
  }

  @Override
  public <T> Future<@Nullable T> executeBlocking(Callable<T> blockingCodeHandler, boolean ordered) {
    ContextInternal context = vertx.getOrCreateContext();
    ContextImpl impl = context instanceof DuplicatedContext ? ((DuplicatedContext)context).delegate : (ContextImpl) context;
    return ContextImpl.executeBlocking(context, blockingCodeHandler, pool, ordered ? impl.orderedTasks : null);
  }

  public <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> asyncResultHandler) {
    Future<T> fut = executeBlocking(blockingCodeHandler, ordered);
    if (asyncResultHandler != null) {
      fut.onComplete(asyncResultHandler);
    }
  }

  @Override
  public Future<Void> close() {
    ContextInternal closingCtx = vertx.getOrCreateContext();
    PromiseInternal<Void> promise = closingCtx.promise();
    closeFuture.close(promise);
    return promise.future();
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    ContextInternal closingCtx = vertx.getOrCreateContext();
    closeFuture.close(handler != null ? closingCtx.promise(handler) : null);
  }

  @Override
  public void close(Promise<Void> completion) {
    synchronized (this) {
      closed = true;
    }
    pool.close();
    completion.complete();
  }
}
