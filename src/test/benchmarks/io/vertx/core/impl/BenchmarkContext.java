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

import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class BenchmarkContext extends ContextImpl {

  public static BenchmarkContext create(Vertx vertx) {
    VertxImpl impl = (VertxImpl) vertx;
    return new BenchmarkContext(impl, impl.internalBlockingPool, impl.workerPool, null, Thread.currentThread().getContextClassLoader());
  }

  public BenchmarkContext(VertxInternal vertx, WorkerPool internalBlockingPool, WorkerPool workerPool, Deployment deployment, ClassLoader tccl) {
    super(vertx, null, internalBlockingPool, workerPool, deployment, tccl);
  }

  @Override
  <T> void execute(T argument, Handler<T> task) {
    emitFromIO(argument, task);
  }

  @Override
  public void execute(Runnable task) {
    if (THREAD_CHECKS) {
      checkEventLoopThread();
    }
    dispatch(task);
  }

  @Override
  public <T> void emitFromIO(T event, Handler<T> handler) {
    if (THREAD_CHECKS) {
      checkEventLoopThread();
    }
    dispatch(event, handler);
  }

  @Override
  public <T> void schedule(T value, Handler<T> task) {
    task.handle(value);
  }

  @Override
  public <T> void emit(T event, Handler<T> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }

  @Override
  public ContextInternal duplicate(ContextInternal in) {
    throw new UnsupportedOperationException();
  }
}
