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

import java.util.concurrent.ConcurrentMap;

/**
 * This interface provides an api for vert.x core internal use only
 * It is not part of the public API and should not be used by
 * developers creating vert.x applications
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ContextInternal extends Context {

  static void setContext(ContextInternal context) {
    Thread current = Thread.currentThread();
    if (current instanceof VertxThread) {
      ((VertxThread)current).setContext(context);
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
   * @return the classloader associated with this context
   */
  ClassLoader classLoader();

}
