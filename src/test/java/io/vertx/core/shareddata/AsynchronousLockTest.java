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

import io.vertx.core.*;
import io.vertx.core.shareddata.impl.LockInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    assertNullPointerException(() -> getVertx().sharedData().getLock(null));
    assertNullPointerException(() -> getVertx().sharedData().getLockWithTimeout(null, 1));
    assertIllegalArgumentException(() -> getVertx().sharedData().getLockWithTimeout("foo", -1));
  }

  @Test
  public void testAcquire() {
    SharedData sharedData = getVertx().sharedData();
    sharedData.getLock("foo").onComplete(onSuccess(lock -> {
      long start = System.currentTimeMillis();
      vertx.setTimer(1000, tid -> {
        lock.release();
      });
      sharedData
        .getLock("foo")
        .onComplete(onSuccess(v -> {
          // Should be delayed
          assertTrue(System.currentTimeMillis() - start >= 1000);
          testComplete();
        }));
    }));
    await();
  }

  @Test
  public void testAcquireOnSameEventLoop() {
    Vertx vertx = getVertx();
    Context context = vertx.getOrCreateContext();
    SharedData sharedData = vertx.sharedData();
    AtomicReference<Long> start = new AtomicReference<>();
    context.runOnContext(v -> {
      sharedData.getLock("foo").onComplete(onSuccess(lock -> {
        start.set(System.currentTimeMillis());
        vertx.setTimer(1000, tid -> {
          lock.release();
        });
        context.runOnContext(v2 -> {
          sharedData
            .getLock("foo")
            .onComplete(onSuccess(ar2 -> {
              // Should be delayed
              assertTrue(System.currentTimeMillis() - start.get() >= 1000);
              testComplete();
            }));
        });
      }));
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
      sharedData
        .getLock("foo")
        .onComplete(onSuccess(foo -> {
          assertTrue(stage.compareAndSet(0, 1));
          // Create another lock request
          sharedData
            .getLock("foo")
            .onComplete(onSuccess(foo1 -> {
              assertEquals(2, stage.get());
              foo1.release();
              testComplete();
            }));
          // Should not be blocked by second request for lock "foo"
          sharedData
            .getLock("bar")
            .onComplete(onSuccess(bar -> {
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
      sharedData
        .getLock("foo")
        .onComplete(ar -> {
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
    }).compose(lock -> {
      start.set(System.currentTimeMillis());
      vertx.setTimer(1000, tid -> {
        lock.release();
      });
      return vertx.executeBlocking(future -> {
        CountDownLatch acquireLatch = new CountDownLatch(1);
        AtomicReference<AsyncResult<Lock>> lockReference = new AtomicReference<>();
        sharedData
          .getLock("foo")
          .onComplete(ar2 -> {
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
      });
    }).onComplete(onSuccess(v -> {
      // Should be delayed
      assertTrue(System.currentTimeMillis() - start.get() >= 1000);
      testComplete();
    }));
    await();
  }

  @Test
  public void testAcquireDifferentLocks() {
    SharedData sharedData = getVertx().sharedData();
    sharedData
      .getLock("foo")
      .onComplete(onSuccess(lock1 -> {
        long start = System.currentTimeMillis();
        sharedData
          .getLock("bar")
          .onComplete(onSuccess(lock2 -> {
            assertTrue(System.currentTimeMillis() - start < 2000);
            testComplete();
          }));
      }));
    await();
  }

  @Test
  public void testAcquireTimeout() {
    SharedData sharedData = getVertx().sharedData();
    sharedData
      .getLock("foo")
      .onComplete(onSuccess(ar -> {
        long start = System.currentTimeMillis();
        sharedData
          .getLockWithTimeout("foo", 1000)
          .onComplete(onFailure(ar2 -> {
            // Should be delayed
            assertTrue(System.currentTimeMillis() - start >= 1000);
            testComplete();
          }));
      }));
    await();
  }

  @Test
  public void testReleaseTwice() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger count = new AtomicInteger(); // success lock count
    getVertx()
      .sharedData()
      .getLock("foo")
      .onComplete(onSuccess(lock1 -> {
        count.incrementAndGet();
        for (int i = 0; i < 2; i++) {
          getVertx()
            .sharedData()
            .getLockWithTimeout("foo", 10)
            .onComplete(ar -> {
              if (ar.succeeded()) {
                count.incrementAndGet();
              }
              latch.countDown();
            });
        }
        lock1.release();
        lock1.release();
      }));
    awaitLatch(latch);
    assertEquals(2, count.get());
  }

  @Test
  public void testNoWorkerStarvation() {
    waitFor(5);
    getVertx().deployVerticle(() -> new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        vertx.sharedData()
          .getLock("foo")
          .onComplete(onSuccess(lock -> {
            vertx.setTimer(10, l -> {
              lock.release();
              complete();
            });
          }));
      }
    }, new DeploymentOptions().setInstances(5).setWorkerPoolName("bar").setWorkerPoolSize(1));
    await();
  }

  @Test
  public void evictTimedOutWaiters() {
    int numWaiters = 10;
    SharedData sharedData = vertx.sharedData();
    sharedData
      .getLocalLock("foo")
      .onComplete(onSuccess(lock -> {
        List<Future> locks = new ArrayList<>();
        for (int i = 0; i < numWaiters; i++) {
          locks.add(sharedData.getLocalLockWithTimeout("foo", 200));
        }
        LockInternal lockInternal = (LockInternal) lock;
        assertEquals(numWaiters, lockInternal.waiters());
        CompositeFuture.join(locks).onComplete(cf -> {
          assertEquals(0, lockInternal.waiters());
          lock.release();
          testComplete();
        });
      }));
    await();
  }
}
