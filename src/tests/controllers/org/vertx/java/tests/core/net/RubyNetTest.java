package org.vertx.java.tests.core.net;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyNetTest extends TestBase {

  private static final Logger log = Logger.getLogger(RubyNetTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void test_echo() throws Exception {
    startApp(AppType.RUBY, "net/test_client.rb");
    startTest(getMethodName());
  }

  @Test
  public void test_echo_ssl() throws Exception {
    startApp(AppType.RUBY, "net/test_client.rb");
    startTest(getMethodName());
  }

  @Test
  public void test_methods() throws Exception {
    startApp(AppType.RUBY, "net/test_client.rb");
    startTest(getMethodName());
  }


}
