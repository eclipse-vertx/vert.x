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
import io.netty.channel.EventLoopGroup;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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

  private static EventLoop getEventLoop(VertxInternal vertx) {
    EventLoopGroup group = vertx.getEventLoopGroup();
    if (group != null) {
      return group.next();
    } else {
      return null;
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

  protected ContextImpl(VertxInternal vertx, VertxTracer<?, ?> tracer, WorkerPool internalBlockingPool, WorkerPool workerPool, Deployment deployment,
                        ClassLoader tccl) {
    this(vertx, tracer, getEventLoop(vertx), internalBlockingPool, workerPool, deployment, tccl);
  }

  protected ContextImpl(VertxInternal vertx, VertxTracer<?, ?> tracer, EventLoop eventLoop, WorkerPool internalBlockingPool, WorkerPool workerPool, Deployment deployment,
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
    this.internalBlockingPool = internalBlockingPool;
    this.orderedTasks = new TaskQueue();
    this.internalOrderedTasks = new TaskQueue();
    this.closeHooks = new CloseHooks(log);
  }

  public Deployment getDeployment() {
    return deployment;
  }

  public void addCloseHook(Closeable hook) {
    closeHooks.add(hook);
  }

  public void removeCloseHook(Closeable hook) {
    closeHooks.remove(hook);
  }

  public void runCloseHooks(Handler<AsyncResult<Void>> completionHandler) {
    closeHooks.run(completionHandler);
    // Now remove context references from threads
    VertxThreadFactory.unsetContext(this);
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
  public <T> void executeBlockingInternal(Handler<Future<T>> action, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(this, action, resultHandler, internalBlockingPool, internalOrderedTasks);
  }

  @Override
  public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(this, blockingCodeHandler, resultHandler, workerPool, ordered ? orderedTasks : null);
  }

  @Override
  public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, TaskQueue queue, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(this, blockingCodeHandler, resultHandler, workerPool, queue);
  }

  static <T> void executeBlocking(ContextInternal context, Handler<Future<T>> blockingCodeHandler,
      Handler<AsyncResult<T>> resultHandler,
      WorkerPool workerPool, TaskQueue queue) {
    PoolMetrics metrics = workerPool.metrics();
    Object queueMetric = metrics != null ? metrics.submitted() : null;
    try {
      Runnable command = () -> {
        VertxThread current = (VertxThread) Thread.currentThread();
        Object execMetric = null;
        if (metrics != null) {
          execMetric = metrics.begin(queueMetric);
        }
        if (!DISABLE_TIMINGS) {
          current.executeStart();
        }
        Future<T> res = Future.future();
        try {
          ContextInternal.setContext(context);
          blockingCodeHandler.handle(res);
        } catch (Throwable e) {
          res.tryFail(e);
        } finally {
          if (!DISABLE_TIMINGS) {
            current.executeEnd();
          }
        }
        if (metrics != null) {
          metrics.end(execMetric, res.succeeded());
        }
        if (resultHandler != null) {
          res.setHandler(ar -> context.runOnContext(v -> resultHandler.handle(ar)));
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
    private final ContextInternal other;
    private ConcurrentMap<Object, Object> localData;

    public Duplicated(C delegate, ContextInternal other) {
      this.delegate = delegate;
      this.other = other;
    }

    @Override
    public VertxTracer tracer() {
      return delegate.tracer();
    }

    public final <T> void executeBlockingInternal(Handler<Future<T>> action, Handler<AsyncResult<T>> resultHandler) {
      ContextImpl.executeBlocking(this, action, resultHandler, delegate.internalBlockingPool, delegate.internalOrderedTasks);
    }

    @Override
    public final <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
      ContextImpl.executeBlocking(this, blockingCodeHandler, resultHandler, delegate.workerPool, ordered ? delegate.orderedTasks : null);
    }

    @Override
    public final <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, TaskQueue queue, Handler<AsyncResult<T>> resultHandler) {
      ContextImpl.executeBlocking(this, blockingCodeHandler, resultHandler, delegate.workerPool, queue);
    }

    @Override
    public final <T> void schedule(T value, Handler<T> task) {
      delegate.schedule(value, task);
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
      if (other == null) {
        synchronized (this) {
          if (localData == null) {
            localData = new ConcurrentHashMap<>();
          }
          return localData;
        }
      } else {
        return other.localContextData();
      }
    }
  }
}
