package org.vertx.java.tests.core.stdio;

import org.junit.Test;
import org.vertx.java.newtests.TestBase;
import vertx.tests.core.stdio.TestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaStdioTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(TestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testIn() throws Exception {
    startTest(getMethodName());
  }


}
