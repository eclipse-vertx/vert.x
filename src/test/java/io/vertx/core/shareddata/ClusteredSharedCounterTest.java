/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.shareddata;

import org.junit.Test;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredSharedCounterTest extends SharedCounterTest {

  @Override
  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  protected final int numNodes = 2;

  public void setUp() throws Exception {
    super.setUp();
    startNodes(numNodes);
  }

  @Test
  public void testGetLocalCounter() {
    final Vertx node1 = getVertx();
    final Vertx node2 = getVertx();
    assertNotSame(node1, node2);

    CompositeFuture.all(Future.<Counter>future(fut -> {
      node1.sharedData().getLocalCounter("counter", fut);
    }), Future.<Counter>future(fut -> {
      node2.sharedData().getLocalCounter("counter", fut);
    })).compose(compFuture -> {
      Counter counterNode1 = compFuture.result().resultAt(0);
      Counter counterNode2 = compFuture.result().resultAt(1);

      return CompositeFuture.all(Future.<Long>future(fut -> {
        counterNode1.addAndGet(1, fut);
      }), Future.<Long>future(fut -> {
        counterNode2.addAndGet(2, fut);
      }));
    }).onComplete(asyncCompFuture -> {
      assertTrue(asyncCompFuture.succeeded());
      long valueCounterNode1 = asyncCompFuture.result().resultAt(0);
      long valueCounterNode2 = asyncCompFuture.result().resultAt(1);
      assertEquals(valueCounterNode1, 1);
      assertEquals(valueCounterNode2, 2);
      testComplete();
    });
    await();
  }

  int pos;
  @Override
  protected Vertx getVertx() {
    Vertx vertx = vertices[pos];
    if (++pos == numNodes) {
      pos = 0;
    }
    return vertx;
  }


}
