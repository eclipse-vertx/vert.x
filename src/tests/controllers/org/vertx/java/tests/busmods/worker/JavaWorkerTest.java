package org.vertx.java.tests.busmods.worker;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;
import vertx.tests.busmods.worker.TestClient;
import vertx.tests.busmods.worker.TestWorker;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaWorkerTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testWorker() throws Exception {
    startApp(AppType.JAVA, TestClient.class.getName());
    startApp(true, AppType.JAVA, TestWorker.class.getName());
    startTest(getMethodName());
  }


}
