package org.vertx.java.tests.core.isolation;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;
import vertx.tests.core.isolation.TestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaIsolationTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testIsolation() throws Exception {
    int numInstances = 10;
    for (int i = 0; i < numInstances; i++) {
      startApp(AppType.JAVA, TestClient.class.getName());
    }
    startApp(AppType.JAVA, TestClient.class.getName(), numInstances);
    startTest(getMethodName(), false);
    for (int i = 0; i < numInstances * 2; i++) {
      waitTestComplete();
    }
  }


}
