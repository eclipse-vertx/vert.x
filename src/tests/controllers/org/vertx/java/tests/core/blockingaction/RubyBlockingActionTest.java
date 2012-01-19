package org.vertx.java.tests.core.blockingaction;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyBlockingActionTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void test_blocking_action() throws Exception {
    startApp(AppType.RUBY, "blockingaction/test_client.rb");
    startTest(getMethodName());
  }


}
