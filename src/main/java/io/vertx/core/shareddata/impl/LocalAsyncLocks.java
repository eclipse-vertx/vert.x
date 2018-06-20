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
import io.vertx.core.shareddata.Lock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Thomas Segismont
 */
public class LocalAsyncLocks {

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
        handler.handle(Future.failedFuture("Timed out waiting to get lock"));
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

  // Value should never be modified
  private final ConcurrentMap<String, List<LockWaiter>> waitersMap = new ConcurrentHashMap<>();

  public void acquire(Context context, String name, long timeout, Handler<AsyncResult<Lock>> handler) {
    LockWaiter lockWaiter = new LockWaiter(context, name, timeout, handler);
    List<LockWaiter> waiters = waitersMap.compute(name, (s, list) -> {
      List<LockWaiter> result;
      if (list != null) {
        result = new ArrayList<>(list.size() + 1);
        result.addAll(list);
      } else {
        result = new ArrayList<>(1);
      }
      result.add(lockWaiter);
      return result;
    });
    if (waiters.size() == 1) {
      waiters.get(0).acquireLock();
    }
  }

  private void nextWaiter(String lockName) {
    List<LockWaiter> waiters = waitersMap.compute(lockName, (s, list) -> {
      return list == null || list.size() == 1 ? null : new ArrayList<>(list.subList(1, list.size()));
    });
    if (waiters != null) {
      waiters.get(0).acquireLock();
    }
  }
}
