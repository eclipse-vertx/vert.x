package org.vertx.java.tests.core.eventbus;

import org.junit.Test;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.newtests.TestBase;
import vertx.tests.core.eventbus.LocalEchoClient;
import vertx.tests.core.eventbus.LocalEchoPeer;

/**
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaEchoTest extends TestBase {

  private static final Logger log = Logger.getLogger(JavaEchoTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(getPeerClassName());
    startApp(getClientClassName());
  }

  protected String getPeerClassName() {
    return LocalEchoPeer.class.getName();
  }

  protected String getClientClassName() {
    return LocalEchoClient.class.getName();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  private void runPeerTest(String testName) {
    startTest(testName + "Initialise");
    startTest(testName);
  }

  @Test
  public void testEchoString() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullString() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoBooleanTrue() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoBooleanFalse() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullBoolean() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoBuffer() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullBuffer() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoByteArray() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullByteArray() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoByte() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullByte() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoCharacter() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullCharacter() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoDouble() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullDouble() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoFloat() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullFloat() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoInt() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullInt() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoJson() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullJson() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoLong() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullLong() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoShort() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testEchoNullShort() {
    runPeerTest(getMethodName());
  }
}
