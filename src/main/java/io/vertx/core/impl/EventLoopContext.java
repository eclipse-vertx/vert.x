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

import io.netty.channel.EventLoop;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventLoopContext extends ContextImpl {

  private static final Logger log = LoggerFactory.getLogger(EventLoopContext.class);

  EventLoopContext(VertxInternal vertx, WorkerPool internalBlockingPool, WorkerPool workerPool, Deployment deployment,
                          ClassLoader tccl) {
    super(vertx, internalBlockingPool, workerPool, deployment, tccl);
  }

  public EventLoopContext(VertxInternal vertx, EventLoop eventLoop, WorkerPool internalBlockingPool, WorkerPool workerPool, Deployment deployment,
                          ClassLoader tccl) {
    super(vertx, eventLoop, internalBlockingPool, workerPool, deployment, tccl);
  }

  void executeAsync(Handler<Void> task) {
    nettyEventLoop().execute(() -> dispatch(null, task));
  }

  @Override
  public <T> void schedule(T value, Handler<T> task) {
    task.handle(value);
  }

  @Override
  <T> void execute(T value, Handler<T> task) {
    dispatch(value, task);
  }

  @Override
  public boolean isEventLoopContext() {
    return true;
  }

}
