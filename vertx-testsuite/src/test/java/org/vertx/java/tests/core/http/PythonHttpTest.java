package org.vertx.java.tests.core.http;

import org.vertx.java.testframework.TestBase;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class PythonHttpTest  extends TestBase {

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      startApp("core/http/test_client.py");
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

    public void test_get_chunked() {
      startTest(getMethodName());
    }

    public void test_get_ssl_chunked() {
      startTest(getMethodName());
    }

    public void test_put_chunked() {
      startTest(getMethodName());
    }

    public void test_put_ssl_chunked() {
      startTest(getMethodName());
    }

    public void test_post_chunked() {
      startTest(getMethodName());
    }

    public void test_post_ssl_chunked() {
      startTest(getMethodName());
    }

    public void test_head_chunked() {
      startTest(getMethodName());
    }

    public void test_head_ssl_chunked() {
      startTest(getMethodName());
    }

    public void test_options_chunked() {
      startTest(getMethodName());
    }

    public void test_options_ssl_chunked() {
      startTest(getMethodName());
    }

    public void test_delete_chunked() {
      startTest(getMethodName());
    }

    public void test_delete_ssl_chunked() {
      startTest(getMethodName());
    }

    public void test_trace_chunked() {
      startTest(getMethodName());
    }

    public void test_trace_ssl_chunked() {
      startTest(getMethodName());
    }

    public void test_connect_chunked() {
      startTest(getMethodName());
    }

    public void test_connect_ssl_chunked() {
      startTest(getMethodName());
    }

    public void test_patch_chunked() {
      startTest(getMethodName());
    }

    public void test_patch_ssl_chunked() {
      startTest(getMethodName());
    }

}
