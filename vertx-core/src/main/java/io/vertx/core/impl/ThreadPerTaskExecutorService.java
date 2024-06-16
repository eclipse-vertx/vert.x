/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPerTaskExecutorService extends AbstractExecutorService {

  private static final int ST_RUNNING = 0;
  private static final int ST_SHUTTING_DOWN = 1;
  private static final int ST_TERMINATED = 2;

  private final AtomicInteger state = new AtomicInteger();
  private final Set<Thread> threads = ConcurrentHashMap.newKeySet();
  private final CountDownLatch terminated = new CountDownLatch(1);
  private final ThreadFactory threadFactory;

  public ThreadPerTaskExecutorService(ThreadFactory threadFactory) {
    this.threadFactory = Objects.requireNonNull(threadFactory);
  }

  @Override
  public void shutdown() {
    shutdown(false);
  }

  @Override
  public List<Runnable> shutdownNow() {
    shutdown(true);
    return Collections.emptyList();
  }

  private void shutdown(boolean now) {
    if (state.get() == ST_RUNNING && state.compareAndSet(ST_RUNNING, ST_SHUTTING_DOWN)) {
      if (threads.isEmpty()) {
        state.set(ST_TERMINATED);
        terminated.countDown();
      } else {
        if (now) {
          for (Thread thread : threads) {
            thread.interrupt();
          }
        }
      }
    }
  }

  @Override
  public boolean isShutdown() {
    return state.get() != ST_RUNNING;
  }

  @Override
  public boolean isTerminated() {
    return state.get() == ST_TERMINATED;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return terminated.await(timeout, unit);
  }

  @Override
  public void execute(Runnable command) {
    Objects.requireNonNull(command);
    if (state.get() == ST_RUNNING) {
      Thread thread = threadFactory.newThread(() -> {
        try {
          command.run();
        } finally {
          threads.remove(Thread.currentThread());
          if (state.get() == ST_SHUTTING_DOWN && threads.isEmpty()) {
            if (state.compareAndSet(ST_SHUTTING_DOWN, ST_TERMINATED)) {
              terminated.countDown();
            }
          }
        }
      });
      threads.add(thread);
      thread.start();
    } else {
      throw new RejectedExecutionException();
    }
  }
}
