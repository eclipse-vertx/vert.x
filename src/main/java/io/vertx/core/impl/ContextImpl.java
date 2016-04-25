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

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.vertx.core.*;
import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.metrics.ThreadPoolMetrics;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class ContextImpl implements ContextInternal {

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
  protected final Map<String, ThreadPoolMetrics> poolMetrics;
  private final NamedThreadPoolManager namedPools;
  protected final ThreadPoolMetrics workerMetrics;
  protected final ThreadPoolMetrics internalBlockingPoolMetrics;
  private Deployment deployment;
  private Set<Closeable> closeHooks;
  private final ClassLoader tccl;
  private final EventLoop eventLoop;
  protected final Executor orderedInternalPoolExec;
  protected final Executor workerExec;
  protected VertxThread contextThread;
  private volatile boolean closeHooksRun;
  private Map<String, Object> contextData;
  private volatile Handler<Throwable> exceptionHandler;

  protected ContextImpl(VertxInternal vertx, Executor orderedInternalPoolExec, Executor workerExec, String deploymentID, JsonObject config,
                        ClassLoader tccl, NamedThreadPoolManager namedThreadPools,
                        Map<String, ThreadPoolMetrics> poolMetrics) {
    if (DISABLE_TCCL && !tccl.getClass().getName().equals("sun.misc.Launcher$AppClassLoader")) {
      log.warn("You have disabled TCCL checks but you have a custom TCCL to set.");
    }
    this.orderedInternalPoolExec = orderedInternalPoolExec;
    this.workerExec = workerExec;
    this.deploymentID = deploymentID;
    this.config = config;
    EventLoopGroup group = vertx.getEventLoopGroup();
    if (group != null) {
      this.eventLoop = group.next();
    } else {
      this.eventLoop = null;
    }
    this.tccl = tccl;
    this.owner = vertx;
    this.exceptionHandler = vertx.exceptionHandler();

    this.namedPools = namedThreadPools;
    this.poolMetrics = poolMetrics;
    if (poolMetrics != null) {
      internalBlockingPoolMetrics = poolMetrics.get("vert.x-internal-blocking-pool");
      workerMetrics = poolMetrics.get("vert.x-worker-thread-pool");
    } else {
      internalBlockingPoolMetrics = null;
      workerMetrics = null;
    }
  }

  public static void setContext(ContextImpl context) {
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
    if (closeHooks == null) {
      // Has to be concurrent as can be removed from non context thread
      closeHooks = new ConcurrentHashSet<>();
    }
    closeHooks.add(hook);
  }

  public void removeCloseHook(Closeable hook) {
    if (closeHooks != null) {
      closeHooks.remove(hook);
    }
  }

  public void runCloseHooks(Handler<AsyncResult<Void>> completionHandler) {
    if (closeHooksRun) {
      // Sanity check
      throw new IllegalStateException("Close hooks already run");
    }
    closeHooksRun = true;
    if (closeHooks != null && !closeHooks.isEmpty()) {
      // Must copy before looping as can be removed during loop otherwise
      Set<Closeable> copy = new HashSet<>(closeHooks);
      int num = copy.size();
      if (num != 0) {
        AtomicInteger count = new AtomicInteger();
        AtomicBoolean failed = new AtomicBoolean();
        for (Closeable hook : copy) {
          try {
            hook.close(ar -> {
              if (ar.failed()) {
                if (failed.compareAndSet(false, true)) {
                  // Only report one failure
                  completionHandler.handle(Future.failedFuture(ar.cause()));
                }
              } else {
                if (count.incrementAndGet() == num) {
                  // closeHooksRun = true;
                  completionHandler.handle(Future.succeededFuture());
                }
              }
            });
          } catch (Throwable t) {
            log.warn("Failed to run close hooks", t);
          }
        }
      } else {
        completionHandler.handle(Future.succeededFuture());
      }
    } else {
      completionHandler.handle(Future.succeededFuture());
    }
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

  public static boolean isOnWorkerThread() {
    return isOnVertxThread(true);
  }

  public static boolean isOnEventLoopThread() {
    return isOnVertxThread(false);
  }

  public static boolean isOnVertxThread() {
    Thread t = Thread.currentThread();
    return (t instanceof VertxThread);
  }

  private static boolean isOnVertxThread(boolean worker) {
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
  public void executeFromIO(ContextTask task) {
    if (THREAD_CHECKS) {
      checkCorrectThread();
    }
    // No metrics on this, as we are on the event loop.
    wrapTask(task, null, true, null).run();
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

  public Vertx owner() {
    return owner;
  }

  // Execute an internal task on the internal blocking ordered executor
  public <T> void executeBlocking(Action<T> action, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(action, null, true, true, resultHandler, null);
  }

  @Override
  public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(null, blockingCodeHandler, false, ordered, resultHandler, null);
  }

  @Override
  public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(blockingCodeHandler, true, resultHandler);
  }

  public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler,
                                  String poolName) {
    executeBlocking(null, blockingCodeHandler, false, true, resultHandler, poolName);
  }

  protected synchronized Map<String, Object> contextData() {
    if (contextData == null) {
      contextData = new ConcurrentHashMap<>();
    }
    return contextData;
  }

  private <T> void executeBlocking(Action<T> action, Handler<Future<T>> blockingCodeHandler, boolean internal,
                                   boolean ordered, Handler<AsyncResult<T>> resultHandler, String poolName) {
    final ThreadPoolMetrics.Job job;

    Executor executor;
    if (poolName != null) {
      executor = namedPools.get(poolName, ordered);
    } else {
      executor = internal ? orderedInternalPoolExec : (ordered ? workerExec : owner.getWorkerPool());
    }

    if (poolMetrics != null) {
      ThreadPoolMetrics metrics;
      if (poolName != null) {
        metrics = namedPools.getMetrics(poolName);
      } else {
        metrics = internal ? internalBlockingPoolMetrics : workerMetrics;
      }
      job = metrics.jobSubmitted();
    } else {
      job = null;
    }

    try {
      executor.execute(() -> {
        if (job != null) {
          job.executing();
        }
        Future<T> res = Future.future();
        try {
          if (blockingCodeHandler != null) {
            setContext(this);
            blockingCodeHandler.handle(res);
          } else {
            T result = action.perform();
            res.complete(result);
          }
        } catch (Throwable e) {
          res.fail(e);
        }
        if (job != null) {
          if (resultHandler != null) {
            runOnContext(v -> res.setHandler(ar -> {
              job.completed(ar.succeeded());
              resultHandler.handle(ar);
            }));
          } else {
            res.setHandler(ar -> job.completed(ar.succeeded()));
          }
        } else {
          if (resultHandler != null) {
            runOnContext(v -> res.setHandler(resultHandler));
          }
        }
      });
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
      if (job != null) job.rejected();
    }
  }

  protected Runnable wrapTask(ContextTask cTask, Handler<Void> hTask, boolean checkThread, ThreadPoolMetrics.Job job) {
    return () -> {
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

      if (job != null) {
        job.executing();
      }

      if (!DISABLE_TIMINGS) {
        current.executeStart();
      }
      try {
        setContext(current, ContextImpl.this);
        if (cTask != null) {
          cTask.run();
        } else {
          hTask.handle(null);
        }
        if (job != null) {
          job.completed(true);
        }
      } catch (Throwable t) {
        log.error("Unhandled exception", t);
        Handler<Throwable> handler = this.exceptionHandler;
        if (handler != null) {
          handler.handle(t);
        }
        if (job != null) {
          job.completed(false);
        }
      } finally {
        // We don't unset the context after execution - this is done later when the context is closed via
        // VertxThreadFactory
        if (!DISABLE_TIMINGS) {
          current.executeEnd();
        }
      }
    };
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
