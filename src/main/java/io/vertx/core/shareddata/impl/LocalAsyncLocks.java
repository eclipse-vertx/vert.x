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

package io.vertx.core.shareddata.impl;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.shareddata.Lock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Thomas Segismont
 */
public class LocalAsyncLocks {

  private class LockWaiter {

    final ContextInternal context;
    final String lockName;
    final Promise<Lock> promise;
    final Long timerId;

    LockWaiter(ContextInternal context, String lockName, long timeout, Promise<Lock> promise) {
      this.lockName = lockName;
      this.promise = promise;
      this.context = context;
      timerId = timeout != Long.MAX_VALUE ? context.setTimer(timeout, tid -> timeout()) : null;
    }

    void timeout() {
      // Cleanup
      waitersMap.compute(lockName, (s, list) -> {
        int idx;
        if (list == null || (idx = list.indexOf(LockWaiter.this)) == -1) {
          // Already removed by release()
          return list;
        } else if (list.size() == 1) {
          return null;
        } else {
          int size = list.size();
          List<LockWaiter> n = new ArrayList<>(size - 1);
          if (idx > 0) {
            n.addAll(list.subList(0, idx));
          }
          if (idx + 1 < size) {
            n.addAll(list.subList(idx + 1, size));
          }
          return n;
        }
      });
      promise.fail("Timed out waiting to get lock");
    }

    void acquireLock() {
      if (timerId == null || context.owner().cancelTimer(timerId)) {
        promise.complete(new AsyncLock(lockName));
      } else {
        nextWaiter(lockName);
      }
    }
  }

  private class AsyncLock implements LockInternal {

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

    @Override
    public int waiters() {
      List<LockWaiter> waiters = waitersMap.get(lockName);
      return waiters == null ? 0 : waiters.size() - 1;
    }
  }

  // Value should never be modified
  private final ConcurrentMap<String, List<LockWaiter>> waitersMap = new ConcurrentHashMap<>();

  public Future<Lock> acquire(ContextInternal context, String name, long timeout) {
    Promise<Lock> promise = context.promise();
    LockWaiter lockWaiter = new LockWaiter(context, name, timeout, promise);
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
    return promise.future();
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
