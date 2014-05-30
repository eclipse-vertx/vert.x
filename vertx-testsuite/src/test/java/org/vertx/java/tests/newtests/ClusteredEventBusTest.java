/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.tests.newtests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.fakecluster.FakeClusterManagerFactory;
import org.vertx.java.spi.cluster.impl.hazelcast.HazelcastClusterManagerFactory;
import static org.vertx.java.tests.newtests.TestUtils.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredEventBusTest extends AsyncTestBase {

  private Vertx[] vertices;
  private static final String ADDRESS1 = "some-address1";

  @Before
  public void before() throws Exception {
    //System.setProperty("vertx.clusterManagerFactory", FakeClusterManagerFactory.class.getCanonicalName());
    System.setProperty("vertx.clusterManagerFactory", HazelcastClusterManagerFactory.class.getCanonicalName());
  }

  @After
  public void after() throws Exception {
    if (vertices != null) {
      for (Vertx vertx: vertices) {
        vertx.stop();
      }
    }
  }

  @Test
  public void testSendString() throws Exception {
    testSend(randomUnicodeString(100));
  }

  @Test
  public void testSendInt() throws Exception {
    testSend(123);
  }

  private <T> void testSend(T val) throws Exception {
    testSend(val, null);
  }

  /*
  TODO - all the send tests
  publish tests for all types
  start/stop node tests

   */

  private <T> void testSend(T val, Consumer<T> consumer) throws Exception {
    startNodes(2);
    vertices[1].eventBus().registerHandler(ADDRESS1, msg -> {
      if (consumer == null) {
        assertEquals(val, msg.body());
      } else {
        consumer.accept(val);
      }
      testComplete();
    }, ar -> {
      assertTrue(ar.succeeded());
      vertices[0].eventBus().send(ADDRESS1, val);
    });
    await();
  }

  @Test
  public void testLocalHandlerNotReceive() throws Exception {
    startNodes(2);
    vertices[1].eventBus().registerLocalHandler(ADDRESS1, msg -> {
      fail("Should not receive message");
    });
    vertices[0].eventBus().send(ADDRESS1, "foo");
    vertices[0].setTimer(1000, id -> testComplete());
    await();
  }

  void startNodes(int numNodes) throws Exception {
    CountDownLatch latch = new CountDownLatch(numNodes);
    vertices = new Vertx[numNodes];
    for (int i = 0; i < numNodes; i++) {
      int index = i;
      VertxFactory.newVertx(0, "localhost", ar -> {
        assertTrue("Failed to start node", ar.succeeded());
        vertices[index] = ar.result();
        latch.countDown();
      });
    }
    assertTrue(latch.await(30, TimeUnit.SECONDS));
  }

  /*
  Start two nodes and
  test echo and reply between them for all types
  and test round robin

  Start three nodes and
  test publish


   */
}
