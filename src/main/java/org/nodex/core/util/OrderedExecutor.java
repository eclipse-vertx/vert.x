/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core.util;

import java.util.LinkedList;
import java.util.concurrent.Executor;

public final class OrderedExecutor implements Executor {

  // @protectedby tasks
  private final LinkedList<Runnable> tasks = new LinkedList<Runnable>();

  // @protectedby tasks
  private boolean running;

  private final Executor parent;

  private final Runnable runner;

  /**
   * Construct a new instance.
   *
   * @param parent the parent executor
   */
  public OrderedExecutor(final Executor parent) {
    this.parent = parent;
    runner = new Runnable() {
      public void run() {
        for (; ; ) {
          final Runnable task;
          synchronized (tasks) {
            task = tasks.poll();
            if (task == null) {
              running = false;
              return;
            }
          }
          try {
            task.run();
          } catch (Throwable t) {
            t.printStackTrace();
          }
        }
      }
    };
  }

  /**
   * Run a task.
   *
   * @param command the task to run.
   */
  public void execute(final Runnable command) {
    synchronized (tasks) {
      tasks.add(command);
      if (!running) {
        running = true;
        parent.execute(runner);
      }
    }
  }
}
