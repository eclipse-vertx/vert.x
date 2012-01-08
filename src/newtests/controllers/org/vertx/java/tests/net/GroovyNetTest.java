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
public class GroovyNetTest extends TestBase {

  private static final Logger log = Logger.getLogger(GroovyNetTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.GROOVY, "testclient.groovy");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void test1() throws Exception {
    startApp(AppType.GROOVY, "echoServer.groovy");
    startTest();
  }


}

