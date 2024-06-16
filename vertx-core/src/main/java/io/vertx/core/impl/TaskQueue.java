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

package io.vertx.core.impl;

import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * A task queue that always run all tasks in order. The executor to run the tasks is passed
 * when the tasks are executed, this executor is not guaranteed to be used, as if several
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

  // @protectedby tasks
  private final LinkedList<Task> tasks = new LinkedList<>();
  private Executor currentExecutor;
  private Thread currentThread;

  private final Runnable runner;

  public TaskQueue() {
    runner = this::run;
  }

  private void run() {
    for (; ; ) {
      final ExecuteTask execute;
      synchronized (tasks) {
        Task task = tasks.poll();
        if (task == null) {
          currentExecutor = null;
          return;
        }
        if (task instanceof ResumeTask) {
          ResumeTask resume = (ResumeTask) task;
          currentExecutor = resume.executor;
          currentThread = resume.thread;
          resume.latch.run();
          return;
        }
        execute = (ExecuteTask) task;
        if (execute.exec != currentExecutor) {
          tasks.addFirst(execute);
          execute.exec.execute(runner);
          currentExecutor = execute.exec;
          return;
        }
      }
      try {
        currentThread = Thread.currentThread();
        execute.runnable.run();
      } catch (Throwable t) {
        log.error("Caught unexpected Throwable", t);
      } finally {
        currentThread = null;
      }
    }
  }

  /**
   * Return a controller for the current task.
   *
   * @return the controller
   * @throws IllegalStateException if the current thread is not currently being executed by the queue
   */
  public WorkerExecutor.TaskController current() {
    Thread thread;
    Executor executor;
    synchronized (tasks) {
      if (Thread.currentThread() != currentThread) {
        throw new IllegalStateException();
      }
      thread = currentThread;
      executor = currentExecutor;
    }
    return new WorkerExecutor.TaskController() {

      final CountDownLatch latch = new CountDownLatch(1);

      @Override
      public void resume(Runnable callback) {
        Runnable task = () -> {
          callback.run();
          latch.countDown();
        };
        synchronized (tasks) {
          if (currentExecutor != null) {
            tasks.addFirst(new ResumeTask(task, executor, thread));
            return;
          }
          currentExecutor = executor;
          currentThread = thread;
        }
        task.run();
      }

      @Override
      public CountDownLatch suspend() {
        if (Thread.currentThread() != thread) {
          throw new IllegalStateException();
        }
        synchronized (tasks) {
          if (currentThread == null || currentThread != Thread.currentThread()) {
            throw new IllegalStateException();
          }
          currentThread = null;
        }
        executor.execute(runner);
        return latch;
      }
    };
  }

  /**
   * Run a task.
   *
   * @param task the task to run.
   */
  public void execute(Runnable task, Executor executor) {
    synchronized (tasks) {
      if (currentExecutor == null) {
        currentExecutor = executor;
        try {
          executor.execute(runner);
        } catch (RejectedExecutionException e) {
          currentExecutor = null;
          throw e;
        }
      }
      // Add the task after the runner has been accepted to the executor
      // to cover the case of a rejected execution exception.
      tasks.add(new ExecuteTask(task, executor));
    }
  }

  /**
   * Test if the task queue is empty and no current executor is running anymore.
   */
  public boolean isEmpty() {
    synchronized (tasks) {
      return tasks.isEmpty() && currentExecutor == null;
    }
  }

  /**
   * A task of this queue.
   */
  private interface Task {
  }

  /**
   * Execute another task
   */
  private static class ExecuteTask implements Task {
    private final Runnable runnable;
    private final Executor exec;
    public ExecuteTask(Runnable runnable, Executor exec) {
      this.runnable = runnable;
      this.exec = exec;
    }
  }

  /**
   * Resume an existing task blocked on a thread
   */
  private static class ResumeTask implements Task {
    private final Runnable latch;
    private final Executor executor;
    private final Thread thread;
    ResumeTask(Runnable latch, Executor executor, Thread thread) {
      this.latch = latch;
      this.executor = executor;
      this.thread = thread;
    }
  }
}
