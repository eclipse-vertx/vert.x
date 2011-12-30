package org.vertx.java.newtests;

import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.logging.Logger;

import java.lang.reflect.Method;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class TestClientBase implements VertxApp {

  private static final Logger log = Logger.getLogger(TestClientBase.class);

  protected TestUtils tu = new TestUtils();

  private void registerTests() {
    Method[] methods = this.getClass().getMethods();
    for (final Method method: methods) {
      if (method.getName().startsWith("test")) {
        tu.register(method.getName(), new SimpleHandler() {
          public void handle() {
            try {
              method.invoke(TestClientBase.this, null);
            } catch (Exception e) {
              log.error("Failed to invoke test", e);
            }
          }
        });
      }
    }
  }

  public void start() {
    registerTests();
  }

  public void stop() {
    tu.unregisterAll();
    tu.appStopped();
  }
}
