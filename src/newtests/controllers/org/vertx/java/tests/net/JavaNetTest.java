package org.vertx.java.tests.net;

import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Immutable;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.AppType;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestBase;
import vertx.tests.net.CloseHandlerServer;
import vertx.tests.net.CloseHandlerServerCloseFromServer;
import vertx.tests.net.ClosingServer;
import vertx.tests.net.DrainingServer;
import vertx.tests.net.EchoServer;
import vertx.tests.net.EchoServerNoReady;
import vertx.tests.net.InstanceCheckServer;
import vertx.tests.net.PausingServer;
import vertx.tests.net.TLSServer;
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
    startTest(getMethodName());
  }

  @Test
  public void testClientAttributes() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testServerDefaults() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testServerAttributes() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testEchoBytes() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testEchoStringDefaultEncoding() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testEchoStringUTF8() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testEchoStringUTF16() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testConnectDefaultHost() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testConnectLocalHost() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testConnectInvalidPort() {
    startTest(getMethodName());
  }

  @Test
  public void testConnectInvalidHost() {
    startTest(getMethodName());
  }

  @Test
  public void testWriteWithCompletion() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testClientCloseHandlersCloseFromClient() throws Exception {
    startApp(AppType.JAVA, EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testClientCloseHandlersCloseFromServer() throws Exception {
    startApp(AppType.JAVA, ClosingServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testServerCloseHandlersCloseFromClient() throws Exception {
    startApp(AppType.JAVA, CloseHandlerServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testServerCloseHandlersCloseFromServer() throws Exception {
    startApp(AppType.JAVA, CloseHandlerServerCloseFromServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testClientDrainHandler() throws Exception {
    startApp(AppType.JAVA, PausingServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testServerDrainHandler() throws Exception {
    startApp(AppType.JAVA, DrainingServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testReconnectAttemptsInfinite() throws Exception {
    // Start the client without the server
    startTest(getMethodName(), false);
    reconnectAttempts();
  }

  @Test
  public void testReconnectAttemptsMany() throws Exception {
    // Start the client without the server
    startTest(getMethodName(), false);
    reconnectAttempts();
  }

  @Test
  public void testReconnectAttemptsNotEnough() throws Exception {
    // Start the client without the server
    startTest(getMethodName());
  }

  void reconnectAttempts() throws Exception {
    // Wait a little while then start the server
    Thread.sleep(1000);
    startApp(AppType.JAVA, EchoServerNoReady.class.getName(), false);
    waitTestComplete();
  }

  public static class TLSTestParams implements Immutable {
    public final boolean clientCert;
    public final boolean clientTrust;
    public final boolean serverCert;
    public final boolean serverTrust;
    public final boolean requireClientAuth;
    public final boolean clientTrustAll;
    public final boolean shouldPass;

    public TLSTestParams(boolean clientCert, boolean clientTrust, boolean serverCert, boolean serverTrust,
                         boolean requireClientAuth, boolean clientTrustAll, boolean shouldPass) {
      this.clientCert = clientCert;
      this.clientTrust = clientTrust;
      this.serverCert = serverCert;
      this.serverTrust = serverTrust;
      this.requireClientAuth = requireClientAuth;
      this.clientTrustAll = clientTrustAll;
      this.shouldPass = shouldPass;
    }
  }

  @Test
  // Client trusts all server certs
  public void testTLSClientTrustAll() throws Exception {
    testTLS(getMethodName(), false, false, true, false, false, true, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert() throws Exception {
    testTLS(getMethodName(), false, true, true, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer() throws Exception {
    testTLS(getMethodName(), false, false, true, false, false, false, false);
  }

  @Test
  //Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired() throws Exception {
    testTLS(getMethodName(), true, true, true, true, false, false, true);
  }

  @Test
  //Client specifies cert and it's not required
  public void testTLSClientCertRequired() throws Exception {
    testTLS(getMethodName(), true, true, true, true, true, false, true);
  }

  @Test
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    testTLS(getMethodName(), false, true, true, true, true, false, false);
  }

  @Test
  //Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted() throws Exception {
    testTLS(getMethodName(), true, true, true, false, true, false, false);
  }

  void testTLS(String testName, boolean clientCert, boolean clientTrust,
               boolean serverCert, boolean serverTrust,
               boolean requireClientAuth, boolean clientTrustAll,
               boolean shouldPass) throws Exception {
    //Put the params in shared-data
    TLSTestParams params = new TLSTestParams(clientCert, clientTrust, serverCert, serverTrust,
        requireClientAuth, clientTrustAll, shouldPass);
    SharedData.getMap("TLSTest").put("params", params);
    startApp(AppType.JAVA, TLSServer.class.getName());
    startTest(testName);
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

    // Start an echo server on a different port to make sure shared servers work ok when there are other servers
    // on different ports

    SharedData.getMap("params").put("listenport", 8181);
    startApp(AppType.JAVA, EchoServer.class.getName(), true);
    SharedData.getMap("params").remove("listenport");

    //We initially start then stop them to make sure the shared server cleanup code works ok

    int numConnections = 100;

    if (initialServers > 0) {

      // First start some servers
      String[] appNames = new String[initialServers];
      for (int i = 0; i < initialServers; i++) {
        appNames[i] = startApp(AppType.JAVA, InstanceCheckServer.class.getName(), 1);
        waitAppReady();
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

    for (int i = 0; i < numInstances; i++) {
      waitAppReady();
    }

    startTest(testName);

    assertEquals(numConnections, SharedData.getCounter("connections").get());
    // And make sure connection requests are distributed amongst them
    assertEquals(numInstances + initialServers - initialToStop, SharedData.getSet("instances").size());
  }

  @Test
  public void testCreateServerNoContext() throws Exception {
    try {
      new NetServer();
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

  @Test
  public void testCreateClientNoContext() throws Exception {
    try {
      new NetClient();
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // Ok
    }
  }

}

