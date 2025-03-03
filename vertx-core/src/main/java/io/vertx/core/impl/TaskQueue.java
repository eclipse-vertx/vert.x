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

import java.util.*;
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
  private final Set<ContinuationTask> continuations = new HashSet<>();
  private boolean closed;
  private Executor currentExecutor;
  private Thread currentThread;
  private ExecuteTask currentTask;

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
        if (task instanceof ContinuationTask) {
          ContinuationTask resume = (ContinuationTask) task;
          currentExecutor = resume.executor;
          currentThread = resume.thread;
          currentTask = resume.task;
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
        currentTask = execute;
        execute.runnable.run();
      } catch (Throwable t) {
        log.error("Caught unexpected Throwable", t);
      } finally {
        currentThread = null;
        currentTask = null;
      }
    }
  }

  /**
   * A task of this queue.
   */
  private interface Task {
  }

  /**
   * Return a continuation task for the current task execution.
   *
   * @return the controller
   * @throws IllegalStateException if the current thread is not currently being executed by the queue
   */
  private ContinuationTask continuationTask() {
    ExecuteTask task;
    Thread thread;
    Executor executor;
    synchronized (tasks) {
      if (Thread.currentThread() != currentThread) {
        throw new IllegalStateException();
      }
      thread = currentThread;
      executor = currentExecutor;
      task = currentTask;
    }
    return new ContinuationTask(task, thread, executor);
  }

  /**
   * Run a task.
   *
   * @param task the task to run.
   */
  public void execute(Runnable task, Executor executor) throws RejectedExecutionException {
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
   * Structure holding the queue state at close time.
   */
  public final static class CloseResult {

    private final Thread activeThread;
    private final Runnable activeTask;
    private final List<Runnable> suspendedTasks;
    private final List<Thread> suspendedThreads;

    private CloseResult(Thread activeThread,
                        Runnable activeTask,
                        List<Thread> suspendedThreads,
                        List<Runnable> suspendedTasks) {
      this.activeThread = activeThread;
      this.activeTask = activeTask;
      this.suspendedThreads = suspendedThreads;
      this.suspendedTasks = suspendedTasks;
    }

    /**
     * @return the thread that was active
     */
    public Thread activeThread() {
      return activeThread;
    }

    public Runnable activeTask() {
      return activeTask;
    }

    /**
     * @return the list of suspended threads
     */
    public List<Thread> suspendedThreads() {
      return suspendedThreads;
    }

    /**
     * @return the list of suspended tasks
     */
    public List<Runnable> suspendedTasks() {
      return suspendedTasks;
    }
  }

  /**
   * Close the queue.
   *
   * @return a structure of suspended threads and pending tasks
   */
  public CloseResult close() {
    List<Thread> suspendedThreads;
    List<Runnable> suspendedTasks;
    Thread activeThread;
    Runnable activeTask;
    synchronized (tasks) {
      if (closed) {
        throw new IllegalStateException("Already closed");
      }
      suspendedThreads = new ArrayList<>(continuations.size());
      suspendedTasks = new ArrayList<>(continuations.size());
      Iterator<Task> it = tasks.iterator();
      while (it.hasNext()) {
        Task task = it.next();
        if (task instanceof ContinuationTask) {
          ContinuationTask continuationTask = (ContinuationTask) task;
          suspendedThreads.add(continuationTask.thread);
          suspendedTasks.add(continuationTask.task.runnable);
          it.remove();
        }
      }
      for (ContinuationTask cont : continuations) {
        suspendedThreads.add(cont.thread);
        suspendedTasks.add(cont.task.runnable);
      }
      continuations.clear();
      activeThread = currentThread;
      activeTask = currentTask != null ? currentTask.runnable : null;
      currentExecutor = null;
      closed = true;
    }
    return new CloseResult(activeThread, activeTask, suspendedThreads, suspendedTasks);
  }

  private class ContinuationTask extends CountDownLatch implements Task, WorkerExecutor.Execution {

    private static final int ST_CREATED = 0, ST_SUSPENDED = 1, ST_RESUMED = 2;

    private final ExecuteTask task;
    private final Thread thread;
    private final Executor executor;
    private int status;
    private Runnable latch;

    public ContinuationTask(ExecuteTask task, Thread thread, Executor executor) {
      super(1);
      this.task = task;
      this.thread = thread;
      this.executor = executor;
      this.status = ST_CREATED;
    }

    @Override
    public void resume() {
      resume(() -> {});
    }

    @Override
    public void resume(Runnable callback) {
      synchronized (tasks) {
        if (closed) {
          return;
        }
        switch (status) {
          case ST_SUSPENDED:
            boolean removed = continuations.remove(this);
            assert removed;
            latch = () -> {
              callback.run();
              countDown();
            };
            if (currentExecutor != null) {
              tasks.addFirst(this);
              return;
            }
            currentExecutor = executor;
            currentThread = thread;
            currentTask = task;
            break;
          case ST_CREATED:
            // The current task still owns the queue
            assert currentExecutor == executor;
            assert currentThread == thread;
            assert currentTask == task;
            latch = callback;
            break;
          default:
            throw new IllegalStateException();
        }
        status = ST_RESUMED;
      }
      latch.run();
    }

    @Override
    public CountDownLatch trySuspend() {
      if (suspend()) {
        return this;
      } else {
        return null;
      }
    }

    private boolean suspend() {
      if (Thread.currentThread() != thread) {
        throw new IllegalStateException();
      }
      synchronized (tasks) {
        if (closed) {
          return false;
        }
        if (currentThread == null || currentThread != thread) {
          throw new IllegalStateException();
        }
        switch (status) {
          case ST_RESUMED:
            countDown();
            return false;
          case ST_SUSPENDED:
            throw new IllegalStateException();
        }
        status = ST_SUSPENDED;
        boolean added = continuations.add(this);
        assert added;
        currentThread = null;
        currentTask = null;
      }
      executor.execute(runner);
      return true;
    }
  }

  public WorkerExecutor.Execution current() {
    return continuationTask();
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
}
