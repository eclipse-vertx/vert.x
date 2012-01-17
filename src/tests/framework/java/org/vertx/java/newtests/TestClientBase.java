package org.vertx.java.newtests;

import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class TestClientBase implements VertxApp {

  private static final Logger log = Logger.getLogger(TestClientBase.class);

  protected TestUtils tu = new TestUtils();

  private boolean stopped;

  public void start() {
    tu.registerTests(this);
  }

  public void stop() {
    if (stopped) {
      throw new IllegalStateException("Already stopped");
    }
    tu.unregisterAll();
    tu.appStopped();
    stopped = true;
  }
}
