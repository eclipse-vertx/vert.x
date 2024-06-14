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
package io.vertx.core.internal.pool;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lock free executor.
 *
 * When a thread submits an action, it will enqueue the action to execute and then try to acquire
 * a lock. When the lock is acquired it will execute all the tasks in the queue until empty and then
 * release the lock.
 */
public class CombinerExecutor<S> implements Executor<S> {

  private final Queue<Action<S>> q = PlatformDependent.newMpscQueue();
  private final AtomicInteger s = new AtomicInteger();
  private final S state;

  protected static final class InProgressTail<S> {

    final CombinerExecutor<S> combiner;
    Task task;
    Map<CombinerExecutor<S>, Task> others;

    public InProgressTail(CombinerExecutor<S> combiner, Task task) {
      this.combiner = combiner;
      this.task = task;
    }
  }

  private static final FastThreadLocal<InProgressTail<?>> current = new FastThreadLocal<>();

  public CombinerExecutor(S state) {
    this.state = state;
  }

  @Override
  public void submit(Action<S> action) {
    q.add(action);
    if (s.get() != 0 || !s.compareAndSet(0, 1)) {
      return;
    }
    Task head = null;
    Task tail = null;
    do {
      try {
        for (; ; ) {
          final Action<S> a = q.poll();
          if (a == null) {
            break;
          }
          final Task task = a.execute(state);
          if (task != null) {
            Task last = task.last();
            if (head == null) {
              assert tail == null;
              tail = last;
              head = task;
            } else {
              tail.next(task);
              tail = last;
            }
          }
        }
      } finally {
        s.set(0);
      }
    } while (!q.isEmpty() && s.compareAndSet(0, 1));
    if (head != null) {
      InProgressTail<S> inProgress = (InProgressTail<S>) current.get();
      if (inProgress == null) {
        inProgress = new InProgressTail<>(this, tail);
        current.set(inProgress);
        try {
          // from now one cannot trust tail anymore
          head.runNextTasks();
          assert inProgress.others == null || inProgress.others.isEmpty();
        } finally {
          current.remove();
        }
      } else {
        if (inProgress.combiner == this) {
          Task oldNextTail = inProgress.task.replaceNext(head);
          assert oldNextTail == null;
          inProgress.task = tail;
        } else {
          Map<CombinerExecutor<S>, Task> map = inProgress.others;
          if (map == null) {
            map = inProgress.others = new HashMap<>(1);
          }
          Task task = map.get(this);
          if (task == null) {
            map.put(this, tail);
            try {
              // from now one cannot trust tail anymore
              head.runNextTasks();
            } finally {
              map.remove(this);
            }
          } else {
            Task oldNextTail = task.replaceNext(head);
            assert oldNextTail == null;
            map.put(this, tail);
          }
        }
      }
    }
  }
}
