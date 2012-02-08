package org.vertx.java.tests.core.eventbus;

import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptEventBusTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/eventbus/test_client.js");
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

  public void testEchoString() {
    startTest(getMethodName());
  }

  public void testEchoNumber1() {
    startTest(getMethodName());
  }

  public void testEchoNumber2() {
    startTest(getMethodName());
  }

  public void testEchoBooleanTrue() {
    startTest(getMethodName());
  }

  public void testEchoBooleanFalse() {
    startTest(getMethodName());
  }

  public void testEchoJson() {
    startTest(getMethodName());
  }

  public void testEchoNull() {
    startTest(getMethodName());
  }


}
