/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal;

import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.time.Duration;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>Helper class to manage a resource as a service, it encapsulates the logic behind
 * a bindable service interacting with a Vert.x context for cleanup purpose. This class makes this
 * reusable and testable independently.</p>
 *
 * <p>This class aims to be subclassed and implementors should implement {@link #startImpl(ContextInternal, Object)} and
 * {@link #stopImpl(ContextInternal, Object, Duration)}.</p>
 *
 * <p>The implementation serializes service operations.</p>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class ServiceResource<A, R> {

  private boolean started;
  private CloseableResource<?> hook;
  private final Lock lock;

  private Task<?> head;
  private Task<?> tail;
  private boolean inProgress;

  public ServiceResource() {
    this.started = false;
    this.lock = new ReentrantLock();
  }

  // Testing purpose
  protected final boolean hasPendingTasks() {
    lock.lock();
    try {
      return tail != null;
    } finally {
      lock.unlock();
    }
  }

  private void checkPendingTask() {
    lock.lock();
    Task<?> task;
    if (!inProgress && (task = head) != null) {
      inProgress = true;
      lock.unlock();
      task.begin();
    } else {
      lock.unlock();
    }
  }

  private void addLast(Task<?> update) {
    if (tail == null) {
      head = tail = update;
    } else {
      tail.next = update;
      tail = update;
    }
  }

  private abstract class Task<T> {
    final Promise<T> completion;
    Task<?> next;

    Task(Promise<T> completion) {
      this.completion = completion;
    }

    abstract void begin();

    final void end() {
      lock.lock();
      Task<?> n = next;
      if (n != null) {
        head = n;
        lock.unlock();
        n.begin();
      } else {
        head = tail = null;
        inProgress = false;
        lock.unlock();
      }
    }
  }

  /**
   * Start the service with the provided {@code context} and {@code args}
   * @param context the start context
   * @param args the start arguments
   * @return a future signaling the completion
   */
  public final Future<R> start(ContextInternal context, A args) {
    try {
      lock.lock();
      if (!inProgress) {
        if (started) {
          return context.failedFuture(new IllegalStateException());
        } else {
          Promise<R> completion = context.promise();
          Start start = new Start(completion, context, args);
          addLast(start);
          return completion.future();
        }
      } else {
        Promise<R> completion = context.promise();
        Start start = new Start(completion, context, args);
        addLast(start);
        return completion.future();
      }
    } finally {
      lock.unlock();
      checkPendingTask();
    }
  }

  /**
   * Calls {@code stop(context, Duration.ZERO, result)}.
   */
  public final Future<?> stop(ContextInternal context) {
    return stop(context, Duration.ZERO);
  }

  /**
   * Stop the service, the provided {@code context} is used to signal the return, the {@code timeout}
   * argument is transmitted to the underlying shutdown operation.
   *
   * @param context factory for creating the return future
   * @param timeout the stop timeout
   * @return a future signaling the completion
   */
  public final Future<Void> stop(ContextInternal context, Duration timeout) {
    try {
      lock.lock();
      if (!inProgress) {
        if (!started) {
          return context.succeededFuture();
        } else {
          Promise<Void> completion = context.promise();
          Stop stop = new Stop(completion, timeout);
          addLast(stop);
          return completion.future();
        }
      } else {
        Promise<Void> completion = context.promise();
        Stop stop = new Stop(completion, timeout);
        addLast(stop);
        return completion.future();
      }
    } finally {
      lock.unlock();
      checkPendingTask();
    }
  }

  private class Start extends Task<R> {

    private final ContextInternal context;
    private final A args;

    Start(Promise<R> completion, ContextInternal context, A args) {
      super(completion);
      this.context = context;
      this.args = args;
    }

    @Override
    void begin() {
      lock.lock();
      if (started) {
        lock.unlock();
        completion.fail(new IllegalStateException());
        end();
      } else {
        lock.unlock();
        Future<R> f = safeStart(context, args);
        f.onComplete(ar -> {
          if (ar.succeeded()) {
            // Remove map empty ????
            var res = context.registerResource(timeout -> safeStop(context, args, timeout));
            if (res == null) {
              // Owner stopped: we close immediately since we will not be able to receive close signal
              Future<Void> result2 = safeStop(context, args, Duration.ZERO);
              result2.onComplete(ar2 -> {
                completion.fail(new IllegalStateException("Owner undeployed or closed"));
                end();
              });
            } else {
              lock.lock();
              hook = res;
              started = true;
              lock.unlock();
              completion.succeed(ar.result());
              end();
            }
          } else {
            completion.fail(ar.cause());
            end();
          }
        });
      }
    }
  }

  private class Stop extends Task<Void> {

    private final Duration timeout;

    Stop(Promise<Void> completion, Duration timeout) {
      super(completion);
      this.timeout = timeout;
    }

    @Override
    void begin() {
      lock.lock();
      if (started) {
        Future<Void> f;
        Future<Void> h = hook.shutdown(timeout);
        hook = null;
        lock.unlock();
        f = h;
        f.onComplete(ar -> {
          lock.lock();
          started = false;
          lock.unlock();
          completion.succeed();
          end();
        });
      } else {
        lock.unlock();
        completion.succeed();
        end();
      }
    }
  }

  private Future<R> safeStart(ContextInternal context, A args) {
    try {
      return startImpl(context, args);
    } catch (Throwable failure) {
      return context.failedFuture(failure);
    }
  }

  private Future<Void> safeStop(ContextInternal context,  A args, Duration timeout) {
    try {
      return stopImpl(context, args, timeout).mapEmpty();
    } catch (Throwable failure) {
      return context.failedFuture(failure);
    }
  }

  /**
   * Implementation of start.
   *
   * @implSpec this returns a succeeded future
   * @param context the start context
   * @param args the start arguments
   * @return the future result
   */
  protected Future<R> startImpl(ContextInternal context, A args) {
    return context.succeededFuture();
  }

  /**
   * Implementation of stop.
   *
   * @implNote the provided {@code  context} is the context used to start the service
   * @implSpec this returns a succeeded future
   * @param context the stop context
   * @param args start args
   * @param timeout the stop timeout
   * @return the future result
   */
  protected Future<?> stopImpl(ContextInternal context, A args, Duration timeout) {
    return context.succeededFuture();
  }
}
