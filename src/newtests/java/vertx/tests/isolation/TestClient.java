package vertx.tests.isolation;

import org.vertx.java.newtests.TestClientBase;

import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 * Test that different instances of the same app can't see each other via statics
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

  private static final AtomicInteger counter = new AtomicInteger(0);

  public void testIsolation() {
    tu.azzert(counter.incrementAndGet() == 1);
    tu.testComplete();
  }


}
