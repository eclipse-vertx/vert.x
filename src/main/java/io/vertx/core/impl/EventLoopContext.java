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

import io.netty.channel.EventLoop;
import io.vertx.core.Handler;

import java.util.concurrent.RejectedExecutionException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventLoopContext extends ContextImpl {

  EventLoopContext(VertxInternal vertx,
                   EventLoop eventLoop,
                   WorkerPool internalBlockingPool,
                   WorkerPool workerPool,
                   Deployment deployment,
                   CloseFuture closeFuture,
                   ClassLoader tccl) {
    super(vertx, eventLoop, internalBlockingPool, workerPool, deployment, closeFuture, tccl);
  }

  @Override
  void runOnContext(AbstractContext ctx, Handler<Void> action) {
    try {
      nettyEventLoop().execute(() -> ctx.dispatch(action));
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
    }
  }

  @Override
  <T> void emit(AbstractContext ctx, T argument, Handler<T> task) {
    EventLoop eventLoop = nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      ContextInternal prev = ctx.beginDispatch();
      try {
        task.handle(argument);
      } catch (Throwable t) {
        reportException(t);
      } finally {
        ctx.endDispatch(prev);
      }
    } else {
      eventLoop.execute(() -> emit(ctx, argument, task));
    }
  }

  /**
   * <ul>
   *   <li>When the current thread is event-loop thread of this context the implementation will execute the {@code task} directly</li>
   *   <li>Otherwise the task will be scheduled on the event-loop thread for execution</li>
   * </ul>
   */
  @Override
  <T> void execute(AbstractContext ctx, T argument, Handler<T> task) {
    EventLoop eventLoop = nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      task.handle(argument);
    } else {
      eventLoop.execute(() -> task.handle(argument));
    }
  }

  @Override
  <T> void execute(AbstractContext ctx, Runnable task) {
    EventLoop eventLoop = nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      task.run();
    } else {
      eventLoop.execute(task);
    }
  }

  @Override
  public boolean isEventLoopContext() {
    return true;
  }

  @Override
  boolean inThread() {
    return nettyEventLoop().inEventLoop();
  }

}
