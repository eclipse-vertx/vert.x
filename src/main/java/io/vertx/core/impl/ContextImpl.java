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
import io.vertx.core.*;
import io.vertx.core.Future;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.metrics.PoolMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.*;

/**
 * A base class for {@link Context} implementations.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class ContextImpl implements ContextInternal {

  static <T> void setResultHandler(ContextInternal ctx, Future<T> fut, Handler<AsyncResult<T>> resultHandler) {
    if (resultHandler != null) {
      fut.onComplete(resultHandler);
    } else {
      fut.onFailure(ctx::reportException);
    }
  }

  private static final Logger log = LoggerFactory.getLogger(ContextImpl.class);

  private static final String DISABLE_TIMINGS_PROP_NAME = "vertx.disableContextTimings";
  static final boolean DISABLE_TIMINGS = Boolean.getBoolean(DISABLE_TIMINGS_PROP_NAME);

  private final boolean isEventLoop;
  private final VertxInternal owner;
  private final JsonObject config;
  private final Deployment deployment;
  private final CloseFuture closeFuture;
  private final ClassLoader tccl;
  private final EventLoop eventLoop;
  private final EventExecutor executor;
  private ConcurrentMap<Object, Object> data;
  private ConcurrentMap<Object, Object> localData;
  private volatile Handler<Throwable> exceptionHandler;
  final TaskQueue internalOrderedTasks;
  final WorkerPool internalWorkerPool;
  final WorkerPool workerPool;
  final TaskQueue orderedTasks;

  public ContextImpl(VertxInternal vertx,
                     boolean isEventLoop,
                     EventLoop eventLoop,
                     EventExecutor executor,
                     WorkerPool internalWorkerPool,
                     WorkerPool workerPool,
                     TaskQueue orderedTasks,
                     Deployment deployment,
                     CloseFuture closeFuture,
                     ClassLoader tccl) {
    this.isEventLoop = isEventLoop;
    this.deployment = deployment;
    this.config = deployment != null ? deployment.config() : new JsonObject();
    this.eventLoop = eventLoop;
    this.executor = executor;
    this.tccl = tccl;
    this.owner = vertx;
    this.workerPool = workerPool;
    this.closeFuture = closeFuture;
    this.internalWorkerPool = internalWorkerPool;
    this.orderedTasks = orderedTasks;
    this.internalOrderedTasks = new TaskQueue();
  }

  public Deployment getDeployment() {
    return deployment;
  }

  @Override
  public CloseFuture closeFuture() {
    return closeFuture;
  }

  @Override
  public JsonObject config() {
    return config;
  }

  public EventLoop nettyEventLoop() {
    return eventLoop;
  }

  public VertxInternal owner() {
    return owner;
  }

  @Override
  public <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action) {
    return executeBlocking(this, action, internalWorkerPool, internalOrderedTasks);
  }

  @Override
  public <T> Future<T> executeBlockingInternal(Callable<T> action) {
    return executeBlocking(this, action, internalWorkerPool, internalOrderedTasks);
  }

  @Override
  public <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action, boolean ordered) {
    return executeBlocking(this, action, internalWorkerPool, ordered ? internalOrderedTasks : null);
  }

  @Override
  public <T> Future<T> executeBlockingInternal(Callable<T> action, boolean ordered) {
    return executeBlocking(this, action, internalWorkerPool, ordered ? internalOrderedTasks : null);
  }

  @Override
  public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered) {
    return executeBlocking(this, blockingCodeHandler, workerPool, ordered ? orderedTasks : null);
  }

  @Override
  public <T> Future<T> executeBlocking(Callable<T> blockingCodeHandler, boolean ordered) {
    return executeBlocking(this, blockingCodeHandler, workerPool, ordered ? orderedTasks : null);
  }

  @Override
  public EventExecutor executor() {
    return executor;
  }

  @Override
  public boolean isEventLoopContext() {
    return isEventLoop;
  }

  @Override
  public boolean isWorkerContext() {
    return !isEventLoop;
  }

  @Override
  public boolean inThread() {
    return executor.inThread();
  }

  @Override
  public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue) {
    return executeBlocking(this, blockingCodeHandler, workerPool, queue);
  }

  @Override
  public <T> Future<T> executeBlocking(Callable<T> blockingCodeHandler, TaskQueue queue) {
    return executeBlocking(this, blockingCodeHandler, workerPool, queue);
  }

  static <T> Future<T> executeBlocking(ContextInternal context, Callable<T> blockingCodeHandler,
                                       WorkerPool workerPool, TaskQueue queue) {
    return internalExecuteBlocking(context, promise -> {
      T result;
      try {
        result = blockingCodeHandler.call();
      } catch (Throwable e) {
        promise.fail(e);
        return;
      }
      promise.complete(result);
    }, workerPool, queue);
  }

  static <T> Future<T> executeBlocking(ContextInternal context, Handler<Promise<T>> blockingCodeHandler,
                                       WorkerPool workerPool, TaskQueue queue) {
    return internalExecuteBlocking(context, promise -> {
      try {
        blockingCodeHandler.handle(promise);
      } catch (Throwable e) {
        promise.tryFail(e);
      }
    }, workerPool, queue);
  }

  private static <T> Future<T> internalExecuteBlocking(ContextInternal context, Handler<Promise<T>> blockingCodeHandler,
      WorkerPool workerPool, TaskQueue queue) {
    PoolMetrics metrics = workerPool.metrics();
    Object queueMetric = metrics != null ? metrics.submitted() : null;
    Promise<T> promise = context.promise();
    Future<T> fut = promise.future();
    try {
      Runnable command = () -> {
        Object execMetric = null;
        if (metrics != null) {
          execMetric = metrics.begin(queueMetric);
        }
        context.dispatch(promise, blockingCodeHandler);
        if (metrics != null) {
          metrics.end(execMetric, fut.succeeded());
        }
      };
      Executor exec = workerPool.executor();
      if (queue != null) {
        queue.execute(command, exec);
      } else {
        exec.execute(command);
      }
    } catch (RejectedExecutionException e) {
      // Pool is already shut down
      if (metrics != null) {
        metrics.rejected(queueMetric);
      }
      throw e;
    }
    return fut;
  }

  @Override
  public VertxTracer tracer() {
    return owner.tracer();
  }

  @Override
  public ClassLoader classLoader() {
    return tccl;
  }

  @Override
  public WorkerPool workerPool() {
    return workerPool;
  }

  @Override
  public synchronized ConcurrentMap<Object, Object> contextData() {
    if (data == null) {
      data = new ConcurrentHashMap<>();
    }
    return data;
  }

  @Override
  public synchronized ConcurrentMap<Object, Object> localContextData() {
    if (localData == null) {
      localData = new ConcurrentHashMap<>();
    }
    return localData;
  }

  public void reportException(Throwable t) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler == null) {
      handler = owner.exceptionHandler();
    }
    if (handler != null) {
      handler.handle(t);
    } else {
      log.error("Unhandled exception", t);
    }
  }

  @Override
  public Context exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  protected void runOnContext(ContextInternal ctx, Handler<Void> action) {
    try {
      Executor exec = ctx.executor();
      exec.execute(() -> ctx.dispatch(action));
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
    }
  }

  @Override
  public void execute(Runnable task) {
    execute(this, task);
  }

  @Override
  public final <T> void execute(T argument, Handler<T> task) {
    execute(this, argument, task);
  }

  protected void execute(ContextInternal ctx, Runnable task) {
    if (inThread()) {
      task.run();
    } else {
      executor.execute(task);
    }
  }

  /**
   * <ul>
   *   <li>When the current thread is event-loop thread of this context the implementation will execute the {@code task} directly</li>
   *   <li>When the current thread is a worker thread of this context the implementation will execute the {@code task} directly</li>
   *   <li>Otherwise the task will be scheduled on the context thread for execution</li>
   * </ul>
   */
  protected <T> void execute(ContextInternal ctx, T argument, Handler<T> task) {
    if (inThread()) {
      task.handle(argument);
    } else {
      executor.execute(() -> task.handle(argument));
    }
  }

  @Override
  public <T> void emit(T argument, Handler<T> task) {
    emit(this, argument, task);
  }

  protected <T> void emit(ContextInternal ctx, T argument, Handler<T> task) {
    if (inThread()) {
      ContextInternal prev = ctx.beginDispatch();
      try {
        task.handle(argument);
      } catch (Throwable t) {
        reportException(t);
      } finally {
        ctx.endDispatch(prev);
      }
    } else {
      executor.execute(() -> emit(ctx, argument, task));
    }
  }

  @Override
  public ContextInternal duplicate() {
    return new DuplicatedContext(this);
  }
}
