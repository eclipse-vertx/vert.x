package org.vertx.java.tests.net;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptNetTest extends TestBase {

  private static final Logger log = Logger.getLogger(JavaScriptNetTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JS, "net/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void test1() throws Exception {
    startApp(AppType.JS, "net/echo_server.js");
    startTest(getMethodName());
  }


}
