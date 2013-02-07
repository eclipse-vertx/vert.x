import org.vertx.java.platform.Verticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ChildVerticle extends Verticle {

  @Override
  public void start() throws Exception {
    container.deployModule("testmod-deploy3");
  }

  @Override
  public void stop() throws Exception {
  }
}