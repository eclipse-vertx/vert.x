/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.it.eventexecutor;

import io.vertx.core.spi.context.executor.EventExecutorProvider;

import java.util.Deque;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class CustomEventExecutorProvider implements EventExecutorProvider, java.util.concurrent.Executor {

  private static final Deque<Runnable> tasks = new LinkedList<>();

  static synchronized boolean hasNext() {
    return !tasks.isEmpty();
  }

  synchronized static Runnable next() {
    Runnable task = tasks.poll();
    if (task != null) {
      return new Runnable() {
        boolean executed;
        @Override
        public void run() {
          synchronized (CustomEventExecutorProvider.class) {
            if (executed) {
              throw new IllegalStateException();
            }
            executed = true;
            task.run();
          }
        }
      };
    }
    throw new NoSuchElementException();
  }

  @Override
  public void execute(Runnable command) {
    synchronized (CustomEventExecutorProvider.class) {
      tasks.add(command);
    }
  }

  @Override
  public java.util.concurrent.Executor eventExecutorFor(Thread thread) {
    if (thread instanceof CustomThread) {
      return this;
    } else {
      return null;
    }
  }
}
