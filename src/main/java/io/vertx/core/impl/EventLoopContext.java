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
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.NamedThreadPoolFactory;
import io.vertx.core.spi.metrics.ThreadPoolMetrics;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventLoopContext extends ContextImpl {

  public EventLoopContext(VertxInternal vertx, Executor internalBlockingExec, Executor workerExec, String deploymentID, JsonObject config,
                          ClassLoader tccl, NamedThreadPoolManager namedThreadPools,
                          Map<String, ThreadPoolMetrics> poolMetrics) {
    super(vertx, internalBlockingExec, workerExec, deploymentID, config, tccl, namedThreadPools,
        poolMetrics);
  }

  public void executeAsync(Handler<Void> task) {
    // No metrics, we are on the event loop.
    nettyEventLoop().execute(wrapTask(null, task, true, null));
  }

  @Override
  public boolean isEventLoopContext() {
    return true;
  }

  @Override
  public boolean isMultiThreadedWorkerContext() {
    return false;
  }

  @Override
  protected void checkCorrectThread() {
    Thread current = Thread.currentThread();
    if (!(current instanceof VertxThread)) {
      throw new IllegalStateException("Expected to be on Vert.x thread, but actually on: " + current);
    } else if (contextThread != null && current != contextThread) {
      throw new IllegalStateException("Event delivered on unexpected thread " + current + " expected: " + contextThread);
    }
  }
  
}
