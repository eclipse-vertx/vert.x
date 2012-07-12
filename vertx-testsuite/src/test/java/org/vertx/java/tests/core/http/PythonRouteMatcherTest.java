package org.vertx.java.tests.core.http;

import org.vertx.java.framework.TestBase;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class PythonRouteMatcherTest extends TestBase {

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      startApp("core/routematcher/test_client.py");
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();
    }

    public void test_get_with_pattern() {
      startTest(getMethodName());
    }

    public void test_get_with_regex() {
      startTest(getMethodName());
    }
    
    public void test_put_with_pattern() {
      startTest(getMethodName());
    }

    public void test_put_with_regex() {
      startTest(getMethodName());
    }

    public void test_post_with_pattern() {
      startTest(getMethodName());
    }

    public void test_post_with_regex() {
      startTest(getMethodName());
    }

    public void test_delete_with_pattern() {
      startTest(getMethodName());
    }

    public void test_delete_with_regex() {
      startTest(getMethodName());
    }

    public void test_options_with_pattern() {
      startTest(getMethodName());
    }

    public void test_options_with_regex() {
      startTest(getMethodName());
    }

    public void test_head_with_pattern() {
      startTest(getMethodName());
    }

    public void test_head_with_regex() {
      startTest(getMethodName());
    }

    public void test_trace_with_pattern() {
      startTest(getMethodName());
    }

    public void test_trace_with_regex() {
      startTest(getMethodName());
    }

    public void test_patch_with_pattern() {
      startTest(getMethodName());
    }

    public void test_patch_with_regex() {
      startTest(getMethodName());
    }

    public void test_connect_with_pattern() {
      startTest(getMethodName());
    }

    public void test_connect_with_regex() {
      startTest(getMethodName());
    }

    public void test_all_with_pattern() {
      startTest(getMethodName());
    }

    public void test_all_with_regex() {
      startTest(getMethodName());
    }

    public void test_route_no_match() {
      startTest(getMethodName());
    }
}
