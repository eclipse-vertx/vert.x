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
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Starter;
import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
abstract class ContextImpl implements ContextInternal {

  private static EventLoop getEventLoop(VertxInternal vertx) {
    EventLoopGroup group = vertx.getEventLoopGroup();
    if (group != null) {
      return group.next();
    } else {
      return null;
    }
  }

  private static final Logger log = LoggerFactory.getLogger(ContextImpl.class);

  private static final String THREAD_CHECKS_PROP_NAME = "vertx.threadChecks";
  private static final String DISABLE_TIMINGS_PROP_NAME = "vertx.disableContextTimings";
  private static final String DISABLE_TCCL_PROP_NAME = "vertx.disableTCCL";
  private static final boolean THREAD_CHECKS = Boolean.getBoolean(THREAD_CHECKS_PROP_NAME);
  private static final boolean DISABLE_TIMINGS = Boolean.getBoolean(DISABLE_TIMINGS_PROP_NAME);
  private static final boolean DISABLE_TCCL = Boolean.getBoolean(DISABLE_TCCL_PROP_NAME);

  protected final VertxInternal owner;
  protected final String deploymentID;
  protected final JsonObject config;
  private Deployment deployment;
  private CloseHooks closeHooks;
  private final ClassLoader tccl;
  private final EventLoop eventLoop;
  protected VertxThread contextThread;
  private ConcurrentMap<Object, Object> contextData;
  private volatile Handler<Throwable> exceptionHandler;
  protected final WorkerPool workerPool;
  protected final WorkerPool internalBlockingPool;
  final TaskQueue orderedTasks;
  protected final TaskQueue internalOrderedTasks;

  protected ContextImpl(VertxInternal vertx, WorkerPool internalBlockingPool, WorkerPool workerPool, String deploymentID, JsonObject config,
                        ClassLoader tccl) {
    this(vertx, getEventLoop(vertx), internalBlockingPool, workerPool, deploymentID, config, tccl);
  }

  protected ContextImpl(VertxInternal vertx, EventLoop eventLoop, WorkerPool internalBlockingPool, WorkerPool workerPool, String deploymentID, JsonObject config,
                        ClassLoader tccl) {
    if (DISABLE_TCCL && !tccl.getClass().getName().equals("sun.misc.Launcher$AppClassLoader")) {
      log.warn("You have disabled TCCL checks but you have a custom TCCL to set.");
    }
    this.deploymentID = deploymentID;
    this.config = config;
    this.eventLoop = eventLoop;
    this.tccl = tccl;
    this.owner = vertx;
    this.workerPool = workerPool;
    this.internalBlockingPool = internalBlockingPool;
    this.orderedTasks = new TaskQueue();
    this.internalOrderedTasks = new TaskQueue();
    this.closeHooks = new CloseHooks(log);
  }

  static void setContext(ContextImpl context) {
    Thread current = Thread.currentThread();
    if (current instanceof VertxThread) {
      setContext((VertxThread) current, context);
    } else {
      throw new IllegalStateException("Attempt to setContext on non Vert.x thread " + Thread.currentThread());
    }
  }

  private static void setContext(VertxThread thread, ContextImpl context) {
    thread.setContext(context);
    if (!DISABLE_TCCL) {
      if (context != null) {
        context.setTCCL();
      } else {
        Thread.currentThread().setContextClassLoader(null);
      }
    }
  }

  public void setDeployment(Deployment deployment) {
    this.deployment = deployment;
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

  protected abstract void executeAsync(Handler<Void> task);

  @Override
  public abstract boolean isEventLoopContext();

  @Override
  public abstract boolean isMultiThreadedWorkerContext();

  @Override
  @SuppressWarnings("unchecked")
  public <T> T get(String key) {
    return (T) contextData().get(key);
  }

  @Override
  public void put(String key, Object value) {
    contextData().put(key, value);
  }

  @Override
  public boolean remove(String key) {
    return contextData().remove(key) != null;
  }

  @Override
  public boolean isWorkerContext() {
    return !isEventLoopContext();
  }

  static boolean isOnVertxThread(boolean worker) {
    Thread t = Thread.currentThread();
    if (t instanceof VertxThread) {
      VertxThread vt = (VertxThread) t;
      return vt.isWorker() == worker;
    }
    return false;
  }

  // This is called to execute code where the origin is IO (from Netty probably).
  // In such a case we should already be on an event loop thread (as Netty manages the event loops)
  // but check this anyway, then execute directly
  @Override
  public void executeFromIO(Handler<Void> task) {
    executeFromIO(null, task);
  }

  @Override
  public <T> void executeFromIO(T value, Handler<T> task) {
    if (THREAD_CHECKS) {
      checkCorrectThread();
    }
    // No metrics on this, as we are on the event loop.
    executeTask(value, task, true);
  }

  protected abstract void checkCorrectThread();

  // Run the task asynchronously on this same context
  @Override
  public void runOnContext(Handler<Void> task) {
    try {
      executeAsync(task);
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
    }
  }

  @Override
  public String deploymentID() {
    return deploymentID;
  }

  @Override
  public JsonObject config() {
    return config;
  }

  @Override
  public List<String> processArgs() {
    // As we are maintaining the launcher and starter class, choose the right one.
    List<String> processArgument = VertxCommandLauncher.getProcessArguments();
    return processArgument != null ? processArgument : Starter.PROCESS_ARGS;
  }

  public EventLoop nettyEventLoop() {
    return eventLoop;
  }

  public VertxInternal owner() {
    return owner;
  }

  @Override
  public <T> void executeBlockingInternal(Handler<Future<T>> action, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(action, resultHandler, internalBlockingPool.executor(), internalOrderedTasks, internalBlockingPool.metrics());
  }

  @Override
  public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(blockingCodeHandler, resultHandler, workerPool.executor(), ordered ? orderedTasks : null, workerPool.metrics());
  }

  @Override
  public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(blockingCodeHandler, true, resultHandler);
  }

  @Override
  public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, TaskQueue queue, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(blockingCodeHandler, resultHandler, workerPool.executor(), queue, workerPool.metrics());
  }

  <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler,
      Handler<AsyncResult<T>> resultHandler,
      Executor exec, TaskQueue queue, PoolMetrics metrics) {
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
          ContextImpl.setContext(this);
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
          runOnContext(v -> res.setHandler(resultHandler));
        }
      };
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
  public synchronized ConcurrentMap<Object, Object> contextData() {
    if (contextData == null) {
      contextData = new ConcurrentHashMap<>();
    }
    return contextData;
  }

  protected <T> Runnable wrapTask(T arg, Handler<T> hTask, boolean checkThread, PoolMetrics metrics) {
    Object metric = metrics != null ? metrics.submitted() : null;
    return () -> {
      if (metrics != null) {
        metrics.begin(metric);
      }
      boolean succeeded = executeTask(arg, hTask, checkThread);
      if (metrics != null) {
        metrics.end(metric, succeeded);
      }
    };
  }

  protected <T> boolean executeTask(T arg, Handler<T> hTask, boolean checkThread) {
    Thread th = Thread.currentThread();
    if (!(th instanceof VertxThread)) {
      throw new IllegalStateException("Uh oh! Event loop context executing with wrong thread! Expected " + contextThread + " got " + th);
    }
    VertxThread current = (VertxThread) th;
    if (THREAD_CHECKS && checkThread) {
      if (contextThread == null) {
        contextThread = current;
      } else if (contextThread != current && !contextThread.isWorker()) {
        throw new IllegalStateException("Uh oh! Event loop context executing with wrong thread! Expected " + contextThread + " got " + current);
      }
    }
    if (!DISABLE_TIMINGS) {
      current.executeStart();
    }
    try {
      setContext(current, ContextImpl.this);
      hTask.handle(arg);
      return true;
    } catch (Throwable t) {
      handleException(t);
      return false;
    } finally {
      // We don't unset the context after execution - this is done later when the context is closed via
      // VertxThreadFactory
      if (!DISABLE_TIMINGS) {
        current.executeEnd();
      }
    }
  }

  private void handleException(Throwable t) {
    log.error("Unhandled exception", t);
    Handler<Throwable> handler = this.exceptionHandler;
    if (handler == null) {
      handler = owner.exceptionHandler();
    }
    if (handler != null) {
      handler.handle(t);
    }
  }

  private void setTCCL() {
    Thread.currentThread().setContextClassLoader(tccl);
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
}
