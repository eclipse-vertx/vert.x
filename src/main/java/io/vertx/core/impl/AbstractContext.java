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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Starter;
import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * A context implementation that does not hold any specific state.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
abstract class AbstractContext implements ContextInternal {

  private static final String THREAD_CHECKS_PROP_NAME = "vertx.threadChecks";
  private static final boolean THREAD_CHECKS = Boolean.getBoolean(THREAD_CHECKS_PROP_NAME);

  abstract void executeAsync(Handler<Void> task);

  abstract <T> void execute(T value, Handler<T> task);

  @Override
  public abstract boolean isEventLoopContext();

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
  public final void executeFromIO(Handler<Void> task) {
    executeFromIO(null, task);
  }

  @Override
  public final void schedule(Handler<Void> task) {
    schedule(null, task);
  }

  @Override
  public final void dispatch(Handler<Void> task) {
    dispatch(null, task);
  }

  @Override
  public final <T> void dispatch(T arg, Handler<T> task) {
    VertxThread currentThread = ContextInternal.beginDispatch(this);
    try {
      task.handle(arg);
    } catch (Throwable t) {
      reportException(t);
    } finally {
      ContextInternal.endDispatch(currentThread);
    }
  }

  @Override
  public final <T> void executeFromIO(T value, Handler<T> task) {
    if (THREAD_CHECKS) {
      checkEventLoopThread();
    }
    execute(value, task);
  }

  private void checkEventLoopThread() {
    Thread current = Thread.currentThread();
    if (!(current instanceof VertxThread)) {
      throw new IllegalStateException("Expected to be on Vert.x thread, but actually on: " + current);
    } else if (((VertxThread) current).isWorker()) {
      throw new IllegalStateException("Event delivered on unexpected worker thread " + current);
    }
  }

  // Run the task asynchronously on this same context
  @Override
  public final void runOnContext(Handler<Void> task) {
    try {
      executeAsync(task);
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
    }
  }

  @Override
  public final List<String> processArgs() {
    // As we are maintaining the launcher and starter class, choose the right one.
    List<String> processArgument = VertxCommandLauncher.getProcessArguments();
    return processArgument != null ? processArgument : Starter.PROCESS_ARGS;
  }

  @Override
  public final <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(blockingCodeHandler, true, resultHandler);
  }

  @Override
  public final ContextInternal duplicate() {
    return duplicate(null);
  }

  @SuppressWarnings("unchecked")
  @Override
  public final <T> T get(String key) {
    return (T) contextData().get(key);
  }

  @Override
  public final void put(String key, Object value) {
    contextData().put(key, value);
  }

  @Override
  public final boolean remove(String key) {
    return contextData().remove(key) != null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final <T> T getLocal(String key) {
    return (T) localContextData().get(key);
  }

  @Override
  public final void putLocal(String key, Object value) {
    localContextData().put(key, value);
  }

  @Override
  public final boolean removeLocal(String key) {
    return localContextData().remove(key) != null;
  }
}
