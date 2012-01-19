package org.vertx.java.tests.core.eventbus;

import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptEventBusTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JS, "eventbus/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testSimple() {
    startTest(getMethodName());
  }

  public void testUnregister() {
    startTest(getMethodName());
  }

  public void testWithReply() {
    startTest(getMethodName());
  }

  public void testEmptyReply() {
    startTest(getMethodName());
  }

  public void testEmptyMessage() {
    startTest(getMethodName());
  }

}
