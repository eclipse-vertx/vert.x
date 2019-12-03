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
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.tracing.VertxTracer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventLoopContext extends ContextImpl {

  private static final Logger log = LoggerFactory.getLogger(EventLoopContext.class);

  EventLoopContext(VertxInternal vertx, VertxTracer<?, ?> tracer, WorkerPool internalBlockingPool, WorkerPool workerPool, Deployment deployment,
                   ClassLoader tccl) {
    super(vertx, tracer, internalBlockingPool, workerPool, deployment, tccl);
  }

  public EventLoopContext(VertxInternal vertx, VertxTracer<?, ?> tracer, EventLoop eventLoop, WorkerPool internalBlockingPool, WorkerPool workerPool, Deployment deployment,
                          ClassLoader tccl) {
    super(vertx, tracer, eventLoop, internalBlockingPool, workerPool, deployment, tccl);
  }

  @Override
  <T> void execute(T argument, Handler<T> task) {
    nettyEventLoop().execute(() -> emit(argument, task));
  }

  @Override
  public void execute(Runnable task) {
    nettyEventLoop().execute(() -> emit(task));
  }

  @Override
  public <T> void schedule(T argument, Handler<T> task) {
    task.handle(argument);
  }

  @Override
  public <T> void dispatchFromIO(T argument, Handler<T> task) {
    if (THREAD_CHECKS) {
      checkEventLoopThread();
    }
    emit(argument, task);
  }

  @Override
  public <T> void dispatch(T argument, Handler<T> task) {
    dispatch(this, argument, task);
  }

  private static <T> void dispatch(AbstractContext ctx, T value, Handler<T> task) {
    EventLoop eventLoop = ctx.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      if (AbstractContext.context() == ctx) {
        ctx.emit(value, task);
      } else {
        ctx.dispatchFromIO(value, task);
      }
    } else {
      ctx.execute(value, task);
    }
  }

  @Override
  public boolean isEventLoopContext() {
    return true;
  }

  @Override
  public ContextInternal duplicate(ContextInternal in) {
    return new Duplicated(this, in);
  }

  static class Duplicated extends ContextImpl.Duplicated<EventLoopContext> {

    private TaskQueue orderedTasks;

    Duplicated(EventLoopContext delegate, ContextInternal other) {
      super(delegate, other);
    }

    @Override
    <T> void execute(T argument, Handler<T> task) {
      nettyEventLoop().execute(() -> emit(argument, task));
    }

    @Override
    public void execute(Runnable task) {
      nettyEventLoop().execute(() -> emit(task));
    }

    @Override
    public final <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action) {
      return ContextImpl.executeBlocking(this, action, delegate.internalBlockingPool, delegate.internalOrderedTasks);
    }

    @Override
    public <T> Future<@Nullable T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered) {
      TaskQueue queue;
      if (ordered) {
        queue = null;
      } else {
        synchronized (this) {
          if (orderedTasks == null) {
            orderedTasks = new TaskQueue();
          }
          queue = orderedTasks;
        }
      }
      return ContextImpl.executeBlocking(this, blockingCodeHandler, delegate.workerPool, queue);
    }

    @Override
    public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue) {
      return ContextImpl.executeBlocking(this, blockingCodeHandler, delegate.workerPool, queue);
    }

    @Override
    public <T> void dispatchFromIO(T argument, Handler<T> task) {
      if (THREAD_CHECKS) {
        checkEventLoopThread();
      }
      emit(argument, task);
    }

    @Override
    public <T> void dispatch(T argument, Handler<T> task) {
      EventLoopContext.dispatch(this, argument, task);
    }

    @Override
    public boolean isEventLoopContext() {
      return true;
    }

    @Override
    public ContextInternal duplicate(ContextInternal context) {
      return new Duplicated(delegate, context);
    }
  }
}
