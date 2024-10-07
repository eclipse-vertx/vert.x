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
package io.vertx.core.impl;

import io.netty.channel.EventLoop;
import io.vertx.core.Promise;

import java.util.List;

/**
 * Worker task queue
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class WorkerTaskQueue extends TaskQueue {

  public WorkerTaskQueue() {
  }

  /**
   * Shutdown the task queue.
   *
   * @param executor an executor that can block in order to join threads.
   * @param
   */
  void shutdown(EventLoop executor, Promise<Void> completion) {
    TaskQueue.CloseResult closeResult = close();

    // Reject all pending tasks
    List<Runnable> pendingTasks = closeResult.pendingTasks();
    for (Runnable pendingTask : pendingTasks) {
      WorkerTask pendingWorkerTask = (WorkerTask) pendingTask;
      pendingWorkerTask.reject();
    }

    // Maintain context invariant: serialize task execution while interrupting tasks
    class InterruptSequence {

      void cancelActiveTask() {

        Thread activeThread = closeResult.activeThread();
        if (activeThread != null) {
          activeThread.interrupt();
          WorkerTask activeTask = (WorkerTask) closeResult.activeTask();
          activeTask.onCompletion(() -> executor.execute(() -> cancelSuspended(0)));
        } else {
          cancelSuspended(0);
        }
      }

      void cancelSuspended(int idx) {
        int num = closeResult.suspendedThreads().size();
        if (idx < num) {
          Thread suspendedThread = closeResult.suspendedThreads().get(idx);
          WorkerTask suspendedTask = (WorkerTask) closeResult.suspendedTasks().get(idx);
          suspendedThread.interrupt();
          suspendedTask.onCompletion(() -> executor.execute(() -> cancelSuspended(idx + 1)));
        } else {
          completion.complete();
        }
      }

    }

    new InterruptSequence().cancelActiveTask();

  }
}
