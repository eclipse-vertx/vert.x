package org.vertx.java.tests.busmods.workqueue;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;
import vertx.tests.busmods.workqueue.TestClient;
import vertx.tests.busmods.workqueue.OrderProcessor;
import vertx.tests.busmods.workqueue.OrderQueue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaWorkQueueTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void test1() throws Exception {
    startApp(AppType.JAVA, OrderQueue.class.getName());
    int numProcessors = 10;
    for (int i = 0; i < numProcessors; i++) {
      startApp(AppType.JAVA, OrderProcessor.class.getName());
    }
    startApp(AppType.JAVA, TestClient.class.getName());
    startTest(getMethodName());
  }


}
