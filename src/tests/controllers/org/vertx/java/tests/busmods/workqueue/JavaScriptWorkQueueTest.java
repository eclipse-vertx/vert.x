package org.vertx.java.tests.busmods.workqueue;

import org.junit.Test;
import org.vertx.java.core.app.VerticleType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptWorkQueueTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testWorkQueue() throws Exception {
    startApp(VerticleType.JS, "busmods/workqueue/order_queue.js");
    int numProcessors = 10;
    for (int i = 0; i < numProcessors; i++) {
      startApp(true, VerticleType.JS, "busmods/workqueue/order_processor.js");
    }
    startApp(VerticleType.JS, "busmods/workqueue/test_client.js");
    startTest(getMethodName());
  }


}
