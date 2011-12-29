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

  private String serverApp;
  private String clientApp;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    serverApp = startApp(AppType.JAVA, "vertx.tests.java.net.ServerApp");
    clientApp = startApp(AppType.JAVA, "vertx.tests.java.net.ClientApp");
  }

  @Override
  protected void tearDown() throws Exception {

    stopApp(clientApp);
    stopApp(serverApp);

    Thread.sleep(1000);

    super.tearDown();
  }

  @Test
  public void testEcho() {
    startTest("testEcho");
    waitTestComplete();
  }

  @Test
  public void testConnect() {
    startTest("testConnect");
    waitTestComplete();
  }

  @Test
  public void testWriteWithCompletion() {
    startTest("testWriteWithCompletion");
    waitTestComplete();
  }

}

