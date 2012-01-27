package org.vertx.java.tests.core.composition;

import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyCompositionTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.RUBY, "core/composition/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_sync_blocks() {
    startTest(getMethodName());
  }

  public void test_with_futures() {
    startTest(getMethodName());
  }
}

