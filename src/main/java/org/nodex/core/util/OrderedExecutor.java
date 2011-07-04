package org.nodex.core.util;

import java.util.LinkedList;
import java.util.concurrent.Executor;

/**
 * User: tfox
 * Date: 04/07/11
 * Time: 14:51
 *
 * @author <a href="david.lloyd@jboss.com">David Lloyd</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 */
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
