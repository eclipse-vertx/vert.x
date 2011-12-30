package vertx.tests.java.net;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.newtests.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetTest extends TestBase {

  private static final Logger log = Logger.getLogger(NetTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JAVA, "vertx.tests.java.net.TestClient");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testEchoBytes() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.EchoServer");
    startTest("testEchoBytes");
  }

  @Test
  public void testEchoStringDefaultEncoding() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.EchoServer");
    startTest("testEchoStringDefaultEncoding");
  }

  @Test
  public void testEchoStringUTF8() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.EchoServer");
    startTest("testEchoStringUTF8");
  }

  @Test
  public void testEchoStringUTF16() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.EchoServer");
    startTest("testEchoStringUTF16");
  }

  @Test
  public void testConnectDefaultHost() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.EchoServer");
    startTest("testConnectDefaultHost");
  }

  @Test
  public void testConnectLocalHost() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.EchoServer");
    startTest("testConnectLocalHost");
  }

  @Test
  public void testConnectInvalidPort() {
    startTest("testConnectInvalidPort");
  }

  @Test
  public void testConnectInvalidHost() {
    startTest("testConnectInvalidPort");
  }

  @Test
  public void testWriteWithCompletion() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.EchoServer");
    startTest("testWriteWithCompletion");
  }

  @Test
  public void testClientCloseHandlersCloseFromClient() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.EchoServer");
    startTest("testClientCloseHandlersCloseFromClient");
  }

  @Test
  public void testClientCloseHandlersCloseFromServer() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.ClosingServer");
    startTest("testClientCloseHandlersCloseFromServer");
  }

  @Test
  public void testServerCloseHandlersCloseFromClient() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.CloseHandlerServer");
    startTest("testServerCloseHandlersCloseFromClient");
  }

  @Test
  public void testServerCloseHandlersCloseFromServer() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.CloseHandlerServerCloseFromServer");
    startTest("testServerCloseHandlersCloseFromServer");
  }

  @Test
  public void testClientDrainHandler() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.PausingServer");
    startTest("testClientDrainHandler");
  }

  @Test
  public void testServerDrainHandler() throws Exception {
    startApp(AppType.JAVA, "vertx.tests.java.net.DrainingServer");
    startTest("testServerDrainHandler");
  }

}

