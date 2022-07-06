/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.pool;

import io.netty.util.internal.PlatformDependent;

import java.time.Duration;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Lock free executor.
 * <p>
 * When a thread submits an action, it will enqueue the action to execute and then try to acquire
 * a lock. When the lock is acquired it will execute all the tasks in the queue until empty and then
 * release the lock.
 */
public class CombinerExecutor<S> implements Executor<S> {
  @FunctionalInterface
  interface YieldCondition {
    boolean yield(long actions);

    static YieldCondition withMaxDuration(final Duration duration) {
      return withMaxDuration(duration, System::nanoTime);
    }

    static YieldCondition withMaxDuration(final Duration duration, final LongSupplier nanoTimeSupplier) {
      return new YieldCondition() {
        private final long durationInNs = Objects.requireNonNull(duration).toNanos();
        private long start;

        @Override
        public boolean yield(long actions) {
          assert actions >= 0;
          if (actions == 0) {
            start = nanoTimeSupplier.getAsLong();
            return false;
          }
          final long elapsed = nanoTimeSupplier.getAsLong() - start;
          assert elapsed >= 0;
          return elapsed >= durationInNs;
        }
      };
    }

    static YieldCondition withMaxCount(final int maxActions) {
      if (maxActions < 0) {
        throw new IllegalArgumentException("maxActions must be >= 1");
      }
      return count -> count >= maxActions;
    }
  }

  private final Queue<Action<S>> q = PlatformDependent.newMpscQueue();
  private final AtomicInteger s = new AtomicInteger();
  private final S state;

  private final YieldCondition yieldCondition;

  private final Continuation resume;

  private final AtomicBoolean inflightContinuation = new AtomicBoolean();

  public CombinerExecutor(final S state) {
    this(state, null);
  }

  public CombinerExecutor(final S state, final YieldCondition yieldCondition) {
    this.state = state;
    this.resume = () -> {
      if (!inflightContinuation.compareAndSet(true, false)) {
        throw new IllegalStateException("Continuation cannot be resumed twice or without a prior yield");
      }
      return pollAndExecute();
    };
    this.yieldCondition = yieldCondition;
  }
  public int actions() {
    return q.size();
  }

  @Override
  public Continuation submitAndContinue(final Action<S> action) {
    Objects.requireNonNull(action);
    q.add(action);
    return pollAndExecute();
  }

  private Continuation pollAndExecute() {
    if (s.get() != 0 || !s.compareAndSet(0, 1)) {
      return null;
    }
    final YieldCondition condition = this.yieldCondition;
    Task head = null;
    Task tail = null;
    Continuation resume = null;
    long actions = 0;
    do {
      // single threaded
      boolean requiresResume = false;
      try {
        while (true) {
          final Action<S> a = q.poll();
          if (a == null) {
            break;
          }
          if (condition != null && actions == 0) {
            // we can ignore the value here
            condition.yield(0);
          }
          Task task = a.execute(state);
          if (task != null) {
            if (head == null) {
              assert tail == null;
              tail = task;
              head = task;
            } else {
              tail = tail.next(task);
            }
          }
          actions++;
          if (condition != null && condition.yield(actions)) {
            requiresResume = true;
            break;
          }
        }
      } finally {
        s.set(0);
        // no longer single threaded
      }
      // requiresResume == true doesn't mean 100% chances to resume: if others has already picked up
      // the action backlog or there wasn't any new actions already, there's no need to resume
      if (q.isEmpty()) {
        break;
      }
      if (requiresResume) {
        if (inflightContinuation.compareAndSet(false, true)) {
          resume = this.resume;
        }
        break;
      }
    } while (s.compareAndSet(0, 1));
    if (head != null) {
      head.runNextTasks();
    }
    return resume;
  }

}
