/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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

  public void testValidSubProtocolHybi00() throws Exception {
    startTest(getMethodName());
  }

  public void testValidSubProtocolHybi08() throws Exception {
    startTest(getMethodName());
  }

  public void testValidSubProtocolHybi17() throws Exception {
    startTest(getMethodName());
  }

  public void testInvalidSubProtocolHybi00() throws Exception {
    startTest(getMethodName());
  }

  public void testInvalidSubProtocolHybi08() throws Exception {
    startTest(getMethodName());
  }

  public void testInvalidSubProtocolHybi17() throws Exception {
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

  @Test
  public void testHeaders() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testHandleWSManually() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testContinuationWriteFromConnectHybi08() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testContinuationWriteFromConnectHybi17() throws Exception {
    startTest(getMethodName());
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
