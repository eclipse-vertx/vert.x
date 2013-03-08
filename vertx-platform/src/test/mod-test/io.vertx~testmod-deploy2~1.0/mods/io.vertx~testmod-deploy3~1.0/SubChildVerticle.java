import org.vertx.java.platform.Verticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SubChildVerticle extends Verticle {

  @Override
  public void start() throws Exception {
    vertx.eventBus().send("test-handler", "started");
  }

  @Override
  public void stop() throws Exception {
    vertx.eventBus().send("test-handler", "stopped");
  }
}