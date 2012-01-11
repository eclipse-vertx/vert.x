package org.vertx.java.tests.globalhandlers;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.newtests.TestBase;
import vertx.tests.globalhandlers.TestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaGlobalHandlerTest extends TestBase {

private static final Logger log = Logger.getLogger(JavaGlobalHandlerTest.class);

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

}
