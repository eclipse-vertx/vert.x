/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class BenchmarkContext extends ContextImpl {

  public static BenchmarkContext create(Vertx vertx) {
    VertxImpl impl = (VertxImpl) vertx;
    return new BenchmarkContext(impl, impl.internalBlockingPool, impl.workerPool, null, null, Thread.currentThread().getContextClassLoader());
  }

  public BenchmarkContext(VertxInternal vertx, WorkerPool internalBlockingPool, WorkerPool workerPool, String deploymentID, JsonObject config, ClassLoader tccl) {
    super(vertx, internalBlockingPool, workerPool, deploymentID, config, tccl);
  }

  @Override
  void executeAsync(Handler<Void> task) {
    execute(null, task);
  }

  @Override
  protected <T> void execute(T value, Handler<T> task) {
    dispatch(value, task);
  }

  @Override
  public <T> void schedule(T value, Handler<T> task) {
    task.handle(value);
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }
}
