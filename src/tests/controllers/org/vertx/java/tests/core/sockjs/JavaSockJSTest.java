package org.vertx.java.tests.core.sockjs;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;
import vertx.tests.core.sockjs.TestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaSockJSTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JAVA, TestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testWebSockets() {
    startTest(getMethodName());
  }

  @Test
  public void testXHRPolling() {
    startTest(getMethodName());
  }

}
