package org.vertx.java.tests.timer;

import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.newtests.TestBase;
import vertx.tests.timer.TestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaTimerTest extends TestBase {

private static final Logger log = Logger.getLogger(JavaTimerTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JAVA, TestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testOneOff() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testPeriodic() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testTimings() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testNoContext() throws Exception {
    try {
      Vertx.instance.setTimer(10, new Handler<Long>() {
        public void handle(Long timerID) {
        }
      });
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

}
