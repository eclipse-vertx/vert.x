package org.vertx.java.tests.http;

import org.junit.Test;

import org.vertx.java.core.app.AppType;

import org.vertx.java.newtests.TestBase;

import vertx.tests.http.TestClient;
import vertx.tests.http.EchoServer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaHttpTest extends TestBase {

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
  public void test1() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testWithParams() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testWithHeaders() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest(getMethodName());
  }


}
