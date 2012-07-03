package org.vertx.java.tests.core.scriptloading;

import org.junit.Test;
import org.vertx.java.framework.TestBase;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class PythonScriptLoadingTest extends TestBase {

    @Override
    protected void setUp() throws Exception {
      super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();
    }

    @Test
    public void test_scriptloading() throws Exception {
      startApp("core/scriptloading/test_client.py");
      startTest(getMethodName());
    }


}
