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

package org.vertx.java.core.impl;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Context;
import org.vertx.java.core.Handler;
import org.vertx.java.core.file.impl.PathResolver;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class ContextImpl implements Context {

  private static final Logger log = LoggerFactory.getLogger(ContextImpl.class);

  protected final VertxInternal vertx;
  private Deployment deployment;
  private Set<Closeable> closeHooks;
  private final ClassLoader tccl;
  private boolean closed;
  private final EventLoop eventLoop;
  protected final Executor orderedBgExec;
  protected VertxThread contextThread;

  protected ContextImpl(VertxInternal vertx, Executor orderedBgExec) {
    this.vertx = vertx;
    this.orderedBgExec = orderedBgExec;
    EventLoopGroup group = vertx.getEventLoopGroup();
    if (group != null) {
      this.eventLoop = group.next();
      this.tccl = Thread.currentThread().getContextClassLoader();
    } else {
      this.eventLoop = null;
      this.tccl = null;
    }
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

  // FIXME - can we get rid of this?
  public PathResolver getPathResolver() {
    return null;
  }

  public void reportException(Throwable t) {
    log.error("Unhandled exception", t);
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

  public void runCloseHooks(Handler<AsyncResult<Void>> doneHandler) {
    if (closeHooks != null) {
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
                doneHandler.handle(new FutureResultImpl<>(ar.cause()));
              }
            } else {
              if (count.incrementAndGet() == num) {
                doneHandler.handle(new FutureResultImpl<>((Void)null));
              }
            }
          });
        } catch (Throwable t) {
          reportException(t);
        }
      }
    } else {
      doneHandler.handle(new FutureResultImpl<>((Void)null));
    }
  }

  public abstract void doExecute(ContextTask task);

  public abstract boolean isEventLoopContext();

  public abstract boolean isMultithreaded();

  public void execute(ContextTask task, boolean expectRightThread) {
    if (isOnCorrectContextThread(expectRightThread)) {
      wrapTask(task, true).run();
    } else {
      doExecute(task);
    }
  }

  protected abstract boolean isOnCorrectContextThread(boolean expectRightThread);

  public void runOnContext(final Handler<Void> task) {
    execute(() -> task.handle(null), false);
  }

  public EventLoop getEventLoop() {
    return eventLoop;
  }

  // This executes the task in the worker pool using the ordered executor of the context
  // It's used e.g. from BlockingActions
  protected void executeOnOrderedWorkerExec(ContextTask task) {
    orderedBgExec.execute(wrapTask(task, false));
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
        reportException(t);
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
