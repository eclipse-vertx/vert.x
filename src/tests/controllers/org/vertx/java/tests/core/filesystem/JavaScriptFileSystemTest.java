package org.vertx.java.tests.core.filesystem;

import org.junit.Test;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptFileSystemTest extends TestBase {

  private static final Logger log = Logger.getLogger(JavaScriptFileSystemTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/filesystem/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testCopy() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testMove() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testReadDir() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testPumpFile() throws Exception {
    startTest(getMethodName());
  }

}
