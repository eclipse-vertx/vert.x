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
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.RejectedExecutionException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventLoopContext extends ContextImpl {

  EventLoopContext(VertxInternal vertx,
                   VertxTracer<?, ?> tracer,
                   EventLoop eventLoop,
                   WorkerPool internalBlockingPool,
                   WorkerPool workerPool,
                   Deployment deployment,
                   CloseHooks closeHooks,
                   ClassLoader tccl) {
    super(vertx, tracer, eventLoop, internalBlockingPool, workerPool, deployment, closeHooks, tccl);
  }

  @Override
  public void runOnContext(Handler<Void> action) {
    try {
      nettyEventLoop().execute(() -> dispatch(action));
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
    }
  }

  /**
   * {@inheritDoc}
   *
   * <ul>
   *   <li>When the current thread is event-loop thread of this context the implementation will execute the {@code task} directly</li>
   *   <li>Otherwise the task will be scheduled on the event-loop thread for execution</li>
   * </ul>
   */
  @Override
  public <T> void execute(T argument, Handler<T> task) {
    EventLoop eventLoop = nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      task.handle(argument);
    } else {
      eventLoop.execute(() -> task.handle(argument));
    }
  }

  @Override
  public boolean isRunningOnContext() {
    return Vertx.currentContext() == this && nettyEventLoop().inEventLoop();
  }

  @Override
  public void execute(Runnable task) {
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
  public ContextInternal duplicate() {
    return new Duplicated(this);
  }

  static class Duplicated extends ContextImpl.Duplicated<EventLoopContext> {

    private TaskQueue orderedTasks;

    Duplicated(EventLoopContext delegate) {
      super(delegate);
    }

    @Override
    public CloseHooks closeHooks() {
      return delegate.closeHooks();
    }

    @Override
    public void runOnContext(Handler<Void> action) {
      try {
        nettyEventLoop().execute(() -> dispatch(action));
      } catch (RejectedExecutionException ignore) {
        // Pool is already shut down
      }
    }

    @Override
    public boolean isRunningOnContext() {
      return Vertx.currentContext() == this && nettyEventLoop().inEventLoop();
    }

    @Override
    public final <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action) {
      return ContextImpl.executeBlocking(this, action, delegate.internalBlockingPool, delegate.internalOrderedTasks);
    }

    @Override
    public <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action, boolean ordered) {
      return ContextImpl.executeBlocking(this, action, delegate.internalBlockingPool, ordered ? delegate.internalOrderedTasks : null);
    }

    @Override
    public <T> Future<@Nullable T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered) {
      TaskQueue queue;
      if (ordered) {
        synchronized (this) {
          if (orderedTasks == null) {
            orderedTasks = new TaskQueue();
          }
          queue = orderedTasks;
        }
      } else {
        queue = null;
      }
      return ContextImpl.executeBlocking(this, blockingCodeHandler, delegate.workerPool, queue);
    }

    @Override
    public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue) {
      return ContextImpl.executeBlocking(this, blockingCodeHandler, delegate.workerPool, queue);
    }

    @Override
    public <T> void execute(T argument, Handler<T> task) {
      delegate.execute(argument, task);
    }

    @Override
    public void execute(Runnable task) {
      delegate.execute(task);
    }

    @Override
    public boolean isEventLoopContext() {
      return true;
    }

    @Override
    public ContextInternal duplicate() {
      return new Duplicated(delegate);
    }
  }
}
