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

import io.netty.channel.EventLoop;
import io.vertx.core.*;
import io.vertx.core.impl.future.FailedFuture;
import io.vertx.core.impl.future.PromiseImpl;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.future.SucceededFuture;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.impl.ContextImpl.setResultHandler;

/**
 * This interface provides an api for vert.x core internal use only
 * It is not part of the public API and should not be used by
 * developers creating vert.x applications
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ContextInternal extends Context {

  /**
   * @return the current context
   */
  static ContextInternal current() {
    Thread thread = Thread.currentThread();
    if (thread instanceof VertxThread) {
      return ((VertxThread) thread).context();
    } else {
      VertxImpl.ContextDispatch current = VertxImpl.nonVertxContextDispatch.get();
      if (current != null) {
        return current.context;
      }
    }
    return null;
  }

  @Override
  default void runOnContext(Handler<Void> action) {
    executor().execute(() -> dispatch(action));
  }

  /**
   * @return an executor that schedule a task on this context, the thread executing the task will not be associated with this context
   */
  Executor executor();

  /**
   * Return the Netty EventLoop used by this Context. This can be used to integrate
   * a Netty Server with a Vert.x runtime, specially the Context part.
   *
   * @return the EventLoop
   */
  EventLoop nettyEventLoop();

  /**
   * @return a {@link Promise} associated with this context
   */
  default <T> PromiseInternal<T> promise() {
    return new PromiseImpl<>(this);
  }

  /**
   * @return a {@link Promise} associated with this context or the {@code handler}
   *         if that handler is already an instance of {@code PromiseInternal}
   */
  default <T> PromiseInternal<T> promise(Handler<AsyncResult<T>> handler) {
    if (handler instanceof PromiseInternal) {
      PromiseInternal<T> promise = (PromiseInternal<T>) handler;
      if (promise.context() != null) {
        return promise;
      }
    }
    PromiseInternal<T> promise = promise();
    promise.future().onComplete(handler);
    return promise;
  }

  /**
   * @return an empty succeeded {@link Future} associated with this context
   */
  default <T> Future<T> succeededFuture() {
    return new SucceededFuture<>(this, null);
  }

  /**
   * @return a succeeded {@link Future} of the {@code result} associated with this context
   */
  default <T> Future<T> succeededFuture(T result) {
    return new SucceededFuture<>(this, result);
  }

  /**
   * @return a {@link Future} failed with the {@code failure} associated with this context
   */
  default <T> Future<T> failedFuture(Throwable failure) {
    return new FailedFuture<>(this, failure);
  }

  /**
   * @return a {@link Future} failed with the {@code message} associated with this context
   */
  default <T> Future<T> failedFuture(String message) {
    return new FailedFuture<>(this, message);
  }

  /**
   * Like {@link #executeBlocking(Handler, boolean, Handler)} but uses the {@code queue} to order the tasks instead
   * of the internal queue of this context.
   *
   * @deprecated instead use {@link #executeBlocking(Callable, TaskQueue)}
   */
  @Deprecated
  default <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue, Handler<AsyncResult<T>> resultHandler) {
    Future<T> fut = executeBlocking(blockingCodeHandler, queue);
    setResultHandler(this, fut, resultHandler);
  }

  /**
   * Like {@link #executeBlocking(Handler, boolean)} but uses the {@code queue} to order the tasks instead
   * of the internal queue of this context.
   */
  @Deprecated
  <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue);

  <T> Future<T> executeBlocking(Callable<T> blockingCodeHandler, TaskQueue queue);

  /**
   * Execute an internal task on the internal blocking ordered executor.
   */
  @Deprecated
  default <T> void executeBlockingInternal(Handler<Promise<T>> action, Handler<AsyncResult<T>> resultHandler) {
    Future<T> fut = executeBlockingInternal(action);
    setResultHandler(this, fut, resultHandler);
  }

  @Deprecated
  default <T> void executeBlockingInternal(Handler<Promise<T>> action, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
    Future<T> fut = executeBlockingInternal(action, ordered);
    setResultHandler(this, fut, resultHandler);
  }

  @Override
  default <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
    Future<T> fut = executeBlocking(blockingCodeHandler, ordered);
    setResultHandler(this, fut, resultHandler);
  }

  /**
   * Like {@link #executeBlockingInternal(Handler, Handler)} but returns a {@code Future} of the asynchronous result
   */
  <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action);

  <T> Future<T> executeBlockingInternal(Callable<T> action);

  /**
   * Like {@link #executeBlockingInternal(Handler, boolean, Handler)} but returns a {@code Future} of the asynchronous result
   */
  <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action, boolean ordered);

  <T> Future<T> executeBlockingInternal(Callable<T> action, boolean ordered);

  /**
   * @return the deployment associated with this context or {@code null}
   */
  Deployment getDeployment();

  @Override
  VertxInternal owner();

  boolean inThread();

  /**
   * Emit the given {@code argument} event to the {@code task} and switch on this context if necessary, this also associates the
   * current thread with the current context so {@link Vertx#currentContext()} returns this context.
   * <br/>
   * Any exception thrown from the {@literal task} will be reported on this context.
   * <br/>
   * Calling this method is equivalent to {@code execute(v -> dispatch(argument, task))}
   *
   * @param argument the {@code task} argument
   * @param task the handler to execute with the {@code event} argument
   */
  <T> void emit(T argument, Handler<T> task);

  /**
   * @see #emit(Object, Handler)
   */
  default void emit(Handler<Void> task) {
    emit(null, task);
  }

  /**
   * @see #execute(Object, Handler)
   */
  default void execute(Handler<Void> task) {
    execute(null, task);
  }

  /**
   * Execute the {@code task} on this context, it will be executed according to the
   * context concurrency model.
   *
   * @param task the task to execute
   */
  void execute(Runnable task);

  /**
   * Execute a {@code task} on this context, the task will be executed according to the
   * context concurrency model.
   *
   * @param argument the {@code task} argument
   * @param task the task to execute
   */
  <T> void execute(T argument, Handler<T> task);

  /**
   * @return whether the current thread is running on this context
   */
  default boolean isRunningOnContext() {
    return current() == this && inThread();
  }

  /**
   * @see #dispatch(Handler)
   */
  default void dispatch(Runnable handler) {
    ContextInternal prev = beginDispatch();
    try {
      handler.run();
    } catch (Throwable t) {
      reportException(t);
    } finally {
      endDispatch(prev);
    }
  }

  /**
   * @see #dispatch(Object, Handler)
   */
  default void dispatch(Handler<Void> handler) {
    dispatch(null, handler);
  }

  /**
   * Dispatch an {@code event} to the {@code handler} on this context.
   * <p>
   * The handler is executed directly by the caller thread which must be a context thread.
   * <p>
   * The handler execution is monitored by the blocked thread checker.
   * <p>
   * This context is thread-local associated during the task execution.
   *
   * @param event the event for the {@code handler}
   * @param handler the handler to execute with the {@code event}
   */
  default <E> void dispatch(E event, Handler<E> handler) {
    ContextInternal prev = beginDispatch();
    try {
      handler.handle(event);
    } catch (Throwable t) {
      reportException(t);
    } finally {
      endDispatch(prev);
    }
  }

  /**
   * Begin the execution of a task on this context.
   * <p>
   * The task execution is monitored by the blocked thread checker.
   * <p>
   * This context is thread-local associated during the task execution.
   * <p>
   * You should not use this API directly, instead you should use {@link #dispatch(Object, Handler)}
   *
   * @return the previous context that shall be restored after or {@code null} if there is none
   * @throws IllegalStateException when the current thread of execution cannot execute this task
   */
  default ContextInternal beginDispatch() {
    VertxImpl vertx = (VertxImpl) owner();
    return vertx.beginDispatch(this);
  }

  /**
   * End the execution of a task on this context, see {@link #beginDispatch()}
   * <p>
   * You should not use this API directly, instead you should use {@link #dispatch(Object, Handler)}
   *
   * @param previous the previous context to restore or {@code null} if there is none
   * @throws IllegalStateException when the current thread of execution cannot execute this task
   */
  default void endDispatch(ContextInternal previous) {
    VertxImpl vertx = (VertxImpl) owner();
    vertx.endDispatch(previous);
  }

  /**
   * Report an exception to this context synchronously.
   * <p>
   * The exception handler will be called when there is one, otherwise the exception will be logged.
   *
   * @param t the exception to report
   */
  void reportException(Throwable t);

  /**
   * @return the {@link ConcurrentMap} used to store context data
   * @see Context#get(Object)
   * @see Context#put(Object, Object)
   */
  ConcurrentMap<Object, Object> contextData();

  @SuppressWarnings("unchecked")
  @Override
  default <T> T get(Object key) {
    return (T) contextData().get(key);
  }

  @Override
  default void put(Object key, Object value) {
    contextData().put(key, value);
  }

  @Override
  default boolean remove(Object key) {
    return contextData().remove(key) != null;
  }

  /**
   * @return the {@link ConcurrentMap} used to store local context data
   */
  ConcurrentMap<Object, Object> localContextData();

  @SuppressWarnings("unchecked")
  @Override
  default <T> T getLocal(Object key) {
    return (T) localContextData().get(key);
  }

  @Override
  default void putLocal(Object key, Object value) {
    localContextData().put(key, value);
  }

  @Override
  default boolean removeLocal(Object key) {
    return localContextData().remove(key) != null;
  }

  /**
   * @return the classloader associated with this context
   */
  ClassLoader classLoader();

  /**
   * @return the context worker pool
   */
  WorkerPool workerPool();

  /**
   * @return the tracer for this context
   */
  VertxTracer tracer();

  /**
   * Returns a context sharing with this context
   * <ul>
   *   <li>the same concurrency</li>
   *   <li>the same exception handler</li>
   *   <li>the same context data</li>
   *   <li>the same deployment</li>
   *   <li>the same config</li>
   *   <li>the same classloader</li>
   * </ul>
   * <p>
   * The duplicate context has its own
   * <ul>
   *   <li>local context data</li>
   *   <li>worker task queue</li>
   * </ul>
   *
   * @return a duplicate of this context
   */
  ContextInternal duplicate();

  /**
   * Like {@link Vertx#setPeriodic(long, Handler)} except the periodic timer will fire on this context and the
   * timer will not be associated with the context close hook.
   */
  default long setPeriodic(long delay, Handler<Long> handler) {
    VertxImpl owner = (VertxImpl) owner();
    return owner.scheduleTimeout(this, true, delay, TimeUnit.MILLISECONDS, false, handler);
  }

  /**
   * Like {@link Vertx#setTimer(long, Handler)} except the timer will fire on this context and the timer
   * will not be associated with the context close hook.
   */
  default long setTimer(long delay, Handler<Long> handler) {
    VertxImpl owner = (VertxImpl) owner();
    return owner.scheduleTimeout(this, false, delay, TimeUnit.MILLISECONDS, false, handler);
  }

  /**
   * @return {@code true} when the context is associated with a deployment
   */
  default boolean isDeployment() {
    return getDeployment() != null;
  }

  default String deploymentID() {
    Deployment deployment = getDeployment();
    return deployment != null ? deployment.deploymentID() : null;
  }

  default int getInstanceCount() {
    Deployment deployment = getDeployment();

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

  CloseFuture closeFuture();

  /**
   * Add a close hook.
   *
   * <p> The {@code hook} will be called when the associated resource needs to be released. Hooks are useful
   * for automatically cleanup resources when a Verticle is undeployed.
   *
   * @param hook the close hook
   */
  default void addCloseHook(Closeable hook) {
    closeFuture().add(hook);
  }

  /**
   * Remove a close hook.
   *
   * <p> This is called when the resource is released explicitly and does not need anymore a managed close.
   *
   * @param hook the close hook
   */
  default void removeCloseHook(Closeable hook) {
    closeFuture().remove(hook);
  }

  /**
   * Returns the original context, a duplicate context returns the wrapped context otherwise this instance is returned.
   *
   * @return the wrapped context
   */
  default ContextInternal unwrap() {
    return this;
  }

  /**
   * Returns {@code true} if this context is a duplicated context.
   *
   * @return {@code true} if this context is a duplicated context, {@code false} otherwise.
   */
  default boolean isDuplicate() {
    return false;
  }
}
