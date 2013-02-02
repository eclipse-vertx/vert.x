/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.tests.core.net;

import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.streams.Pump;
import org.vertx.java.testframework.TestBase;
import vertx.tests.core.http.TLSTestParams;
import vertx.tests.core.net.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaNetTest extends TestBase {

  private static final Logger log = LoggerFactory.getLogger(JavaNetTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(TestClient.class.getName());
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
    startApp(EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testEchoStringDefaultEncoding() throws Exception {
    startApp(EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testEchoStringUTF8() throws Exception {
    startApp(EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testEchoStringUTF16() throws Exception {
    startApp(EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testConnectDefaultHost() throws Exception {
    startApp(EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testConnectLocalHost() throws Exception {
    startApp(EchoServer.class.getName());
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
    startApp(EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testClientCloseHandlersCloseFromClient() throws Exception {
    startApp(EchoServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testClientCloseHandlersCloseFromServer() throws Exception {
    startApp(CloseSocketServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testServerCloseHandlersCloseFromClient() throws Exception {
    startApp(CloseHandlerServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testServerCloseHandlersCloseFromServer() throws Exception {
    startApp(CloseHandlerServerCloseFromServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testClientDrainHandler() throws Exception {
    startApp(PausingServer.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testServerDrainHandler() throws Exception {
    startApp(DrainingServer.class.getName());
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
    startApp(EchoServerNoReady.class.getName(), false);
    waitTestComplete();
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
    vertx.sharedData().getMap("TLSTest").put("params", params.serialize());
    startApp(TLSServer.class.getName());
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
    numInstances = numInstances > 0 ? numInstances : 1;
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
    numInstances = numInstances > 0 ? numInstances : 1;
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
    numInstances = numInstances > 0 ? numInstances : 1;
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

    vertx.sharedData().getMap("params").put("listenport", 8181);
    startApp(EchoServer.class.getName(), true);
    vertx.sharedData().getMap("params").remove("listenport");

    //We initially start then stop them to make sure the shared server cleanup code works ok

    int numConnections = 100;

    if (initialServers > 0) {

      vertx.sharedData().getSet("connections").clear();
      vertx.sharedData().getSet("servers").clear();
      vertx.sharedData().getSet("instances").clear();
      vertx.sharedData().getMap("params").put("numConnections", numConnections);

      // First start some servers
      String[] appNames = new String[initialServers];
      for (int i = 0; i < initialServers; i++) {
        appNames[i] = startApp(InstanceCheckServer.class.getName(), 1);
      }

      startTest(testName);

      assertEquals(numConnections, vertx.sharedData().getSet("connections").size());
      // And make sure connection requests are distributed amongst them
      assertEquals(initialServers, vertx.sharedData().getSet("instances").size());

      // Then stop some

      for (int i = 0; i < initialToStop; i++) {
        stopApp(appNames[i]);
      }
    }

    vertx.sharedData().getSet("connections").clear();
    vertx.sharedData().getSet("servers").clear();
    vertx.sharedData().getSet("instances").clear();
    vertx.sharedData().getMap("params").put("numConnections", numConnections);

    //Now start some more

    if (multipleInstances) {
      startApp(InstanceCheckServer.class.getName(), numInstances);
    } else {
      for (int i = 0; i < numInstances; i++) {
        startApp(InstanceCheckServer.class.getName(), 1);
      }
    }

    startTest(testName);

    assertEquals(numConnections, vertx.sharedData().getSet("connections").size());
    // And make sure connection requests are distributed amongst them
    assertEquals(numInstances + initialServers - initialToStop, vertx.sharedData().getSet("instances").size());
  }

  @Test
  public void testNoContext() throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);

    Vertx vertx = Vertx.newVertx();

    final NetServer server = vertx.createNetServer();
    server.connectHandler(new Handler<NetSocket>() {
      public void handle(NetSocket socket) {
        Pump p = Pump.createPump(socket, socket);
        p.start();
      }
    });
    server.listen(1234);

    final NetClient client = vertx.createNetClient();
    client.connect(1234, new Handler<NetSocket>() {
      public void handle(NetSocket socket) {
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            server.close(new SimpleHandler() {
              public void handle() {
                client.close();
                latch.countDown();
              }
            });

          }
        });
        socket.write("foo");
      }
    });

    assertTrue(latch.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void testFanout() throws Exception {
    startApp(FanoutServer.class.getName());
    startTest(getMethodName());
  }
}

