/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import org.junit.Test;

import io.vertx.core.*;
import io.vertx.test.fakecluster.FakeClusterManager;

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
  public void testFailCreateClusteredVertxSynchronously() {
    VertxOptions options = new VertxOptions();
    options.setClustered(true);
    try {
      Vertx.vertx(options);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testCreateClusteredVertxAsync() {
    VertxOptions options = new VertxOptions();
    options.setClustered(true);
    clusteredVertx(options, ar -> {
      assertTrue(ar.succeeded());
      assertNotNull(ar.result());
      assertTrue(ar.result().isClustered());
      Vertx v = ar.result();
      v.close(ar2 -> {
        assertTrue(ar2.succeeded());
        testComplete();
      });
    });
    await();
  }

  /*
  If the user doesn't explicitly set clustered to true, it should still create a clustered Vert.x
   */
  @Test
  public void testCreateClusteredVertxAsyncDontSetClustered() {
    VertxOptions options = new VertxOptions();
    clusteredVertx(options, ar -> {
      assertTrue(ar.succeeded());
      assertNotNull(ar.result());
      assertTrue(options.isClustered());
      assertTrue(ar.result().isClustered());
      Vertx v = ar.result();
      v.close(ar2 -> {
        assertTrue(ar2.succeeded());
        testComplete();
      });
    });
    await();
  }


  @Test
  public void testCreateClusteredVertxAsyncDetectJoinFailure() {
    VertxOptions options = new VertxOptions().setClusterManager(new FakeClusterManager(){
      @Override
      public void join(Handler<AsyncResult<Void>> resultHandler) {
        resultHandler.handle(Future.failedFuture(new Exception("joinfailure")));
      }
    });
    clusteredVertx(options, ar -> {
      assertTrue(ar.failed());
      assertEquals("joinfailure", ar.cause().getMessage());
      testComplete();
    });
    await();
  }
}
