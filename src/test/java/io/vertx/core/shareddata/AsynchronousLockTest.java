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

package io.vertx.core.shareddata;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
  public void testAcquireDifferentLocksOnSameEventLoop() {
    Vertx vertx = getVertx();
    Context context = vertx.getOrCreateContext();
    SharedData sharedData = vertx.sharedData();
    AtomicInteger stage = new AtomicInteger();
    context.runOnContext(v -> {
      sharedData.getLock("foo", onSuccess(foo -> {
        assertTrue(stage.compareAndSet(0, 1));
        // Create another lock request
        sharedData.getLock("foo", onSuccess(foo1 -> {
          assertEquals(2, stage.get());
          foo1.release();
          testComplete();
        }));
        // Should not be blocked by second request for lock "foo"
        sharedData.getLock("bar", onSuccess(bar -> {
          assertTrue(stage.compareAndSet(1, 2));
          foo.release();
          bar.release();
        }));
      }));
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

  @Test
  public void testReleaseTwice() {
    waitFor(3);
    AtomicInteger count = new AtomicInteger(); // success lock count
    getVertx().sharedData().getLock("foo", onSuccess(lock1 -> {
      count.incrementAndGet();
      complete();
      for (int i = 0; i < 2; i++) {
        getVertx().sharedData().getLockWithTimeout("foo", 1000, ar -> {
          if (ar.succeeded()) {
            count.incrementAndGet();
            vertx.setTimer(1000, l -> {
              ar.result().release();
              complete();
            });
          } else {
            complete();
          }
        });
      }
      lock1.release();
      lock1.release();
    }));
    await();
    assertEquals(2, count.get());
  }
}
