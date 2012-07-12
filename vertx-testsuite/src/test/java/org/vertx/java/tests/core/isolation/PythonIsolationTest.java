package org.vertx.java.tests.core.isolation;

import org.junit.Test;
import org.vertx.java.framework.TestBase;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class PythonIsolationTest  extends TestBase {

    @Override
    protected void setUp() throws Exception {
      super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();
    }

    @Test
    public void test_isolation() throws Exception {
      int numInstances = 10;
      for (int i = 0; i < numInstances; i++) {
        startApp("core/isolation/test_client.py");
      }
      startApp("core/isolation/test_client.py", numInstances);
      startTest(getMethodName(), false);
      for (int i = 0; i < numInstances * 2; i++) {
        waitTestComplete();
      }
    }


  }
