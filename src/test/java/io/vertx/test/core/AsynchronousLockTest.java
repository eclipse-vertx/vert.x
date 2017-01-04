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

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.test.core.TestUtils.*;

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
  public void testAcquireOnSameEventLoop() {
    Vertx vertx = getVertx();
    Context context = vertx.getOrCreateContext();
    SharedData sharedData = vertx.sharedData();
    AtomicReference<Long> start = new AtomicReference<>();
    context.runOnContext(v -> {
      sharedData.getLock("foo", ar -> {
        assertTrue(ar.succeeded());
        start.set(System.currentTimeMillis());
        Lock lock = ar.result();
        vertx.setTimer(1000, tid -> {
          lock.release();
        });
        context.runOnContext(v2 -> {
          sharedData.getLock("foo", ar2 -> {
            assertTrue(ar2.succeeded());
            // Should be delayed
            assertTrue(System.currentTimeMillis() - start.get() >= 1000);
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testAcquireOnExecuteBlocking() {
    Vertx vertx = getVertx();
    SharedData sharedData = vertx.sharedData();
    AtomicReference<Long> start = new AtomicReference<>();
    vertx.<Lock>executeBlocking(future -> {
      CountDownLatch acquireLatch = new CountDownLatch(1);
      AtomicReference<AsyncResult<Lock>> lockReference = new AtomicReference<>();
      sharedData.getLock("foo", ar -> {
        lockReference.set(ar);
        acquireLatch.countDown();
      });
      try {
        awaitLatch(acquireLatch);
        AsyncResult<Lock> ar = lockReference.get();
        if (ar.succeeded()) {
          future.complete(ar.result());
        } else {
          future.fail(ar.cause());
        }
      } catch (InterruptedException e) {
        future.fail(e);
      }
    }, ar -> {
      if (ar.succeeded()) {
        start.set(System.currentTimeMillis());
        vertx.setTimer(1000, tid -> {
          ar.result().release();
        });
        vertx.executeBlocking(future -> {
          CountDownLatch acquireLatch = new CountDownLatch(1);
          AtomicReference<AsyncResult<Lock>> lockReference = new AtomicReference<>();
          sharedData.getLock("foo", ar2 -> {
            lockReference.set(ar2);
            acquireLatch.countDown();
          });
          try {
            awaitLatch(acquireLatch);
            AsyncResult<Lock> ar3 = lockReference.get();
            if (ar3.succeeded()) {
              future.complete(ar3.result());
            } else {
              future.fail(ar3.cause());
            }
          } catch (InterruptedException e) {
            future.fail(e);
          }
        }, ar4 -> {
          if (ar4.succeeded()) {
            // Should be delayed
            assertTrue(System.currentTimeMillis() - start.get() >= 1000);
            testComplete();
          } else {
            fail(ar4.cause());
          }
        });
      } else {
        fail(ar.cause());
      }
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
