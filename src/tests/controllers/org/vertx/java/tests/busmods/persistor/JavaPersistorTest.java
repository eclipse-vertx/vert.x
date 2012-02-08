package org.vertx.java.tests.busmods.persistor;

import org.junit.Test;
import org.vertx.java.newtests.TestBase;
import vertx.tests.busmods.persistor.TestClient;

/**
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaPersistorTest extends TestBase {

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
  public void testPersistor() throws Exception {
    startTest(getMethodName());
  }


}
