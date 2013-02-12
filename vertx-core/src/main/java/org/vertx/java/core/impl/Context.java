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

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class Context {

  private static final Logger log = LoggerFactory.getLogger(Context.class);

  private final VertxInternal vertx;
  private DeploymentHandle deploymentContext;
  private Path pathAdjustment;
  private Map<Object, Runnable> closeHooks;
  private final ClassLoader tccl;
  private boolean closed;
  protected final Executor orderedBgExec;

  protected Context(VertxInternal vertx, Executor orderedBgExec) {
    this.vertx = vertx;
    this.orderedBgExec = orderedBgExec;
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

  public Path getPathAdjustment() {
    return pathAdjustment;
  }

  public void setPathAdjustment(Path pathAdjustment) {
    this.pathAdjustment = pathAdjustment;
  }

  public void reportException(Throwable t) {
    if (deploymentContext != null) {
      deploymentContext.reportException(t);
    } else {
      t.printStackTrace();
      log.error("context Unhandled exception", t);
    }
  }

  public Runnable getCloseHook(Object key) {
    return closeHooks == null ? null : closeHooks.get(key);
  }

  public void putCloseHook(Object key, Runnable hook) {
    if (closeHooks == null) {
      closeHooks = new HashMap<>();
    }
    closeHooks.put(key, hook);
  }

  public void runCloseHooks() {
    if (closeHooks != null) {
      for (Runnable hook: closeHooks.values()) {
        try {
          hook.run();
        } catch (Throwable t) {
          reportException(t);
        }
      }
    }
  }

  public abstract void execute(Runnable handler);

  public abstract boolean isOnCorrectWorker(NioWorker worker);

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
        try {
          vertx.setContext(Context.this);
          task.run();
        } catch (Throwable t) {
          reportException(t);
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
