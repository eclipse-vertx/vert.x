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

package io.vertx.tests.vertx;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CreateVertxTest extends VertxTestBase {

  @Test
  public void testCreateSimpleVertx() {
    Vertx vertx = vertx();
    assertNotNull(vertx);
  }

  @Test
  public void testCreateVertxWithOptions() {
    VertxOptions options = new VertxOptions();
    Vertx vertx = vertx(options);
    assertNotNull(vertx);
    assertFalse(vertx.isClustered());
  }

  @Test
  public void testCreateClusteredVertxAsync() {
    VertxOptions options = new VertxOptions();
    clusteredVertx(options)
      .compose(v -> {
        assertTrue(v.isClustered());
        return v.close();
      }).await();
  }

  @Test
  public void testCreateClusteredVertxAsyncDetectJoinFailure() {
    ClusterManager clusterManager = new FakeClusterManager(){
      @Override
      public void join(Promise<Void> promise) {
        promise.fail("joinfailure");
      }
    };
    try {
      clusteredVertx(new VertxOptions(), clusterManager).await();
    } catch (Throwable e) {
      assertEquals("joinfailure", e.getMessage());
      return;
    }
    fail();
  }
}
