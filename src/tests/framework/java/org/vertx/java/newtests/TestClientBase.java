package org.vertx.java.newtests;

import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class TestClientBase implements Verticle {

  private static final Logger log = LoggerFactory.getLogger(TestClientBase.class);

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
