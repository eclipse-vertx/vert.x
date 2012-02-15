package vertx.tests.core.logger;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.newtests.TestClientBase;

/**
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testLogger() {

    Logger logger = Vertx.instance.getLogger();
    tu.azzert(logger != null);

    //Not much we can do to test this
    logger.info("foo");

    tu.testComplete();
  }

}
