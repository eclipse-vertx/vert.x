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

package org.vertx.java.tests.core.websockets;

import org.junit.Test;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.testframework.TestBase;
import vertx.tests.core.websockets.InstanceCheckServer;
import vertx.tests.core.websockets.WebsocketsTestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaWebsocketTest extends TestBase {

  private static final Logger log = LoggerFactory.getLogger(JavaWebsocketTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(WebsocketsTestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testRejectHybi00() throws Exception {
    startTest(getMethodName());
  }

  public void testRejectHybi08() throws Exception {
    startTest(getMethodName());
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

    //We initially start then stop them to make sure the shared server cleanup code works ok

    int numConnections = 100;

    if (initialServers > 0) {

      // First start some servers
      String[] appNames = new String[initialServers];
      for (int i = 0; i < initialServers; i++) {
        appNames[i] = startApp(InstanceCheckServer.class.getName(), 1);
      }

      vertx.sharedData().getSet("connections").clear();
      vertx.sharedData().getSet("servers").clear();
      vertx.sharedData().getSet("instances").clear();
      vertx.sharedData().getMap("params").put("numConnections", numConnections);

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

}
