package org.vertx.java.tests.core.eventbus;

import org.vertx.java.core.app.VerticleType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyEventBusTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(VerticleType.RUBY, "core/eventbus/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_simple_send() {
    startTest(getMethodName());
  }

  public void test_send_empty() {
    startTest(getMethodName());
  }

  public void test_reply() {
    startTest(getMethodName());
  }

  public void test_empty_reply() {
    startTest(getMethodName());
  }

  public void test_send_unregister_send() {
    startTest(getMethodName());
  }

  public void test_send_multiple_matching_handlers() {
    startTest(getMethodName());
  }

  public void test_echo_string() {
    startTest(getMethodName());
  }

  public void test_echo_fixnum() {
    startTest(getMethodName());
  }

  public void test_echo_float() {
    startTest(getMethodName());
  }

  public void test_echo_boolean_true() {
    startTest(getMethodName());
  }

  public void test_echo_boolean_false() {
    startTest(getMethodName());
  }

  public void test_echo_json() {
    startTest(getMethodName());
  }



}
