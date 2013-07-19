import org.vertx.java.platform.Verticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ChildVerticle extends Verticle {

  @Override
  public void start() {
    container.deployVerticle("SubChildVerticle.java");
  }

  @Override
  public void stop() {
  }
}