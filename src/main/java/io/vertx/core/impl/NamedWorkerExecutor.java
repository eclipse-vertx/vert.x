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
import io.vertx.core.WorkerExecutor;

import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class NamedWorkerExecutor implements WorkerExecutor, Closeable {

  private final ContextImpl context;
  final VertxImpl.SharedWorkerPool pool;
  private boolean closed;
  private final Executor workerExec;

  public NamedWorkerExecutor(ContextImpl context, VertxImpl.SharedWorkerPool pool) {
    this.pool = pool;
    this.context = context;
    this.workerExec = pool.createOrderedExecutor();
  }

  public WorkerPool getPool() {
    return pool;
  }

  public synchronized <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> asyncResultHandler) {
    if (closed) {
      throw new IllegalStateException("Worker executor closed");
    }
    context.executeBlocking(null, blockingCodeHandler, asyncResultHandler, ordered ? workerExec : pool.executor(), pool.metrics());
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
    pool.release();
  }

  @Override
  public void close(Handler<AsyncResult<Void>> completionHandler) {
    close();
    completionHandler.handle(Future.succeededFuture());
  }

}
