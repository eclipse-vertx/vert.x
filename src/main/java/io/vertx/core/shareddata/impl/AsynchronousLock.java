/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.shareddata.impl;

import io.vertx.core.*;
import io.vertx.core.shareddata.Lock;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsynchronousLock implements Lock {

  private final Vertx vertx;
  private final Queue<LockWaiter> waiters = new LinkedList<>();
  private boolean owned;

  public AsynchronousLock(Vertx vertx) {
    this.vertx = vertx;
  }

  public void acquire(long timeout, Handler<AsyncResult<Lock>> resultHandler) {
    Context context = vertx.getOrCreateContext();
    doAcquire(context, timeout, resultHandler);
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

  public void doAcquire(Context context, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
    synchronized (this) {
      if (!owned) {
        // We now have the lock
        owned = true;
        lockAcquired(context, resultHandler);
      } else {
        waiters.add(new LockWaiter(this, context, timeout, resultHandler));
      }
    }
  }

  private void lockAcquired(Context context, Handler<AsyncResult<Lock>> resultHandler) {
    context.runOnContext(v -> resultHandler.handle(Future.succeededFuture(this)));
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

  private static class LockWaiter {
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
        context.owner().setTimer(timeout, tid -> timedOut());
      }
    }

    void timedOut() {
      synchronized (lock) {
        if (!acquired) {
          timedOut = true;
          context.runOnContext(v -> resultHandler.handle(Future.failedFuture(new VertxException("Timed out waiting to get lock"))));
        }
      }
    }

    void acquire(AsynchronousLock lock) {
      acquired = true;
      lock.lockAcquired(context, resultHandler);
    }

  }
}
