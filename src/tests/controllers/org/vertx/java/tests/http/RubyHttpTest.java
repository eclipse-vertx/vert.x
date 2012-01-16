package org.vertx.java.tests.http;

import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyHttpTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.RUBY, "http/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_get() {
    startTest(getMethodName());
  }

  public void test_get_ssl() {
    startTest(getMethodName());
  }

  public void test_put() {
    startTest(getMethodName());
  }

  public void test_put_ssl() {
    startTest(getMethodName());
  }

  public void test_post() {
    startTest(getMethodName());
  }

  public void test_post_ssl() {
    startTest(getMethodName());
  }

  public void test_head() {
    startTest(getMethodName());
  }

  public void test_head_ssl() {
    startTest(getMethodName());
  }

  public void test_options() {
    startTest(getMethodName());
  }

  public void test_options_ssl() {
    startTest(getMethodName());
  }

  public void test_delete() {
    startTest(getMethodName());
  }

  public void test_delete_ssl() {
    startTest(getMethodName());
  }

  public void test_trace() {
    startTest(getMethodName());
  }

  public void test_trace_ssl() {
    startTest(getMethodName());
  }

  public void test_connect() {
    startTest(getMethodName());
  }

  public void test_connect_ssl() {
    startTest(getMethodName());
  }

  public void test_patch() {
    startTest(getMethodName());
  }

  public void test_patch_ssl() {
    startTest(getMethodName());
  }
}
