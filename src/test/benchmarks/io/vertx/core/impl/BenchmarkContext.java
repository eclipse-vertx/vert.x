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
    return new BenchmarkContext(
      impl,
      impl.internalWorkerPool,
      impl.workerPool,
      Thread.currentThread().getContextClassLoader()
    );
  }

  public BenchmarkContext(VertxInternal vertx, WorkerPool internalBlockingPool, WorkerPool workerPool, ClassLoader tccl) {
    super(vertx, vertx.getEventLoopGroup().next(), internalBlockingPool, workerPool, null, null, tccl);
  }

  @Override
  boolean inThread() {
    throw new UnsupportedOperationException();
  }

  @Override
  <T> void emit(AbstractContext ctx, T argument, Handler<T> task) {
    throw new UnsupportedOperationException();
  }

  @Override
  void runOnContext(AbstractContext ctx, Handler<Void> action) {
    ctx.dispatch(null, action);
  }

  @Override
  <T> void execute(AbstractContext ctx, T argument, Handler<T> task) {
    task.handle(argument);
  }

  @Override
  <T> void execute(AbstractContext ctx, Runnable task) {
    task.run();
  }

  @Override
  public void execute(Runnable task) {
    task.run();
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }

}
