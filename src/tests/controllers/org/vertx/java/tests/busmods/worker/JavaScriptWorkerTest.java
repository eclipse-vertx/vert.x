package org.vertx.java.tests.busmods.worker;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptWorkerTest extends TestBase {

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
    startApp(AppType.JS, "busmods/worker/test_client.js");
    startApp(true, AppType.JS, "busmods/worker/test_worker.js");
    startTest(getMethodName());
  }


}
