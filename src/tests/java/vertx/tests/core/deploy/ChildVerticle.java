package vertx.tests.core.deploy;

import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.eventbus.EventBus;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ChildVerticle implements Verticle {

  private EventBus eb = EventBus.instance;

  @Override
  public void start() throws Exception {
    eb.send("test-handler", "started");
  }

  @Override
  public void stop() throws Exception {
    eb.send("test-handler", "stopped");
  }
}
