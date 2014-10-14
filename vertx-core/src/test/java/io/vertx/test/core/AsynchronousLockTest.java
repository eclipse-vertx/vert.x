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

package io.vertx.test.core;

import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Lock;
import org.junit.Test;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsynchronousLockTest extends VertxTestBase {

  protected Vertx getVertx() {
    return vertx;
  }

  @Test
  public void testIllegalArguments() throws Exception {
    assertNullPointerException(() -> getVertx().sharedData().getLock(null, ar -> {}));
    assertNullPointerException(() -> getVertx().sharedData().getLock("foo", null));
    assertNullPointerException(() -> getVertx().sharedData().getLockWithTimeout(null, 1, ar -> {}));
    assertNullPointerException(() -> getVertx().sharedData().getLockWithTimeout("foo", 1, null));
    assertIllegalArgumentException(() -> getVertx().sharedData().getLockWithTimeout("foo", -1, ar -> {}));
  }

  @Test
  public void testAcquire() {
    getVertx().sharedData().getLock("foo", ar -> {
      assertTrue(ar.succeeded());
      long start = System.currentTimeMillis();
      Lock lock = ar.result();
      vertx.setTimer(1000, tid -> {
        lock.release();
      });
      getVertx().sharedData().getLock("foo", ar2 -> {
        assertTrue(ar2.succeeded());
        // Should be delayed
        assertTrue(System.currentTimeMillis() - start >= 1000);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAcquireDifferentLocks() {
    getVertx().sharedData().getLock("foo", ar -> {
      assertTrue(ar.succeeded());
      long start = System.currentTimeMillis();
      Lock lock = ar.result();
      getVertx().sharedData().getLock("bar", ar2 -> {
        assertTrue(ar2.succeeded());
        assertTrue(System.currentTimeMillis() - start < 2000);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAcquireTimeout() {
    getVertx().sharedData().getLock("foo", ar -> {
      assertTrue(ar.succeeded());
      long start = System.currentTimeMillis();
      getVertx().sharedData().getLockWithTimeout("foo", 1000, ar2 -> {
        assertFalse(ar2.succeeded());
        // Should be delayed
        assertTrue(System.currentTimeMillis() - start >= 1000);
        testComplete();
      });
    });
    await();
  }

}
