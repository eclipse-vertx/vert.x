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
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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
   * @return a {@link Promise} associated with this context
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

  /**
   * Like {@link #executeBlockingInternal(Handler, Handler)} but returns a {@code Future} of the asynchronous result
   */
  <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action);

  /**
   * @return the deployment associated with this context or {@code null}
   */
  Deployment getDeployment();

  @Override
  VertxInternal owner();

  /**
   * @see #emit(Object, Handler)
   */
  void emitFromIO(Handler<Void> handler);

  /**
   * Emit the {@code event} to the {@code handler} and switch on this context if necessary, this also associates the
   * current thread with the current context so {@link Vertx#currentContext()} returns this context.<p/>
   *
   * The caller thread is assumed to be the event loop thread of this context.<p/>
   *
   * Any exception thrown from the {@literal handler} will be reported on this context.
   *
   * @param event the event for the {@code handler}
   * @param handler the handler to execute with the {@code event} argument
   */
  <T> void emitFromIO(T event, Handler<T> handler);

  /**
   * Emit the {@code event} to the {@code handler} and switch on this context if necessary, this also associates the
   * current thread with the current context so {@link Vertx#currentContext()} returns this context.<p/>
   *
   * Any exception thrown from the {@literal handler} will be reported on this context.
   *
   * @param event the event for the {@code handler}
   * @param handler the handler to execute with the {@code event} argument
   */
  <E> void emit(E event, Handler<E> handler);

  /**
   * @see #schedule(Object, Handler)
   */
  void schedule(Handler<Void> task);

  /**
   * Schedule a task to be executed on this context, the task will be executed according to the
   * context concurrency model, on an event-loop context, the task is executed directly, on a worker
   * context the task is executed on the worker thread pool.
   *
   * @param value the task value
   * @param task the task
   */
  <T> void schedule(T value, Handler<T> task);

  /**
   * @see #dispatch(Handler)
   */
  void dispatch(Runnable handler);

  /**
   * @see #dispatch(Object, Handler)
   */
  void dispatch(Handler<Void> handler);

  /**
   * Dispatch a {@code event} to the {@code handler} on this context. The handler is executed directly by the caller thread which must be a
   * {@link VertxThread}.
   * <p>
   * The handler execution is monitored by the blocked thread checker.
   * <p>
   * This context is thread-local associated during the task execution.
   *
   * @param event the event for the {@code handler}
   * @param handler the handler to execute with the {@code event} argument
   */
  <E> void dispatch(E event, Handler<E> handler);

  /**
   * Begin the dispatch of a task on this context.
   * <p>
   * The task execution is monitored by the blocked thread checker.
   * <p>
   * This context is thread-local associated during the task execution.
   *
   * @return the previous context that shall be restored after or {@code null} if there is none
   * @throws IllegalStateException when the current thread of execution cannot execute this task
   */
  ContextInternal beginDispatch();

  /**
   * End the dispatch of a task on this context.
   *
   * @param prev the previous context to restore or {@code null} if there is none
   * @throws IllegalStateException when the current thread of execution cannot execute this task
   */
  void endDispatch(ContextInternal prev);

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
   * Returns a context which shares the whole behavior of this context but not the {@link #localContextData()} which
   * remains private to the context:
   * <ul>
   *   <li>same concurrency</li>
   *   <li>same exception handler</li>
   *   <li>same context context</li>
   *   <li>same deployment</li>
   *   <li>same config</li>
   *   <li>same classloader</li>
   * </ul>
   * <p>
   * The duplicated context will have its own private local context data.
   *
   * @return a context whose behavior will is equivalent to this context but with new private
   */
  ContextInternal duplicate();

  /**
   * Like {@link #duplicate()} but the duplicated context local data will adopt the local data of the specified
   * {@code context} argument.
   */
  ContextInternal duplicate(ContextInternal context);

}
