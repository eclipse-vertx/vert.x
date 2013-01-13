package org.vertx.java.tests.core.deploy;

import org.junit.Test;
import org.vertx.java.testframework.TestBase;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */

public class PythonDeployTest extends TestBase {

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      startApp("core/deploy/test_client.py");
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();
    }

    @Test
    public void test_deploy() throws Exception {
      startTest(getMethodName());
    }

    @Test
    public void test_undeploy() throws Exception {
      startTest(getMethodName());
    }

  }
