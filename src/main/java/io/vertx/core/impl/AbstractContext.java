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

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.vertx.core.*;
import io.vertx.core.impl.future.FailedFuture;
import io.vertx.core.impl.future.PromiseImpl;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.future.SucceededFuture;
import io.vertx.core.impl.launcher.VertxCommandLauncher;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.impl.VertxThread.DISABLE_TCCL;

/**
 * A context implementation that does not hold any specific state.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
abstract class AbstractContext implements ContextInternal {

  static final String THREAD_CHECKS_PROP_NAME = "vertx.threadChecks";
  static final boolean THREAD_CHECKS = Boolean.getBoolean(THREAD_CHECKS_PROP_NAME);

  static class Holder implements BlockedThreadChecker.Task {

    BlockedThreadChecker checker;
    ContextInternal ctx;
    long startTime = 0;
    long maxExecTime = VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME;
    TimeUnit maxExecTimeUnit = VertxOptions.DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME_UNIT;

    @Override
    public long startTime() {
      return startTime;
    }

    @Override
    public long maxExecTime() {
      return maxExecTime;
    }

    @Override
    public TimeUnit maxExecTimeUnit() {
      return maxExecTimeUnit;
    }
  }

  final static FastThreadLocal<Holder> holderLocal = new FastThreadLocal<Holder>() {
    @Override
    protected Holder initialValue() {
      return new Holder();
    }
  };

  @Override
  public abstract boolean isEventLoopContext();

  @Override
  public final boolean isRunningOnContext() {
    return Vertx.currentContext() == this && inThread();
  }

  abstract boolean inThread();

  @Override
  public boolean isWorkerContext() {
    return !isEventLoopContext();
  }

  @Override
  public final <T> void emit(T argument, Handler<T> task) {
    execute(v -> dispatch(argument, task));
  }

  @Override
  public void emit(Handler<Void> task) {
    emit(null, task);
  }

  @Override
  public final void execute(Handler<Void> task) {
    execute(null, task);
  }

  @Override
  public final void dispatch(Handler<Void> handler) {
    dispatch(null, handler);
  }

  public final ContextInternal beginDispatch() {
    ContextInternal prev;
    Thread th = Thread.currentThread();
    if (th instanceof VertxThread) {
      prev = ((VertxThread)th).beginEmission(this);
    } else {
      prev = beginNettyThreadEmit(th);
    }
    if (!DISABLE_TCCL) {
      th.setContextClassLoader(classLoader());
    }
    return prev;
  }

  private ContextInternal beginNettyThreadEmit(Thread th) {
    if (th instanceof FastThreadLocalThread) {
      Holder holder = holderLocal.get();
      ContextInternal prev = holder.ctx;
      if (!ContextImpl.DISABLE_TIMINGS) {
        if (holder.checker == null) {
          BlockedThreadChecker checker = owner().blockedThreadChecker();
          holder.checker = checker;
          holder.maxExecTime = owner().maxEventLoopExecTime();
          holder.maxExecTimeUnit = owner().maxEventLoopExecTimeUnit();
          checker.registerThread(th, holder);
        }
        if (holder.ctx == null) {
          holder.startTime = System.nanoTime();
        }
      }
      holder.ctx = this;
      return prev;
    } else {
      throw new IllegalStateException("Uh oh! context executing with wrong thread! " + th);
    }
  }

  public final void endDispatch(ContextInternal previous) {
    Thread th = Thread.currentThread();
    if (!DISABLE_TCCL) {
      th.setContextClassLoader(previous != null ? previous.classLoader() : null);
    }
    if (th instanceof VertxThread) {
      ((VertxThread)th).endEmission(previous);
    } else {
      endNettyThreadAssociation(th, previous);
    }
  }

  @Override
  public long setPeriodic(long delay, Handler<Long> handler) {
    VertxImpl owner = (VertxImpl) owner();
    return owner.scheduleTimeout(this, handler, delay, true);
  }

  @Override
  public long setTimer(long delay, Handler<Long> handler) {
    VertxImpl owner = (VertxImpl) owner();
    return owner.scheduleTimeout(this, handler, delay, false);
  }

  private static void endNettyThreadAssociation(Thread th, ContextInternal prev) {
    if (th instanceof FastThreadLocalThread) {
      Holder holder = holderLocal.get();
      holder.ctx = prev;
      if (!ContextImpl.DISABLE_TIMINGS) {
        if (holder.ctx == null) {
          holder.startTime = 0L;
        }
      }
    } else {
      throw new IllegalStateException("Uh oh! context executing with wrong thread! " + th);
    }
  }

  @Override
  public final <T> void dispatch(T event, Handler<T> handler) {
    ContextInternal prev = beginDispatch();
    try {
      handler.handle(event);
    } catch (Throwable t) {
      reportException(t);
    } finally {
      endDispatch(prev);
    }
  }

  public final void dispatch(Runnable handler) {
    ContextInternal prev = beginDispatch();
    try {
      handler.run();
    } catch (Throwable t) {
      reportException(t);
    } finally {
      endDispatch(prev);
    }
  }

  static void checkEventLoopThread() {
    Thread current = Thread.currentThread();
    if (!(current instanceof FastThreadLocalThread)) {
      throw new IllegalStateException("Expected to be on Vert.x thread, but actually on: " + current);
    } else if ((current instanceof VertxThread) && ((VertxThread) current).isWorker()) {
      throw new IllegalStateException("Event delivered on unexpected worker thread " + current);
    }
  }

  @Override
  public final List<String> processArgs() {
    return VertxCommandLauncher.getProcessArguments();
  }

  @Override
  public final <T> void executeBlockingInternal(Handler<Promise<T>> action, Handler<AsyncResult<T>> resultHandler) {
    Future<T> fut = executeBlockingInternal(action);
    setResultHandler(this, fut, resultHandler);
  }

  @Override
  public <T> void executeBlockingInternal(Handler<Promise<T>> action, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
    Future<T> fut = executeBlockingInternal(action, ordered);
    setResultHandler(this, fut, resultHandler);
  }

  @Override
  public <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
    Future<T> fut = executeBlocking(blockingCodeHandler, ordered);
    setResultHandler(this, fut, resultHandler);
  }

  @Override
  public <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue, Handler<AsyncResult<T>> resultHandler) {
    Future<T> fut = executeBlocking(blockingCodeHandler, queue);
    setResultHandler(this, fut, resultHandler);
  }

  @Override
  public final <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
    executeBlocking(blockingCodeHandler, true, resultHandler);
  }

  public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler) {
    return executeBlocking(blockingCodeHandler, true);
  }

  @Override
  public <T> PromiseInternal<T> promise() {
    return new PromiseImpl<>(this);
  }

  @Override
  public <T> PromiseInternal<T> promise(Handler<AsyncResult<T>> handler) {
    if (handler instanceof PromiseInternal) {
      return (PromiseInternal<T>) handler;
    } else {
      PromiseInternal<T> promise = promise();
      promise.future().onComplete(handler);
      return promise;
    }
  }

  @Override
  public <T> Future<T> succeededFuture() {
    return new SucceededFuture<>(this, null);
  }

  @Override
  public <T> Future<T> succeededFuture(T result) {
    return new SucceededFuture<>(this, result);
  }

  @Override
  public <T> Future<T> failedFuture(Throwable failure) {
    return new FailedFuture<>(this, failure);
  }

  @Override
  public <T> Future<T> failedFuture(String message) {
    return new FailedFuture<T>(this, message);
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

  public abstract CloseHooks closeHooks();

  private static <T> void setResultHandler(ContextInternal ctx, Future<T> fut, Handler<AsyncResult<T>> resultHandler) {
    if (resultHandler != null) {
      fut.onComplete(resultHandler);
    } else {
      fut.onFailure(ctx::reportException);
    }
  }
}
