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
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Starter;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class ContextImpl implements Context {

  private static final Logger log = LoggerFactory.getLogger(ContextImpl.class);

  protected final VertxInternal owner;
  protected final String deploymentID;
  protected final JsonObject config;
  private Deployment deployment;
  private Set<Closeable> closeHooks;
  private final ClassLoader tccl;
  private final EventLoop eventLoop;
  protected final Executor orderedInternalPoolExec;
  protected final Executor workerExec;
  protected VertxThread contextThread;

  protected ContextImpl(VertxInternal vertx, Executor orderedInternalPoolExec, Executor workerExec, String deploymentID, JsonObject config,
                        ClassLoader tccl) {
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
  }

  public static void setContext(ContextImpl context) {
    Thread current = Thread.currentThread();
    if (current instanceof VertxThread) {
      ((VertxThread)current).setContext(context);
      if (context != null) {
        context.setTCCL();
      } else {
        Thread.currentThread().setContextClassLoader(null);
      }
    } else {
      throw new IllegalStateException("Attempt to setContext on non Vert.x thread " + Thread.currentThread());
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
      closeHooks = new HashSet<>();
    }
    closeHooks.add(hook);
  }

  public void removeCloseHook(Closeable hook) {
    if (closeHooks != null) {
      closeHooks.remove(hook);
    }
  }

  public void runCloseHooks(Handler<AsyncResult<Void>> completionHandler) {
    if (closeHooks != null && !closeHooks.isEmpty()) {
      final int num = closeHooks.size();
      AtomicInteger count = new AtomicInteger();
      AtomicBoolean failed = new AtomicBoolean();
      // Copy to avoid ConcurrentModificationException
      for (Closeable hook: new HashSet<>(closeHooks)) {
        try {
          hook.close(ar -> {
            if (ar.failed()) {
              if (failed.compareAndSet(false, true)) {
                // Only report one failure
                completionHandler.handle(Future.failedFuture(ar.cause()));
              }
            } else {
              if (count.incrementAndGet() == num) {
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
  }

  protected abstract void executeAsync(Handler<Void> task);

  public abstract boolean isEventLoopContext();

  public abstract boolean isMultiThreaded();

  public abstract Map<String, Object> contextData();

  @Override
  @SuppressWarnings("unchecked")
  public <T> T get(String key) {
    return (T)contextData().get(key);
  }

  @Override
  public void put(String key, Object value) {
    contextData().put(key, value);
  }

  @Override
  public boolean remove(String key) {
    return contextData().remove(key) != null;
  }

  public boolean isWorker() {
    return !isEventLoopContext();
  }

  // We should already be on the event loop, but check this anyway, then execute directly
  public void executeSync(ContextTask task) {
    checkCorrectThread();
    wrapTask(task, null, true).run();
  }

  protected abstract void checkCorrectThread();

  // Run the task asynchronously on this same context
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
    return Starter.PROCESS_ARGS;
  }

  public EventLoop getEventLoop() {
    return eventLoop;
  }

  // Execute an internal task on the internal blocking ordered executor
  public <T> void executeBlocking(Action<T> action, boolean internal, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(action, null, internal, resultHandler);
  }

  public <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(null, blockingCodeHandler, false, resultHandler);
  }

  private <T> void executeBlocking(Action<T> action, Handler<Future<T>> blockingCodeHandler, boolean internal,
                                  Handler<AsyncResult<T>> resultHandler) {
    try {
      Executor exec = internal ? orderedInternalPoolExec : workerExec;
      exec.execute(() -> {
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
        if (resultHandler != null) {
          runOnContext(v -> res.setHandler(resultHandler));
        }
      });
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
    }
  }

  protected void executeStart() {
    Thread thread = Thread.currentThread();
    // Sanity check - make sure Netty is really delivering events on the correct thread
    if (this.contextThread == null) {
      if (thread instanceof VertxThread) {
        this.contextThread = (VertxThread)thread;
      } else {
        throw new IllegalStateException("Not a vert.x thread!");
      }
    } else if (this.contextThread != thread && !this.contextThread.isWorker()) {
      throw new IllegalStateException("Uh oh! Event loop context executing with wrong thread! Expected " + this.contextThread + " got " + thread);
    }
    this.contextThread.executeStart();
  }

  protected void executeEnd() {
    contextThread.executeEnd();
  }

  protected Runnable wrapTask(ContextTask cTask, Handler<Void> hTask, boolean checkThread) {
    return () -> {
      if (checkThread) {
        executeStart();
      }
      try {
        setContext(ContextImpl.this);
        if (cTask != null) {
          cTask.run();
        } else {
          hTask.handle(null);
        }
      } catch (Throwable t) {
        log.error("Unhandled exception", t);
      } finally {
        // TODO - we might have to restore the thread name in case it's been changed during the execution
        if (checkThread) {
          executeEnd();
        }
      }
    };
  }

  private void setTCCL() {
    Thread.currentThread().setContextClassLoader(tccl);
  }

  public int getInstanceCount(){
    if(deployment == null || deployment.deploymentOptions() == null) {
      // implicitly there is the current instance of the verticle running always
      return 1;
    }
    return deployment.deploymentOptions().getInstances();
  }
}
