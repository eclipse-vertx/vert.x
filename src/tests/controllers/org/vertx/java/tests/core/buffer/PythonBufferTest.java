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

    public void test_something() {
      startTest(getMethodName());
    }

    public void test_another() {
      startTest(getMethodName());
    }
}
