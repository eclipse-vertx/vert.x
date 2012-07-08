package org.vertx.java.tests.core.shareddata;

import org.vertx.java.framework.TestBase;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class PythonSharedDataTest extends TestBase {

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      startApp("core/shareddata/test_client.py");
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();
    }

    public void test_hash() {
      startTest(getMethodName());
    }

    public void test_set() {
      startTest(getMethodName());
    }

}
