package org.vertx.java.tests.core.buffer;

import org.vertx.java.framework.TestBase;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class PythonBufferTest extends TestBase {
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        startApp("core/buffer/test_client.py");
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();
    }

    public void test_append_buff() {
      startTest(getMethodName());
    }

    public void test_append_fixnum_1() {
      startTest(getMethodName());
    }

    public void test_append_fixnum_2() {
      startTest(getMethodName());
    }

    public void test_append_fixnum_4() {
      startTest(getMethodName());
    }

    public void test_append_fixnum_8() {
      startTest(getMethodName());
    }

    public void test_append_float_4() {
      startTest(getMethodName());
    }

    public void test_append_float_8() {
      startTest(getMethodName());
    }

    public void test_append_string_1() {
      startTest(getMethodName());
    }

    public void test_append_string_2() {
      startTest(getMethodName());
    }

    public void test_set_fixnum_1() {
      startTest(getMethodName());
    }

    public void test_set_fixnum_2() {
      startTest(getMethodName());
    }

    public void test_set_fixnum_4() {
      startTest(getMethodName());
    }

    public void test_set_fixnum_8() {
      startTest(getMethodName());
    }

    public void test_set_float_4() {
      startTest(getMethodName());
    }

    public void test_set_float_8() {
      startTest(getMethodName());
    }

    public void test_length() {
      startTest(getMethodName());
    }

    public void test_copy() {
      startTest(getMethodName());
    }

    public void test_create() {
      startTest(getMethodName());
    }
        
}
