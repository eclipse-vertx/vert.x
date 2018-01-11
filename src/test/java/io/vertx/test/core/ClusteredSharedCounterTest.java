/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

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
