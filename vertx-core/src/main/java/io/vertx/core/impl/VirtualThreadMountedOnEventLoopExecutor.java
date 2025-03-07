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

import io.netty.channel.EventLoop;
import io.netty.util.internal.PlatformDependent;
import io.vertx.core.internal.EventExecutor;

import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Execute events on a virtual threads mounted on event-loop.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VirtualThreadMountedOnEventLoopExecutor implements EventExecutor {

  private final ThreadLocal<Boolean> inThread = new ThreadLocal<>();
  private final ExecutorService executor;
  private boolean closed;
  private Queue<Runnable> continuations = PlatformDependent.newMpscQueue();

  // Flag indicating a task submission
  // Should use scoped values but requires enable preview flag which requires manual config in IDEA that does
  // not seem to recognize the enablePreview flag of the Maven compiler (sadly)
  private final ThreadLocal<Boolean> submission = new ThreadLocal<>();

  public VirtualThreadMountedOnEventLoopExecutor(EventLoop carrier) {
    ThreadFactory threadFactory = Thread.ofVirtual()
      .name("vert.x-virtual-thread-")
      .scheduler(task -> {
//        if (carrier.inEventLoop()) {
//          task.run();
//          return;
//        }
        boolean isContinuation = null == submission.get();
        if (isContinuation) {
          continuations.add(task);
        }
        carrier.execute(() -> {
          // Continuation, must be executed first
          Runnable continuation;
          while ((continuation = continuations.poll()) != null) {
            continuation.run();
          }
          if (!isContinuation) {
            task.run();
          }
        });
      }).factory();
    this.executor = new ThreadPerTaskExecutorService(threadFactory);
  }

  @Override
  public boolean inThread() {
    return Boolean.TRUE == inThread.get();
  }

  @Override
  public void execute(Runnable command) {
    if (closed) {
      throw new IllegalArgumentException();
    }
    submission.set(true); // Visible to scheduler, use a thread local because of concurrent executes
    try {
      executor.execute(() -> {
        inThread.set(true);
        try {
          command.run();
        } finally {
          inThread.remove();
        }
      });
    } finally {
      submission.remove();
    }
  }

  public void close() {
    closed = true;
    executor.shutdownNow(); // Interrupt threads
  }
}
