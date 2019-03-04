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

import io.netty.channel.EventLoop;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.ConcurrentMap;

/**
 * This interface provides an api for vert.x core internal use only
 * It is not part of the public API and should not be used by
 * developers creating vert.x applications
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ContextInternal extends Context {

  /**
   * Begin the dispatch of a context task.
   * <p>
   * This is a low level interface that should not be used, instead {@link ContextInternal#dispatch(Object, Handler)}
   * shall be used.
   *
   * @param ctx the context on which the task is dispatched on
   * @return the current vertx thread
   * @throws IllegalStateException when the current thread is not a vertx thread
   */
  static VertxThread beginDispatch(ContextInternal ctx) {
    Thread th = Thread.currentThread();
    if (!(th instanceof VertxThread)) {
      throw new IllegalStateException("Uh oh! context executing with wrong thread! " + th);
    }
    VertxThread current = (VertxThread) th;
    if (!ContextImpl.DISABLE_TIMINGS) {
      current.executeStart();
    }
    current.setContext(ctx);
    return current;
  }

  /**
   * End the dispatch of a context task.
   * <p>
   * This is a low level interface that should not be used, instead {@link ContextInternal#dispatch(Object, Handler)}
   * shall be used.
   *
   * @param current the current vertx thread
   */
  static void endDispatch(VertxThread current) {
    // We don't unset the context after execution - this is done later when the context is closed via
    // VertxThreadFactory
    current.setContext(null);
    if (!ContextImpl.DISABLE_TIMINGS) {
      current.executeEnd();
    }
  }

  static ContextInternal setContext(ContextInternal context) {
    Thread current = Thread.currentThread();
    if (current instanceof VertxThread) {
      return ((VertxThread)current).setContext(context);
    } else {
      throw new IllegalStateException("Attempt to setContext on non Vert.x thread " + Thread.currentThread());
    }
  }

  static boolean isOnWorkerThread() {
    return ContextImpl.isOnVertxThread(true);
  }

  static boolean isOnEventLoopThread() {
    return ContextImpl.isOnVertxThread(false);
  }

  static boolean isOnVertxThread() {
    Thread t = Thread.currentThread();
    return (t instanceof VertxThread);
  }

  /**
   * Return the Netty EventLoop used by this Context. This can be used to integrate
   * a Netty Server with a Vert.x runtime, specially the Context part.
   *
   * @return the EventLoop
   */
  EventLoop nettyEventLoop();

  /**
   * Like {@link #executeBlocking(Handler, boolean, Handler)} but uses the {@code queue} to order the tasks instead
   * of the internal queue of this context.
   */
  <T> void executeBlocking(Handler<Future<T>> blockingCodeHandler, TaskQueue queue, Handler<AsyncResult<T>> resultHandler);

  /**
   * Execute an internal task on the internal blocking ordered executor.
   */
  <T> void executeBlockingInternal(Handler<Future<T>> action, Handler<AsyncResult<T>> resultHandler);

  /**
   * @return the deployment associated with this context or {@code null}
   */
  Deployment getDeployment();

  @Override
  VertxInternal owner();

  /**
   * Like {@link #executeFromIO(Object, Handler)} but with no argument.
   */
  void executeFromIO(Handler<Void> task);

  /**
   * Execute the context task and switch on this context if necessary, this also associates the
   * current thread with the current context so {@link Vertx#currentContext()} returns this context.<p/>
   *
   * The caller thread should be the the event loop thread of this context.<p/>
   *
   * Any exception thrown from the {@literal task} will be reported on this context.
   *
   * @param value the argument for the {@code task}
   * @param task the task to execute with the {@code value} argument
   */
  <T> void executeFromIO(T value, Handler<T> task);

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
   * @see #dispatch(Object, Handler)
   */
  void dispatch(Handler<Void> task);

  /**
   * Dispatch a task on this context. The task is executed directly by the caller thread which must be a
   * {@link VertxThread}.
   * <p>
   * The task execution is monitored by the blocked thread checker.
   * <p>
   * This context is thread-local associated during the task execution.
   *
   * @param arg the task argument
   * @param task the task to execute
   */
  <T> void dispatch(T arg, Handler<T> task);

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
