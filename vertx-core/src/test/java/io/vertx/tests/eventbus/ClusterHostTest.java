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

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.Utils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Assume;
import org.junit.Test;

public class ClusterHostTest extends VertxTestBase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    Assume.assumeTrue(Utils.isLinux());
  }

  @Test
  public void testClusterHostHintFromClusterManager() {
    FakeClusterManager clusterManager = new FakeClusterManager() {
      @Override
      public String clusterHost() {
        return "127.0.0.3";
      }
    };
    Vertx clusteredVertx = clusteredVertx(new VertxOptions(), clusterManager).await();
    assertEquals("127.0.0.3", clusterManager.getNodeInfo().host());
  }

  @Test
  public void testClusterPublicHostHintFromClusterManager() {
    FakeClusterManager clusterManager = new FakeClusterManager() {
      @Override
      public String clusterHost() {
        return "127.0.0.2";
      }

      @Override
      public String clusterPublicHost() {
        return "127.0.0.3";
      }
    };
    Vertx clusteredVertx = clusteredVertx(new VertxOptions(), clusterManager).await();
    assertEquals("127.0.0.3", clusterManager.getNodeInfo().host());
  }

  @Test
  public void testUserSuppliedHostPrecedence() {
    FakeClusterManager clusterManager = new FakeClusterManager() {
      @Override
      public String clusterHost() {
        return "127.0.0.2";
      }

      @Override
      public String clusterPublicHost() {
        return "127.0.0.3";
      }
    };
    VertxOptions options = new VertxOptions();
    options.getEventBusOptions().setHost("127.0.0.4");
    Vertx clusteredVertx = clusteredVertx(options, clusterManager).await();
    assertEquals("127.0.0.4", clusterManager.getNodeInfo().host());
  }
}
