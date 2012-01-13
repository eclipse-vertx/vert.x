package org.vertx.java.tests.eventbus;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;
import vertx.tests.eventbus.LocalClient;
import vertx.tests.eventbus.LocalPeer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaEventBusTest extends TestBase {

  private int numPeers = 4;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    for (int i = 0; i < numPeers; i++) {
      startApp(AppType.JAVA, getLocalPeerClassName());
    }
    startApp(AppType.JAVA, getLocalClientClassName());
  }

  protected String getLocalPeerClassName() {
    return LocalPeer.class.getName();
  }

  protected String getLocalClientClassName() {
    return LocalClient.class.getName();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  private void runPeerTest(String testName) {
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
  public void testNoBuffer() throws Exception {
    runPeerTest(getMethodName());
  }

  @Test
  public void testNullBuffer() throws Exception {
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

}
