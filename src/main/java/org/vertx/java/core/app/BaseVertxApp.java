package org.vertx.java.core.app;

import org.vertx.java.core.Handler;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BaseVertxApp implements VertxApp {

  private Handler<Void> doneHandler;

  public void stop(Handler<Void> doneHandler) throws Exception {
    this.doneHandler = doneHandler;
    stop();
    stopAsync();
  }

  public void stop() throws Exception {
  }

  public void stopAsync() throws Exception {
    stopped();
  }

  public void stopped() {
    doneHandler.handle(null);
  }
}
