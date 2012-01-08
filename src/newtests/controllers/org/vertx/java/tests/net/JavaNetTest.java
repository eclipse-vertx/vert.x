package org.vertx.java.tests.net;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.newtests.TestBase;
import vertx.tests.net.CloseHandlerServer;
import vertx.tests.net.CloseHandlerServerCloseFromServer;
import vertx.tests.net.ClosingServer;
import vertx.tests.net.DrainingServer;
import vertx.tests.net.EchoServer;
import vertx.tests.net.PausingServer;
import vertx.tests.net.TestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaNetTest extends TestBase {

  private static final Logger log = Logger.getLogger(JavaNetTest.class);

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
  public void testClientDefaults() throws Exception {
    startTest();
  }

  @Test
  public void testClientAttributes() throws Exception {
    startTest();
  }

  @Test
  public void testServerDefaults() throws Exception {
    startTest();
  }

  @Test
  public void testServerAttributes() throws Exception {
    startTest();
  }

  @Test
  public void testEchoBytes() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest();
  }

  @Test
  public void testEchoStringDefaultEncoding() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest();
  }

  @Test
  public void testEchoStringUTF8() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest();
  }

  @Test
  public void testEchoStringUTF16() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest();
  }

  @Test
  public void testConnectDefaultHost() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest();
  }

  @Test
  public void testConnectLocalHost() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest();
  }

  @Test
  public void testConnectInvalidPort() {
    startTest();
  }

  @Test
  public void testConnectInvalidHost() {
    startTest();
  }

  @Test
  public void testWriteWithCompletion() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest();
  }

  @Test
  public void testClientCloseHandlersCloseFromClient() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest();
  }

  @Test
  public void testClientCloseHandlersCloseFromServer() throws Exception {
    startApp(AppType.JAVA, ClosingServer.class.getName());
    startTest();
  }

  @Test
  public void testServerCloseHandlersCloseFromClient() throws Exception {
    startApp(AppType.JAVA, CloseHandlerServer.class.getName());
    startTest();
  }

  @Test
  public void testServerCloseHandlersCloseFromServer() throws Exception {
    startApp(AppType.JAVA, CloseHandlerServerCloseFromServer.class.getName());
    startTest();
  }

  @Test
  public void testClientDrainHandler() throws Exception {
    startApp(AppType.JAVA, PausingServer.class.getName());
    startTest();
  }

  @Test
  public void testServerDrainHandler() throws Exception {
    startApp(AppType.JAVA, DrainingServer.class.getName());
    startTest();
  }

}

