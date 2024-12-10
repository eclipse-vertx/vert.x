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

import io.vertx.core.Future;
import io.vertx.core.shareddata.AsyncMap;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredAsyncMapTest extends AsyncMapTest {

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
  public void testGetLocalAsyncMap() {
    final Vertx node1 = getVertx();
    final Vertx node2 = getVertx();
    assertNotSame(node1, node2);

    Future
      .all(node1.sharedData().getLocalAsyncMap("map"), node2.sharedData().getLocalAsyncMap("map"))
      .compose(compFutureMaps -> {
      AsyncMap<String, String> mapNode1 = compFutureMaps.result().resultAt(0);
      AsyncMap<String, String> mapNode2 = compFutureMaps.result().resultAt(1);
      return Future
        .all(mapNode1.put("Hodor", "Hodor"), mapNode2.put("Hodor", "Hodor Hodor"))
        .compose(compFuturePutted -> Future.all(mapNode1.get("Hodor"), mapNode2.get("Hodor")));
    }).onComplete(onSuccess(asyncCompFuture -> {
      String valueMapNode1 = asyncCompFuture.result().resultAt(0);
      String valueMapNode2 = asyncCompFuture.result().resultAt(1);
      assertEquals(valueMapNode1, "Hodor");
      assertEquals(valueMapNode2, "Hodor Hodor");
      testComplete();
    }));
    await();
  }

}
