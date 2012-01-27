package org.vertx.java.tests.core.websockets;

import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyWebsocketTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.RUBY, "core/websocket/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_websocket() {
    startTest(getMethodName());
  }

}
