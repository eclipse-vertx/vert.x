/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.eventbus;

import io.vertx.core.Promise;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

/**
 * @author Thomas Segismont
 */
public class NodeInfoTest extends VertxTestBase {

  @Override
  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  @Test
  public void testFailedFutureForUnknownNode() {
    startNodes(2);
    ClusterManager clusterManager = ((VertxInternal) vertices[0]).clusterManager();
    // Create unknown node identifier
    String unknown = String.join("", clusterManager.getNodes());
    // Needed as callback might be done from non Vert.x thread
    disableThreadChecks();
    Promise<NodeInfo> promise = Promise.promise();
    clusterManager.getNodeInfo(unknown, promise);
    promise.future().onComplete(onFailure(t -> testComplete()));
    await();
  }
}
