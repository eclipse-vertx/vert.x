package org.vertx.java.tests.websockets;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestBase;
import vertx.tests.websockets.InstanceCheckServer;
import vertx.tests.websockets.WebsocketsTestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaWebsocketTest extends TestBase {

  private static final Logger log = Logger.getLogger(JavaWebsocketTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JAVA, WebsocketsTestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testWSBinaryHybi00() throws Exception {
    startTest(getMethodName());
  }

  public void testWSStringHybi00() throws Exception {
    startTest(getMethodName());
  }

  public void testWSBinaryHybi08() throws Exception {
    startTest(getMethodName());
  }

  public void testWSStringHybi08() throws Exception {
    startTest(getMethodName());
  }

  public void testWSBinaryHybi17() throws Exception {
    startTest(getMethodName());
  }

  public void testWSStringHybi17() throws Exception {
    startTest(getMethodName());
  }

  public void testWriteFromConnectHybi00() throws Exception {
    startTest(getMethodName());
  }

  public void testWriteFromConnectHybi08() throws Exception {
    startTest(getMethodName());
  }

  public void testWriteFromConnectHybi17() throws Exception {
    startTest(getMethodName());
  }

   @Test
  public void testSharedServersMultipleInstances1() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() * 2;
    sharedServers(getMethodName(), true, numInstances, 0, 0);
  }

  @Test
  public void testSharedServersMultipleInstances2() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() - 1;
    sharedServers(getMethodName(), true, numInstances, 0, 0);
  }

  @Test
  public void testSharedServersMultipleInstances3() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() + 1;
    sharedServers(getMethodName(), true, numInstances, 0, 0);
  }

  @Test
  public void testSharedServersMultipleInstances1StartAllStopAll() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() * 2;
    sharedServers(getMethodName(), true, numInstances, numInstances, numInstances);
  }

  @Test
  public void testSharedServersMultipleInstances2StartAllStopAll() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() - 1;
    sharedServers(getMethodName(), true, numInstances, numInstances, numInstances);
  }

  @Test
  public void testSharedServersMultipleInstances3StartAllStopAll() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() + 1;
    sharedServers(getMethodName(), true, numInstances, numInstances,
        numInstances);
  }

  @Test
  public void testSharedServersMultipleInstances1StartAllStopSome() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() * 2;
    sharedServers(getMethodName(), true, numInstances, numInstances, numInstances / 2);
  }

  @Test
  public void testSharedServersMultipleInstances2StartAllStopSome() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() - 1;
    sharedServers(getMethodName(), true, numInstances, numInstances, numInstances / 2);
  }

  @Test
  public void testSharedServersMultipleInstances3StartAllStopSome() throws Exception {
    int numInstances = Runtime.getRuntime().availableProcessors() + 1;
    sharedServers(getMethodName(), true, numInstances, numInstances,
        numInstances / 2);
  }

  void sharedServers(String testName, boolean multipleInstances, int numInstances, int initialServers, int initialToStop) throws Exception {

    //We initially start then stop them to make sure the shared server cleanup code works ok

    int numConnections = 100;

    if (initialServers > 0) {

      // First start some servers
      String[] appNames = new String[initialServers];
      for (int i = 0; i < initialServers; i++) {
        appNames[i] = startApp(AppType.JAVA, InstanceCheckServer.class.getName(), 1);
      }

      SharedData.getCounter("connections").set(0);
      SharedData.getCounter("servers").set(0);
      SharedData.getSet("instances").clear();
      SharedData.getMap("params").put("numConnections", numConnections);

      startTest(testName);

      assertEquals(numConnections, SharedData.getCounter("connections").get());
      // And make sure connection requests are distributed amongst them
      assertEquals(initialServers, SharedData.getSet("instances").size());

      // Then stop some

      for (int i = 0; i < initialToStop; i++) {
        stopApp(appNames[i]);
      }
    }

    SharedData.getCounter("connections").set(0);
    SharedData.getCounter("servers").set(0);
    SharedData.getSet("instances").clear();
    SharedData.getMap("params").put("numConnections", numConnections);

    //Now start some more

    if (multipleInstances) {
      startApp(AppType.JAVA, InstanceCheckServer.class.getName(), numInstances);
    } else {
      for (int i = 0; i < numInstances; i++) {
        startApp(AppType.JAVA, InstanceCheckServer.class.getName(), 1);
      }
    }

    startTest(testName);

    assertEquals(numConnections, SharedData.getCounter("connections").get());
    // And make sure connection requests are distributed amongst them
    assertEquals(numInstances + initialServers - initialToStop, SharedData.getSet("instances").size());
  }

//  public void testFoo() throws Exception {
//    super.runTestInLoop("testSharedServersMultipleInstances3StartAllStopAll", 10);
//  }
}
