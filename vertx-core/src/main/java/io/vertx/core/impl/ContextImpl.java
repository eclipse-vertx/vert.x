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
import io.vertx.core.spi.cluster.Action;

import java.util.HashSet;
import java.util.List;
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

  protected final String deploymentID;
  protected final JsonObject config;
  protected final VertxInternal vertx;
  private Deployment deployment;
  private Set<Closeable> closeHooks;
  private final ClassLoader tccl;
  private boolean closed;
  private final EventLoop eventLoop;
  protected final Executor orderedInternalPoolExec;
  protected VertxThread contextThread;

  protected ContextImpl(VertxInternal vertx, Executor orderedInternalPoolExec, String deploymentID, JsonObject config,
                        ClassLoader tccl) {
    this.vertx = vertx;
    this.orderedInternalPoolExec = orderedInternalPoolExec;
    this.deploymentID = deploymentID;
    this.config = config;
    EventLoopGroup group = vertx.getEventLoopGroup();
    if (group != null) {
      this.eventLoop = group.next();
    } else {
      this.eventLoop = null;
    }
    this.tccl = tccl;
  }

  public void setTCCL() {
    Thread.currentThread().setContextClassLoader(tccl);
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
                completionHandler.handle(Future.completedFuture(ar.cause()));
              }
            } else {
              if (count.incrementAndGet() == num) {
                completionHandler.handle(Future.completedFuture());
              }
            }
          });
        } catch (Throwable t) {
          log.warn("Failed to run close hooks", t);
        }
      }
    } else {
      completionHandler.handle(Future.completedFuture());
    }
  }

  public abstract void doExecute(ContextTask task);

  public abstract boolean isEventLoopContext();

  public abstract boolean isMultiThreaded();

  public boolean isWorker() {
    return !isEventLoopContext();
  }

  public void execute(ContextTask task, boolean expectRightThread) {
    if (isOnCorrectContextThread(expectRightThread)) {
      wrapTask(task, true).run();
    } else {
      doExecute(task);
    }
  }

  protected abstract boolean isOnCorrectContextThread(boolean expectRightThread);

  public void runOnContext(final Handler<Void> task) {
    try {
      execute(() -> task.handle(null), false);
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
  public <T> void executeBlocking(Action<T> action, Handler<AsyncResult<T>> resultHandler) {
    try {
      orderedInternalPoolExec.execute(() -> {
        Future<T> res = Future.future();
        try {
          T result = action.perform();
          res.complete(result);
        } catch (Throwable e) {
          res.fail(e);
        }
        if (resultHandler != null) {
          execute(() -> res.setHandler(resultHandler), false);
        }
      });
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
    }
  }

  public void close() {
    unsetContext();
    closed = true;
  }

  private void unsetContext() {
    vertx.setContext(null);
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

  protected Runnable wrapTask(ContextTask task, boolean checkThread) {
    return () -> {
      if (checkThread) {
        executeStart();
      }
      try {
        vertx.setContext(ContextImpl.this);
        task.run();
      } catch (Throwable t) {
        log.error("Unhandled exception", t);
      } finally {
        // TODO - we might have to restore the thread name in case it's been changed during the execution
        if (checkThread) {
          executeEnd();
        }
      }
      if (closed) {
        // We allow tasks to be run after the context is closed but we make sure we unset the context afterwards
        // to avoid any leaks
        unsetContext();
      }
    };
  }

}
