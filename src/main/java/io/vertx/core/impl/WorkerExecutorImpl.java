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
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.PoolMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WorkerExecutorImpl implements Closeable, MetricsProvider, WorkerExecutorInternal {

  private final Vertx vertx;
  private final WorkerPool pool;
  private boolean closed;
  private final boolean releaseOnClose;

  public WorkerExecutorImpl(Vertx vertx, WorkerPool pool, boolean releaseOnClose) {
    this.vertx = vertx;
    this.pool = pool;
    this.releaseOnClose = releaseOnClose;
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

  public WorkerPool getPool() {
    return pool;
  }

  public synchronized <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> asyncResultHandler) {
    if (closed) {
      throw new IllegalStateException("Worker executor closed");
    }
    ContextImpl context = (ContextImpl) vertx.getOrCreateContext();
    context.executeBlocking(null, blockingCodeHandler, asyncResultHandler, pool.executor(), ordered ? context.orderedTasks : null, pool.metrics());
  }

  @Override
  public void close() {
    synchronized (this) {
      if (!closed) {
        closed = true;
      } else {
        return;
      }
    }
    if (releaseOnClose && pool instanceof VertxImpl.SharedWorkerPool) {
      ((VertxImpl.SharedWorkerPool)pool).release();
    }
  }

  @Override
  public void close(Handler<AsyncResult<Void>> completionHandler) {
    close();
    completionHandler.handle(Future.succeededFuture());
  }

}
