package vertx.tests.core.net;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EchoServerNoReady extends EchoServer {
  public EchoServerNoReady() {
    super(false);
  }
}
