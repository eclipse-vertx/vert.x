/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.impl;

import io.netty.channel.EventLoop;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Context;
import org.vertx.java.core.Handler;
import org.vertx.java.core.file.impl.PathResolver;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class DefaultContext implements Context {

  private static final Logger log = LoggerFactory.getLogger(DefaultContext.class);

  protected final VertxInternal vertx;
  private DeploymentHandle deploymentContext;
  private PathResolver pathResolver;
  private Set<Closeable> closeHooks;
  private final ClassLoader tccl;
  private boolean closed;
  private final EventLoop eventLoop;
  protected final Executor orderedBgExec;

  protected DefaultContext(VertxInternal vertx, Executor orderedBgExec) {
    this.vertx = vertx;
    this.orderedBgExec = orderedBgExec;
    this.eventLoop = vertx.getEventLoopGroup().next();
    this.tccl = Thread.currentThread().getContextClassLoader();
  }

  public void setTCCL() {
    Thread.currentThread().setContextClassLoader(tccl);
  }

  public void setDeploymentHandle(DeploymentHandle deploymentHandle) {
    this.deploymentContext = deploymentHandle;
  }

  public DeploymentHandle getDeploymentHandle() {
    return deploymentContext;
  }

  public PathResolver getPathResolver() {
    return pathResolver;
  }

  public void setPathResolver(PathResolver pathResolver) {
    this.pathResolver = pathResolver;
  }

  public void reportException(Throwable t) {
    if (deploymentContext != null) {
      deploymentContext.reportException(t);
    } else {
      log.error("Unhandled exception", t);
    }
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
      final CountingCompletionHandler<Void> aggHandler = new CountingCompletionHandler<>(vertx, closeHooks.size());
      aggHandler.setHandler(doneHandler);
      // Copy to avoid ConcurrentModificationException
      for (Closeable hook: new HashSet<>(closeHooks)) {
        try {
          hook.close(new AsyncResultHandler<Void>() {
            @Override
            public void handle(AsyncResult<Void> asyncResult) {
              if (asyncResult.failed()) {
                aggHandler.failed(asyncResult.cause());
              } else {
                aggHandler.complete();
              }
            }
          });
        } catch (Throwable t) {
          reportException(t);
        }
      }
    } else {
      doneHandler.handle(new DefaultFutureResult<>((Void)null));
    }
  }

  public abstract void execute(Runnable handler);

  public abstract boolean isOnCorrectWorker(EventLoop worker);

  public void execute(EventLoop worker, Runnable handler) {
    if (isOnCorrectWorker(worker)) {
      wrapTask(handler).run();
    } else {
      execute(handler);
    }
  }

  public void runOnContext(final Handler<Void> task) {
    execute(new Runnable() {
      public void run() {
        task.handle(null);
      }
    });
  }

  public EventLoop getEventLoop() {
    return eventLoop;
  }

  // This executes the task in the worker pool using the ordered executor of the context
  // It's used e.g. from BlockingActions
  protected void executeOnOrderedWorkerExec(final Runnable task) {
    orderedBgExec.execute(wrapTask(task));
  }

  public void close() {
    unsetContext();
    closed = true;
  }

  private void unsetContext() {
    vertx.setContext(null);
    Thread.currentThread().setContextClassLoader(null);
  }

  protected Runnable wrapTask(final Runnable task) {
    return new Runnable() {
      public void run() {
        Thread currentThread = Thread.currentThread();
        String threadName = currentThread.getName();
        try {
          vertx.setContext(DefaultContext.this);
          task.run();
        } catch (Throwable t) {
          reportException(t);
        } finally {
          if (!threadName.equals(currentThread.getName())) {
            currentThread.setName(threadName);
          }
        }
        if (closed) {
          // We allow tasks to be run after the context is closed but we make sure we unset the context afterwards
          // to avoid any leaks
          unsetContext();
        }
      }
    };
  }

}
