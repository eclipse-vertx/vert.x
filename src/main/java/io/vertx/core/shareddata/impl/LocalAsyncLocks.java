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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

/**
 * @author Thomas Segismont
 */
public class LocalAsyncLocks {

  // Immutable
  private static class WaitersList {

    final List<LockWaiter> waiters;

    WaitersList(List<LockWaiter> waiters) {
      this.waiters = waiters;
    }

    int size() {
      return waiters.size();
    }

    LockWaiter first() {
      return waiters.get(0);
    }

    WaitersList add(LockWaiter waiter) {
      return new WaitersList(Stream.concat(waiters.stream(), Stream.of(waiter)).collect(toList()));
    }

    WaitersList removeStale() {
      if (waiters.size() > 1) {
        List<LockWaiter> lockWaiters = this.waiters.stream().skip(1).filter(LockWaiter::isWaiting).collect(toList());
        if (!lockWaiters.isEmpty()) {
          return new WaitersList(lockWaiters);
        }
      }
      return null;
    }
  }

  private enum Status {WAITING, ACQUIRED, TIMED_OUT}

  private class LockWaiter {

    final Context context;
    final String lockName;
    final Handler<AsyncResult<Lock>> handler;
    final AtomicReference<Status> status;
    final Long timerId;

    LockWaiter(Context context, String lockName, long timeout, Handler<AsyncResult<Lock>> handler) {
      this.context = context;
      this.lockName = lockName;
      this.handler = handler;
      status = new AtomicReference<>(Status.WAITING);
      timerId = timeout != Long.MAX_VALUE ? context.owner().setTimer(timeout, tid -> timeout()) : null;
    }

    boolean isWaiting() {
      return status.get() == Status.WAITING;
    }

    void timeout() {
      if (status.compareAndSet(Status.WAITING, Status.TIMED_OUT)) {
        handler.handle(Future.failedFuture(new VertxException("Timed out waiting to get lock")));
      }
    }

    void acquireLock() {
      if (status.compareAndSet(Status.WAITING, Status.ACQUIRED)) {
        if (timerId != null) {
          context.owner().cancelTimer(timerId);
        }
        context.runOnContext(v -> handler.handle(Future.succeededFuture(new AsyncLock(lockName))));
      } else {
        context.runOnContext(v -> nextWaiter(lockName));
      }
    }
  }

  private class AsyncLock implements Lock {
    final String lockName;
    final AtomicBoolean invoked = new AtomicBoolean();

    AsyncLock(String lockName) {
      this.lockName = lockName;
    }

    @Override
    public void release() {
      if (invoked.compareAndSet(false, true)) {
        nextWaiter(lockName);
      }
    }
  }

  private final ConcurrentMap<String, WaitersList> waitersMap = new ConcurrentHashMap<>();

  public void acquire(Context context, String name, long timeout, Handler<AsyncResult<Lock>> handler) {
    LockWaiter lockWaiter = new LockWaiter(context, name, timeout, handler);
    WaitersList waiters = waitersMap.compute(name, (s, list) -> {
      return list == null ? new WaitersList(Collections.singletonList(lockWaiter)) : list.add(lockWaiter);
    });
    if (waiters.size() == 1) {
      waiters.first().acquireLock();
    }
  }

  private void nextWaiter(String lockName) {
    WaitersList waiters = waitersMap.compute(lockName, (s, list) -> list == null ? null : list.removeStale());
    if (waiters != null) {
      waiters.first().acquireLock();
    }
  }
}
