package org.vertx.java.tests.core.parsetools;

import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptRecordParserTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JS, "core/parsetools/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testDelimited() {
    startTest(getMethodName());
  }

  public void testFixed() {
    startTest(getMethodName());
  }
}
