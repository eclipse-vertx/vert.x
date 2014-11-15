/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.shareddata.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.Lock;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsynchronousLock implements Lock {

  private final VertxInternal vertx;
  private Queue<LockWaiter> waiters = new LinkedList<>();
  private boolean owned;

  public AsynchronousLock(VertxInternal vertx) {
    this.vertx = vertx;
  }

  public void acquire(long timeout, Handler<AsyncResult<Lock>> resultHandler) {
    Context context = vertx.getOrCreateContext();
    synchronized (this) {
      if (!owned) {
        // We now have the lock
        owned = true;
        lockAquired(context, resultHandler);
      } else {
        waiters.add(new LockWaiter(this, context, timeout, resultHandler));
      }
    }
  }

  @Override
  public synchronized void release() {
    LockWaiter waiter = pollWaiters();
    if (waiter != null) {
      waiter.acquire(this);
    } else {
      owned = false;
    }
  }

  private void lockAquired(Context context, Handler<AsyncResult<Lock>> resultHandler) {
    context.runOnContext(v -> resultHandler.handle(Future.completedFuture(this)));
  }

  private LockWaiter pollWaiters() {
    while (true) {
      LockWaiter waiter = waiters.poll();
      if (waiter == null) {
        return null;
      } else if (!waiter.timedOut) {
        return waiter;
      }
    }
  }

  private class LockWaiter {
    final AsynchronousLock lock;
    final Context context;
    final Handler<AsyncResult<Lock>> resultHandler;
    volatile boolean timedOut;
    volatile boolean acquired;

    LockWaiter(AsynchronousLock lock, Context context, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
      this.lock = lock;
      this.context = context;
      this.resultHandler = resultHandler;
      if (timeout != Long.MAX_VALUE) {
        vertx.setTimer(timeout, tid -> timedOut());
      }
    }

    void timedOut() {
      synchronized (lock) {
        if (!acquired) {
          timedOut = true;
          context.runOnContext(v -> resultHandler.handle(Future.completedFuture(new VertxException("Timed out waiting to get lock"))));
        }
      }
    }

    void acquire(AsynchronousLock lock) {
      acquired = true;
      lock.lockAquired(context, resultHandler);
    }

  }
}
