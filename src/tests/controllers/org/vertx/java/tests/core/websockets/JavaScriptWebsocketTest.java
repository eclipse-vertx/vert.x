package org.vertx.java.tests.core.websockets;

import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptWebsocketTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/websocket/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testEchoBinary() {
    startTest(getMethodName());
  }

  public void testEchoText() {
    startTest(getMethodName());
  }

  public void testWriteFromConnectHandler() {
    startTest(getMethodName());
  }

  public void testClose() {
    startTest(getMethodName());
  }

  public void testCloseFromConnectHandler() {
    startTest(getMethodName());
  }

}
