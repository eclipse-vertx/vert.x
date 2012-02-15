package org.vertx.java.tests.core.eventbus;

import org.junit.Test;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestBase;
import vertx.tests.core.eventbus.LocalClient;
import vertx.tests.core.eventbus.LocalPeer;

/**
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaEventBusTest extends TestBase {

  private static final Logger log = LoggerFactory.getLogger(JavaEventBusTest.class);

  private int numPeers = 4;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    SharedData.instance.getSet("addresses").clear();
    for (int i = 0; i < numPeers; i++) {
      startApp(getPeerClassName());
    }
    startApp(getClientClassName());
  }

  protected String getPeerClassName() {
    return LocalPeer.class.getName();
  }

  protected String getClientClassName() {
    return LocalClient.class.getName();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  private void runPeerTest(String testName) {
    runPeerTest(testName, numPeers);
  }

  private void runPeerTest(String testName, int numPeers) {
    startTest(testName + "Initialise", false);
    for (int i = 0; i < numPeers; i++) {
      super.waitTestComplete();
    }
    startTest(testName, false);
    for (int i = 0; i < numPeers; i++) {
      super.waitTestComplete();
    }
  }

  @Test
  public void testPubSub() throws Exception {
    runPeerTest(getMethodName());
  }

  @Test
  public void testPubSubMultipleHandlers() throws Exception {
    runPeerTest(getMethodName());
  }

  @Test
  public void testPointToPoint() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testReply() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testLocal() {
    startTest(getMethodName());
  }

  @Test
  public void testRegisterNoAddress() {
    startTest(getMethodName());
  }

}
