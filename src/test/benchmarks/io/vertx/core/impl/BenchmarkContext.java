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

import io.netty.channel.EventLoopGroup;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class BenchmarkContext extends ContextBase {

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
    super(vertx, vertx.<EventLoopGroup>getEventLoopGroup().next(), internalBlockingPool, workerPool, null, null, tccl);
  }

  @Override
  public Executor executor() {
    return Runnable::run;
  }

  @Override
  public boolean inThread() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected <T> void emit(ContextInternal ctx, T argument, Handler<T> task) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void runOnContext(ContextInternal ctx, Handler<Void> action) {
    ctx.dispatch(null, action);
  }

  @Override
  protected <T> void execute(ContextInternal ctx, T argument, Handler<T> task) {
    task.handle(argument);
  }

  @Override
  protected void execute(ContextInternal ctx, Runnable task) {
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

  @Override
  public boolean isWorkerContext() {
    return false;
  }
}
