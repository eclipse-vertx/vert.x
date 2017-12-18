/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.Executor;

/**
 * A task queue that always run all tasks in order. The executor to run the tasks is passed when
 * the tasks when the tasks are executed, this executor is not guaranteed to be used, as if several
 * tasks are queued, the original thread will be used.
 *
 * More specifically, any call B to the {@link #execute(Runnable, Executor)} method that happens-after another call A to the
 * same method, will result in B's task running after A's.
 *
 * @author <a href="david.lloyd@jboss.com">David Lloyd</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TaskQueue {

  static final Logger log = LoggerFactory.getLogger(TaskQueue.class);

  private static class Task {

    private final Runnable runnable;
    private final Executor exec;

    public Task(Runnable runnable, Executor exec) {
      this.runnable = runnable;
      this.exec = exec;
    }
  }

  // @protectedby tasks
  private final LinkedList<Task> tasks = new LinkedList<>();

  // @protectedby tasks
  private Executor current;

  private final Runnable runner;

  public TaskQueue() {
    runner = this::run;
  }

  private void run() {
    for (; ; ) {
      final Task task;
      synchronized (tasks) {
        task = tasks.poll();
        if (task == null) {
          current = null;
          return;
        }
        if (task.exec != current) {
          tasks.addFirst(task);
          task.exec.execute(runner);
          current = task.exec;
          return;
        }
      }
      try {
        task.runnable.run();
      } catch (Throwable t) {
        log.error("Caught unexpected Throwable", t);
      }
    }
  };

  /**
   * Run a task.
   *
   * @param task the task to run.
   */
  public void execute(Runnable task, Executor executor) {
    synchronized (tasks) {
      tasks.add(new Task(task, executor));
      if (current == null) {
        current = executor;
        executor.execute(runner);
      }
    }
  }
}
