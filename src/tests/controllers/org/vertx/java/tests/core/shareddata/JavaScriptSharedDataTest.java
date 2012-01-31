package org.vertx.java.tests.core.shareddata;

import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptSharedDataTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JS, "core/shareddata/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testMap() {
    startTest(getMethodName());
  }

  public void testSet() {
    startTest(getMethodName());
  }
}
