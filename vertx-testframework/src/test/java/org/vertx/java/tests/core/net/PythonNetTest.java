package org.vertx.java.tests.core.net;

import org.junit.Test;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.framework.TestBase;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class PythonNetTest extends TestBase {

    private static final Logger log = LoggerFactory.getLogger(PythonNetTest.class);

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      startApp("core/net/test_client.py");
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();
    }

    @Test
    public void test_echo() throws Exception {
      startTest(getMethodName());
    }

    @Test
    public void test_write_str() throws Exception {
      startTest(getMethodName());
    }
    
    @Test
    public void test_echo_ssl() throws Exception {
      startTest(getMethodName());
    }

    @Test
    public void test_methods() throws Exception {
      startTest(getMethodName());
    }
    
}
