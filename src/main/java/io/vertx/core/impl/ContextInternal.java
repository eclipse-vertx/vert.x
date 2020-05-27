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
import io.netty.util.concurrent.FastThreadLocalThread;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.*;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * This interface provides an api for vert.x core internal use only
 * It is not part of the public API and should not be used by
 * developers creating vert.x applications
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ContextInternal extends Context, Executor {

  /**
   * @return the current context
   */
  static ContextInternal current() {
    Thread current = Thread.currentThread();
    if (current instanceof VertxThread) {
      return ((VertxThread) current).context();
    } else if (current instanceof FastThreadLocalThread) {
      return AbstractContext.holderLocal.get().ctx;
    }
    return null;
  }

  /**
   * Return the Netty EventLoop used by this Context. This can be used to integrate
   * a Netty Server with a Vert.x runtime, specially the Context part.
   *
   * @return the EventLoop
   */
  EventLoop nettyEventLoop();

  /**
   * {@inheritDoc}
   * <br/>
   * Execution follows the same semantics than {@link #runOnContext(Handler)}, simply put it is equivalent
   * to {@code runOncontext(v -> command.run())}.
   */
  @Override
  void execute(Runnable command);

  /**
   * @return a {@link Promise} associated with this context
   */
  <T> PromiseInternal<T> promise();

  /**
   * @return a {@link Promise} associated with this context or the {@code handler}
   *         if that handler is already an instance of {@code PromiseInternal}
   */
  <T> PromiseInternal<T> promise(Handler<AsyncResult<T>> handler);

  /**
   * @return an empty succeeded {@link Future} associated with this context
   */
  <T> Future<T> succeededFuture();

  /**
   * @return a succeeded {@link Future} of the {@code result} associated with this context
   */
  <T> Future<T> succeededFuture(T result);

  /**
   * @return a {@link Future} failed with the {@code failure} associated with this context
   */
  <T> Future<T> failedFuture(Throwable failure);

  /**
   * @return a {@link Future} failed with the {@code message} associated with this context
   */
  <T> Future<T> failedFuture(String message);

  /**
   * Like {@link #executeBlocking(Handler, boolean, Handler)} but uses the {@code queue} to order the tasks instead
   * of the internal queue of this context.
   */
  <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue, Handler<AsyncResult<T>> resultHandler);

  /**
   * Like {@link #executeBlocking(Handler, boolean)} but uses the {@code queue} to order the tasks instead
   * of the internal queue of this context.
   */
  <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue);

  /**
   * Execute an internal task on the internal blocking ordered executor.
   */
  <T> void executeBlockingInternal(Handler<Promise<T>> action, Handler<AsyncResult<T>> resultHandler);

  <T> void executeBlockingInternal(Handler<Promise<T>> action, boolean ordered, Handler<AsyncResult<T>> resultHandler);

  /**
   * Like {@link #executeBlockingInternal(Handler, Handler)} but returns a {@code Future} of the asynchronous result
   */
  <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action);

  /**
   * Like {@link #executeBlockingInternal(Handler, boolean, Handler)} but returns a {@code Future} of the asynchronous result
   */
  <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action, boolean ordered);

  /**
   * @return the deployment associated with this context or {@code null}
   */
  Deployment getDeployment();

  @Override
  VertxInternal owner();

  /**
   * Dispatch the given {@code argument} to the {@code task} and switch on this context if necessary, this also associates the
   * current thread with the current context so {@link Vertx#currentContext()} returns this context.
   * <br/>
   * Any exception thrown from the {@literal task} will be reported on this context.
   * <br/>
   * Calling this method is equivalent to {@code schedule(v -> emit(argument, task))}
   *
   * @param argument the {@code task} argument
   * @param task the handler to execute with the {@code event} argument
   */
  <T> void dispatch(T argument, Handler<T> task);

  /**
   * @see #dispatch(Object, Handler)
   */
  void dispatch(Handler<Void> task);

  /**
   * @see #schedule(Object, Handler)
   */
  void schedule(Handler<Void> task);

  /**
   * Schedule a task to be executed on this context, the task will be executed according to the
   * context concurrency model.
   *
   * @param argument the {@code task} argument
   * @param task the task
   */
  <T> void schedule(T argument, Handler<T> task);

  /**
   * @see #emit(Handler)
   */
  void emit(Runnable handler);

  /**
   * @see #emit(Object, Handler)
   */
  void emit(Handler<Void> handler);

  /**
   * Emit an {@code event} to the {@code handler} on this context.
   * <p>
   * The handler is executed directly by the caller thread which must be a {@link VertxThread} or a {@link FastThreadLocalThread}.
   * <p>
   * The handler execution is monitored by the blocked thread checker.
   * <p>
   * This context is thread-local associated during the task execution.
   *
   * @param event the event for the {@code handler}
   * @param handler the handler to execute with the {@code event}
   */
  <E> void emit(E event, Handler<E> handler);

  /**
   * Begin the execution of a task on this context.
   * <p>
   * The task execution is monitored by the blocked thread checker.
   * <p>
   * This context is thread-local associated during the task execution.
   * <p>
   * You should not use this API directly, instead you should use {@link #emit(Object, Handler)}
   *
   * @return the previous context that shall be restored after or {@code null} if there is none
   * @throws IllegalStateException when the current thread of execution cannot execute this task
   */
  ContextInternal emitBegin();

  /**
   * End the execution of a task on this context, see {@link #emitBegin()}
   * <p>
   * You should not use this API directly, instead you should use {@link #emit(Object, Handler)}
   *
   * @param previous the previous context to restore or {@code null} if there is none
   * @throws IllegalStateException when the current thread of execution cannot execute this task
   */
  void emitEnd(ContextInternal previous);

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
   * @see Context#get(String)
   * @see Context#put(String, Object)
   */
  ConcurrentMap<Object, Object> contextData();

  /**
   * @return the {@link ConcurrentMap} used to store local context data
   */
  ConcurrentMap<Object, Object> localContextData();

  /**
   * @return the classloader associated with this context
   */
  ClassLoader classLoader();

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
   * Like {@link Vertx#setPeriodic(long, Handler)} except the periodic timer will fire on this context.
   */
  long setPeriodic(long delay, Handler<Long> handler);

  /**
   * Like {@link Vertx#setTimer(long, Handler)} except the timer will fire on this context.
   */
  long setTimer(long delay, Handler<Long> handler);

  /**
   * @return {@code true} when the context is associated with a deployment
   */
  boolean isDeployment();

  /**
   * Add a close hook.
   *
   * <p> The {@code hook} will be called when the associated resource needs to be released. Hooks are useful
   * for automatically cleanup resources when a Verticle is undeployed.
   *
   * @param hook the close hook
   */
  void addCloseHook(Closeable hook);

  /**
   * Remove a close hook.
   *
   * <p> This is called when the resource is released explicitly and does not need anymore a managed close.
   *
   * @param hook the close hook
   */
  void removeCloseHook(Closeable hook);

}
