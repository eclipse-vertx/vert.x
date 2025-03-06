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
import io.vertx.core.internal.EventExecutor;

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

  public VirtualThreadMountedOnEventLoopExecutor(EventLoop carrier) {
    ThreadFactory threadFactory = Thread.ofVirtual()
      .name("vert.x-virtual-thread-")
      .scheduler(vTask -> {
        if (carrier.inEventLoop()) {
          vTask.run();
        } else {
          carrier.execute(vTask);
        }
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
    executor.execute(() -> {
      inThread.set(true);
      try {
        command.run();
      } finally {
        inThread.remove();
      }
    });
  }

  public void close() {
    closed = true;
    executor.shutdownNow(); // Interrupt threads
  }
}
