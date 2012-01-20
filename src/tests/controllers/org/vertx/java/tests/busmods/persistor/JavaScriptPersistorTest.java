package org.vertx.java.tests.busmods.persistor;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;

/**
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptPersistorTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(true, AppType.JS, "busmods/persistor/test_persistor.js");
    startApp(AppType.JS, "busmods/persistor/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testSimple() throws Exception {
    startTest(getMethodName());
  }


}
