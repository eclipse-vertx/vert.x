package org.vertx.java.tests.core.eventbus;

import org.junit.Test;
import org.vertx.java.core.app.VerticleType;
import org.vertx.java.core.logging.Logger;
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

  private static final Logger log = Logger.getLogger(JavaEventBusTest.class);

  private int numPeers = 4;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    SharedData.instance.getSet("addresses").clear();
    for (int i = 0; i < numPeers; i++) {
      startApp(VerticleType.JAVA, getLocalPeerClassName());
    }
    startApp(VerticleType.JAVA, getLocalClientClassName());
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
  public void testPointToPoint() {
    runPeerTest(getMethodName());
  }

//  public void testFoo() throws Exception {
//    super.runTestInLoop("testPointToPoint", 100000);
//  }

  @Test
  public void testReply() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testLocal() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoString() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoNullString() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoBooleanTrue() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoBooleanFalse() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoNullBoolean() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoBuffer() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoNullBuffer() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoByteArray() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoNullByte() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoCharacter() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoNullCharacter() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoDouble() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoNullDouble() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoFloat() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoNullFloat() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoInt() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoNullInt() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoJson() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoNullJson() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoLong() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoNullLong() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoShort() {
    startTest(getMethodName());
  }

  @Test
  public void testEchoNullShort() {
    startTest(getMethodName());
  }

  @Test
  public void testRegisterNoAddress() {
    startTest(getMethodName());
  }

}
