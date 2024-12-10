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

package io.vertx.tests.shareddata;

import io.vertx.core.shareddata.Counter;
import org.junit.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredSharedCounterTest extends SharedCounterTest {

  protected final int numNodes = 3;
  AtomicInteger pos = new AtomicInteger();

  public void setUp() throws Exception {
    super.setUp();
    startNodes(numNodes);
  }

  @Override
  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  @Override
  protected Vertx getVertx() {
    int i = pos.incrementAndGet();
    i = mod(i, numNodes);
    return vertices[i];
  }

  private int mod(int idx, int size) {
    int i = idx % size;
    return i < 0 ? i + size : i;
  }

  @Test
  public void testGetLocalCounter() {
    final Vertx node1 = getVertx();
    final Vertx node2 = getVertx();
    assertNotSame(node1, node2);

    Future.all(node1.sharedData().getLocalCounter("counter"), node2.sharedData().getLocalCounter("counter"))
      .compose(compFuture -> {
        Counter counterNode1 = compFuture.result().resultAt(0);
        Counter counterNode2 = compFuture.result().resultAt(1);
        return Future.all(counterNode1.addAndGet(1), counterNode2.addAndGet(2));
      }).onComplete(onSuccess(asyncCompFuture -> {
      long valueCounterNode1 = asyncCompFuture.result().resultAt(0);
      long valueCounterNode2 = asyncCompFuture.result().resultAt(1);
      assertEquals(valueCounterNode1, 1);
      assertEquals(valueCounterNode2, 2);
      testComplete();
    }));
    await();
  }

}
