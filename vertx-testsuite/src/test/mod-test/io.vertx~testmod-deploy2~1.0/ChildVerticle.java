import org.vertx.java.platform.Verticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ChildVerticle extends Verticle {

  @Override
  public void start() {
    container.deployModule("io.vertx~testmod-deploy3~1.0");
  }

  @Override
  public void stop() {
  }
}