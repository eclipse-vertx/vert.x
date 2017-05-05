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

import io.vertx.core.*;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
// TODO: 16/12/15 by zmyer
class WorkerExecutorImpl implements WorkerExecutor, Closeable, MetricsProvider {
    //执行上下文对象
    private final ContextImpl context;
    //工作对象池
    final WorkerPool pool;
    //关闭标记
    private boolean closed;
    //工作线程对象
    private final Executor workerExec;
    //释放标记
    private final boolean releaseOnClose;

    // TODO: 16/12/15 by zmyer
    public WorkerExecutorImpl(ContextImpl context, WorkerPool pool, boolean releaseOnClose) {
        this.pool = pool;
        this.context = context;
        this.workerExec = pool.createOrderedExecutor();
        this.releaseOnClose = releaseOnClose;
    }

    @Override
    public Metrics getMetrics() {
        return pool.metrics();
    }

    @Override
    public boolean isMetricsEnabled() {
        PoolMetrics metrics = pool.metrics();
        return metrics != null && metrics.isEnabled();
    }

    public WorkerPool getPool() {
        return pool;
    }

    // TODO: 16/12/15 by zmyer
    public synchronized <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> asyncResultHandler) {
        if (closed) {
            throw new IllegalStateException("Worker executor closed");
        }
        //在执行上下文中执行给定的处理对象
        context.executeBlocking(null, blockingCodeHandler, asyncResultHandler, ordered ? workerExec : pool.executor(), pool.metrics());
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public void close() {
        synchronized (this) {
            if (!closed) {
                closed = true;
            } else {
                return;
            }
        }
        //如果开启了释放标记,则需要及时释放对应的工作对象池
        if (releaseOnClose && pool instanceof VertxImpl.SharedWorkerPool) {
            ((VertxImpl.SharedWorkerPool) pool).release();
        }
    }

    // TODO: 16/12/15 by zmyer
    @Override
    public void close(Handler<AsyncResult<Void>> completionHandler) {
        close();
        completionHandler.handle(Future.succeededFuture());
    }

}
