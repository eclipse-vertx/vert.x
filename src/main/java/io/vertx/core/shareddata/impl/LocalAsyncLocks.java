/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.shareddata.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.shareddata.Lock;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

/**
 * @author Thomas Segismont
 */
public class LocalAsyncLocks {

  // Immutable
  private class AsyncLock implements Lock {

    final String name;
    final List<LockWaiter> waiters;

    AsyncLock(String name, LockWaiter waiter) {
      this.name = name;
      waiters = Collections.singletonList(waiter);
    }

    AsyncLock(String name, List<LockWaiter> waiters) {
      this.name = name;
      this.waiters = waiters;
    }

    boolean firstWaiter() {
      return waiters.size() == 1;
    }

    AsyncLock addWaiter(LockWaiter waiter) {
      return new AsyncLock(name, Stream.concat(waiters.stream(), Stream.of(waiter)).collect(toList()));
    }

    AsyncLock forNextWaiter() {
      if (waiters.size() > 1) {
        List<LockWaiter> lockWaiters = waiters.stream().skip(1).filter(LockWaiter::notTimedOut).collect(toList());
        if (!lockWaiters.isEmpty()) {
          return new AsyncLock(name, lockWaiters);
        }
      }
      return null;
    }

    void acquire() {
      LockWaiter waiter = waiters.get(0);
      if (!waiter.acquire(this)) {
        release();
      }
    }

    @Override
    public void release() {
      AsyncLock asyncLock = localLocks.compute(name, (name, lock) -> lock == null ? null : lock.forNextWaiter());
      if (asyncLock != null) {
        asyncLock.acquire();
      }
    }
  }

  private static class LockWaiter {
    final Context context;
    final Handler<AsyncResult<Lock>> handler;
    final Long timerId;
    volatile boolean timedOut;
    volatile boolean acquired;

    LockWaiter(Context context, Handler<AsyncResult<Lock>> handler, long timeout) {
      this.context = context;
      this.handler = handler;
      timerId = timeout != Long.MAX_VALUE ? context.owner().setTimer(timeout, tid -> timeout()) : null;
    }

    void timeout() {
      if (!acquired) {
        timedOut = true;
        context.runOnContext(v -> handler.handle(Future.failedFuture(new VertxException("Timed out waiting to get lock"))));
      }
    }

    boolean acquire(AsyncLock lock) {
      if (!timedOut) {
        if (timerId != null) {
          context.owner().cancelTimer(timerId);
        }
        acquired = true;
        context.runOnContext(v -> handler.handle(Future.succeededFuture(lock)));
      }
      return acquired;
    }

    boolean notTimedOut() {
      return !timedOut;
    }
  }

  private final ConcurrentMap<String, AsyncLock> localLocks = new ConcurrentHashMap<>();

  public void acquire(Context context, String name, long timeout, Handler<AsyncResult<Lock>> handler) {
    LockWaiter lockWaiter = new LockWaiter(context, handler, timeout);
    AsyncLock asyncLock = localLocks.compute(name, (lockName, lock) -> {
      if (lock == null) {
        return new AsyncLock(lockName, lockWaiter);
      }
      return lock.addWaiter(lockWaiter);
    });
    if (asyncLock.firstWaiter()) {
      asyncLock.acquire();
    }
  }
}
