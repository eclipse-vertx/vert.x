package org.vertx.java.tests.core.blockingaction;

import org.junit.Test;
import org.vertx.java.newtests.TestBase;
import vertx.tests.core.blockingaction.TestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaBlockingActionTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testBlockingAction() throws Exception {
    startApp(TestClient.class.getName());
    startTest(getMethodName());
  }


}
