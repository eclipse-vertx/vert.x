/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
  protected void executeAsync(Handler<Void> task) {
    wrapTask(null, task, true, null).run();
  }

  public void runDirect(Handler<Void> task) {
    wrapTask(null, task, true, null).run();
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }

  @Override
  public boolean isMultiThreadedWorkerContext() {
    return false;
  }

  @Override
  protected void checkCorrectThread() {
  }
}
