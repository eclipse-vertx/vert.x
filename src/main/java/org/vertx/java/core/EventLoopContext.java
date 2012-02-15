package org.vertx.java.core;

import org.jboss.netty.channel.socket.nio.NioWorker;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventLoopContext extends BaseContext {

  private final NioWorker worker;

  public EventLoopContext(NioWorker worker) {
    this.worker = worker;
  }

  public void execute(Runnable task) {
    worker.scheduleOtherTask(wrapTask(task));
  }

  public NioWorker getWorker() {
    return worker;
  }
}
