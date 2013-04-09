import org.vertx.java.platform.Verticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SubChildVerticle extends Verticle {

  @Override
  public void start() {
    vertx.eventBus().send("test-handler", "started");
  }

  @Override
  public void stop() {
    vertx.eventBus().send("test-handler", "stopped");
  }
}