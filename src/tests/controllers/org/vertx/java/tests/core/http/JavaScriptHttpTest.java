package org.vertx.java.tests.core.http;

import org.junit.Test;
import org.vertx.java.core.app.VerticleType;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptHttpTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(VerticleType.JS, "core/http/http_server.js");
    startApp(VerticleType.JS, "core/http/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testHTTP() throws Exception {
    startTest(getMethodName());
  }

}
