package org.vertx.java.tests.redis;

import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyRedisTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("redis/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_method_with_buffer_arg() {
    startTest(getMethodName());
  }

  public void test_method_with_buffer_array_arg() {
    startTest(getMethodName());
  }

  public void test_method_with_buffer_array_ret() {
    startTest(getMethodName());
  }

}
