package org.vertx.java.tests.busmods.workqueue;

import org.junit.Test;
import org.vertx.java.newtests.TestBase;
import vertx.tests.busmods.workqueue.OrderProcessor;
import vertx.tests.busmods.workqueue.TestClient;

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
    startApp(TestClient.class.getName());
    int numProcessors = 10;
    for (int i = 0; i < numProcessors; i++) {
      startApp(OrderProcessor.class.getName());
    }
    startTest(getMethodName());
  }


}
