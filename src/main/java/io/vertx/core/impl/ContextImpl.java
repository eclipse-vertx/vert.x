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
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.metrics.PoolMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
abstract class ContextImpl extends AbstractContext {

  /**
   * Execute the {@code task} disabling the thread-local association for the duration
   * of the execution. {@link Vertx#currentContext()} will return {@code null},
   * @param task the task to execute
   * @throws IllegalStateException if the current thread is not a Vertx thread
   */
  static void executeIsolated(Handler<Void> task) {
    Thread currentThread = Thread.currentThread();
    if (currentThread instanceof VertxThread) {
      VertxThread vertxThread = (VertxThread) currentThread;
      ContextInternal prev = vertxThread.beginEmission(null);
      try {
        task.handle(null);
      } finally {
        vertxThread.endEmission(prev);
      }
    } else {
      task.handle(null);
    }
  }

  private static final Logger log = LoggerFactory.getLogger(ContextImpl.class);

  private static final String DISABLE_TIMINGS_PROP_NAME = "vertx.disableContextTimings";
  static final boolean DISABLE_TIMINGS = Boolean.getBoolean(DISABLE_TIMINGS_PROP_NAME);

  protected final VertxInternal owner;
  protected final VertxTracer<?, ?> tracer;
  protected final JsonObject config;
  private final Deployment deployment;
  private final CloseHooks closeHooks;
  private final ClassLoader tccl;
  private final EventLoop eventLoop;
  private ConcurrentMap<Object, Object> data;
  private ConcurrentMap<Object, Object> localData;
  private volatile Handler<Throwable> exceptionHandler;
  final TaskQueue internalOrderedTasks;
  final WorkerPool internalBlockingPool;
  final WorkerPool workerPool;
  final TaskQueue orderedTasks;

  ContextImpl(VertxInternal vertx,
              VertxTracer<?, ?> tracer,
              EventLoop eventLoop,
              WorkerPool internalBlockingPool,
              WorkerPool workerPool,
              Deployment deployment,
              CloseHooks closeHooks,
              ClassLoader tccl) {
    if (VertxThread.DISABLE_TCCL && tccl != ClassLoader.getSystemClassLoader()) {
      log.warn("You have disabled TCCL checks but you have a custom TCCL to set.");
    }
    this.tracer = tracer;
    this.deployment = deployment;
    this.config = deployment != null ? deployment.config() : new JsonObject();
    this.eventLoop = eventLoop;
    this.tccl = tccl;
    this.owner = vertx;
    this.workerPool = workerPool;
    this.closeHooks = closeHooks;
    this.internalBlockingPool = internalBlockingPool;
    this.orderedTasks = new TaskQueue();
    this.internalOrderedTasks = new TaskQueue();
  }

  public Deployment getDeployment() {
    return deployment;
  }

  @Override
  public CloseHooks closeHooks() {
    return closeHooks;
  }

  public void addCloseHook(Closeable hook) {
    if (closeHooks != null) {
      closeHooks.add(hook);
    } else {
      owner.addCloseHook(hook);
    }
  }

  @Override
  public boolean isDeployment() {
    return deployment != null;
  }

  public void removeCloseHook(Closeable hook) {
    if (deployment != null) {
      closeHooks.remove(hook);
    } else {
      owner.removeCloseHook(hook);
    }
  }

  @Override
  public String deploymentID() {
    return deployment != null ? deployment.deploymentID() : null;
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
    return executeBlocking(this, action, internalBlockingPool, internalOrderedTasks);
  }

  @Override
  public <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action, boolean ordered) {
    return executeBlocking(this, action, internalBlockingPool, ordered ? internalOrderedTasks : null);
  }

  @Override
  public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered) {
    return executeBlocking(this, blockingCodeHandler, workerPool, ordered ? orderedTasks : null);
  }

  @Override
  public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue) {
    return executeBlocking(this, blockingCodeHandler, workerPool, queue);
  }

  static <T> Future<T> executeBlocking(ContextInternal context, Handler<Promise<T>> blockingCodeHandler,
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
        context.emit(promise, f -> {
          try {
            blockingCodeHandler.handle(promise);
          } catch (Throwable e) {
            promise.tryFail(e);
          }
        });
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
    return tracer;
  }

  @Override
  public ClassLoader classLoader() {
    return tccl;
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

  public int getInstanceCount() {
    // the no verticle case
    if (deployment == null) {
      return 0;
    }

    // the single verticle without an instance flag explicitly defined
    if (deployment.deploymentOptions() == null) {
      return 1;
    }
    return deployment.deploymentOptions().getInstances();
  }

  static abstract class Duplicated<C extends ContextImpl> extends AbstractContext {

    protected final C delegate;
    private ConcurrentMap<Object, Object> localData;

    Duplicated(C delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean isDeployment() {
      return delegate.isDeployment();
    }

    @Override
    public VertxTracer tracer() {
      return delegate.tracer();
    }

    @Override
    public final String deploymentID() {
      return delegate.deploymentID();
    }

    @Override
    public final JsonObject config() {
      return delegate.config();
    }

    @Override
    public final int getInstanceCount() {
      return delegate.getInstanceCount();
    }

    @Override
    public final Context exceptionHandler(Handler<Throwable> handler) {
      delegate.exceptionHandler(handler);
      return this;
    }

    @Override
    public final Handler<Throwable> exceptionHandler() {
      return delegate.exceptionHandler();
    }

    @Override
    public final void addCloseHook(Closeable hook) {
      delegate.addCloseHook(hook);
    }

    @Override
    public final void removeCloseHook(Closeable hook) {
      delegate.removeCloseHook(hook);
    }

    @Override
    public final EventLoop nettyEventLoop() {
      return delegate.nettyEventLoop();
    }

    @Override
    public final Deployment getDeployment() {
      return delegate.getDeployment();
    }

    @Override
    public final VertxInternal owner() {
      return delegate.owner();
    }

    @Override
    public final ClassLoader classLoader() {
      return delegate.classLoader();
    }

    @Override
    public final void reportException(Throwable t) {
      delegate.reportException(t);
    }

    @Override
    public final ConcurrentMap<Object, Object> contextData() {
      return delegate.contextData();
    }

    @Override
    public final ConcurrentMap<Object, Object> localContextData() {
      synchronized (this) {
        if (localData == null) {
          localData = new ConcurrentHashMap<>();
        }
        return localData;
      }
    }
  }
}
