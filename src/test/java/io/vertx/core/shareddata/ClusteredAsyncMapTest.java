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
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredAsyncMapTest extends AsyncMapTest {

  int pos;

  @Override
  protected Vertx getVertx() {
    Vertx vertx = vertices[pos];
    if (++pos == getNumNodes()) {
      pos = 0;
    }
    return vertx;
  }

  @Test
  public void testGetLocalAsyncMap() {
    final Vertx node1 = getVertx();
    final Vertx node2 = getVertx();
    assertNotSame(node1, node2);

    CompositeFuture
      .all(node1.sharedData().getLocalAsyncMap("map"), node2.sharedData().getLocalAsyncMap("map"))
      .compose(compFutureMaps -> {
      AsyncMap<String, String> mapNode1 = compFutureMaps.result().resultAt(0);
      AsyncMap<String, String> mapNode2 = compFutureMaps.result().resultAt(1);
      return CompositeFuture
        .all(mapNode1.put("Hodor", "Hodor"), mapNode2.put("Hodor", "Hodor Hodor"))
        .compose(compFuturePutted -> CompositeFuture.all(mapNode1.get("Hodor"), mapNode2.get("Hodor")));
    }).onComplete(onSuccess(asyncCompFuture -> {
      String valueMapNode1 = asyncCompFuture.result().resultAt(0);
      String valueMapNode2 = asyncCompFuture.result().resultAt(1);
      assertEquals(valueMapNode1, "Hodor");
      assertEquals(valueMapNode2, "Hodor Hodor");
      testComplete();
    }));
    await();
  }

  public void setUp() throws Exception {
    super.setUp();
    startNodes(getNumNodes());
  }

  protected int getNumNodes() {
    return 2;
  }

  @Override
  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }
}
