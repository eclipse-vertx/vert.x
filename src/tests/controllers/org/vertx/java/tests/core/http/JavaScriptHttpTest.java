package org.vertx.java.tests.core.http;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestBase;
import org.vertx.java.tests.core.TLSTestParams;
import vertx.tests.core.http.CountServer;
import vertx.tests.core.http.DrainingServer;
import vertx.tests.core.http.HttpTestClient;
import vertx.tests.core.http.InstanceCheckServer;
import vertx.tests.core.http.PausingServer;
import vertx.tests.core.http.TLSServer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptHttpTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JS, "core/http/http_server.js");
    startApp(AppType.JS, "core/http/test_client.js");
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
