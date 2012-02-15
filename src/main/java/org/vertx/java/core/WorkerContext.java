package org.vertx.java.core;

import java.util.concurrent.Executor;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WorkerContext extends BaseContext {

  private final Executor bgExec;

  public WorkerContext(Executor bgExec) {
    this.bgExec = bgExec;
  }

  public void execute(final Runnable task) {
    bgExec.execute(new Runnable() {
      public void run() {
        wrapTask(task).run();
      }
    });
  }
}
