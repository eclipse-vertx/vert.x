package org.vertx.java.tests.core.filesystem;

import org.junit.Test;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.testframework.TestBase;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class PythonFileSystemTest extends TestBase {

    private static final Logger log = LoggerFactory.getLogger(PythonFileSystemTest.class);

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      startApp("core/filesystem/test_client.py");
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();
    }

    @Test
    public void test_copy() throws Exception {
      startTest(getMethodName());
    }

    @Test
    public void test_stats() throws Exception {
      startTest(getMethodName());
    }

    @Test
    public void test_async_file() throws Exception {
      startTest(getMethodName());
    }

    @Test
    public void test_async_file_streams() throws Exception {
      startTest(getMethodName());
    }
    
}
