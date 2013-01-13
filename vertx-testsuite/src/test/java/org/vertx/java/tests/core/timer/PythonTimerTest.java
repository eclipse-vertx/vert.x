package org.vertx.java.tests.core.timer;

import org.vertx.java.testframework.TestBase;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class PythonTimerTest  extends TestBase {

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      startApp("core/timer/test_client.py");
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();
    }

    public void test_one_off() {
      startTest(getMethodName());
    }

    public void test_periodic() {
      startTest(getMethodName());
    }
}
