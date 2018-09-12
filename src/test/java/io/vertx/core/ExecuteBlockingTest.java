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

package io.vertx.core;

import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ExecuteBlockingTest extends VertxTestBase {

  @Test
  public void testExecuteBlockingSuccess() {

    vertx.executeBlocking(future -> {
      try {
        Thread.sleep(1000);
      } catch (Exception ignore) {
      }
      future.complete("done!");
    }, onSuccess(res -> {
      assertEquals("done!", res);
      testComplete();
    }));
    await();
  }

  @Test
  public void testExecuteBlockingFailed() {

    vertx.executeBlocking(future -> {
      try {
        Thread.sleep(1000);
      } catch (Exception ignore) {
      }
      future.fail("failed!");
    }, onFailure(t -> {
      assertEquals("failed!", t.getMessage());
      testComplete();
    }));
    await();
  }

  @Test
  public void testExecuteBlockingThrowsRTE() {

    vertx.executeBlocking(future -> {
      throw new RuntimeException("rte");
    }, onFailure(t -> {
      assertEquals("rte", t.getMessage());
      testComplete();
    }));
    await();
  }

  @Test
  public void testExecuteBlockingContext() {

    vertx.runOnContext(v -> {
      Context ctx = vertx.getOrCreateContext();
      assertTrue(ctx.isEventLoopContext());
      vertx.executeBlocking(future -> {
        assertSame(ctx, vertx.getOrCreateContext());
        assertTrue(Thread.currentThread().getName().startsWith("vert.x-worker-thread"));
        assertTrue(Context.isOnWorkerThread());
        assertFalse(Context.isOnEventLoopThread());
        try {
          Thread.sleep(1000);
        } catch (Exception ignore) {
        }
        vertx.runOnContext(v2 -> {
          assertSame(ctx, vertx.getOrCreateContext());
          assertTrue(Thread.currentThread().getName().startsWith("vert.x-eventloop-thread"));
          assertFalse(Context.isOnWorkerThread());
          assertTrue(Context.isOnEventLoopThread());
          future.complete("done!");
        });
      }, onSuccess(res -> {
        assertSame(ctx, vertx.getOrCreateContext());
        assertTrue(Thread.currentThread().getName().startsWith("vert.x-eventloop-thread"));
        assertFalse(Context.isOnWorkerThread());
        assertTrue(Context.isOnEventLoopThread());
        assertEquals("done!", res);
        testComplete();
      }));
    });

    await();
  }

  @Test
  public void testExecuteBlockingTTCL() throws Exception {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    assertNotNull(cl);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<ClassLoader> blockingTCCL = new AtomicReference<>();
    vertx.<String>executeBlocking(future -> {
      future.complete("whatever");
      blockingTCCL.set(Thread.currentThread().getContextClassLoader());
    }, ar -> {
      assertTrue(ar.succeeded());
      assertEquals("whatever", ar.result());
      latch.countDown();
    });
    assertSame(cl, Thread.currentThread().getContextClassLoader());
    awaitLatch(latch);
    assertSame(cl, blockingTCCL.get());
  }

  @Test
  public void testExecuteBlockingParallel() throws Exception {

    long start = System.currentTimeMillis();
    int numExecBlocking = 10;
    long pause = 1000;
    CountDownLatch latch = new CountDownLatch(numExecBlocking);

    vertx.runOnContext(v -> {
      Context ctx = vertx.getOrCreateContext();
      assertTrue(ctx.isEventLoopContext());

      for (int i = 0; i < numExecBlocking; i++) {
        vertx.executeBlocking(future -> {
          assertSame(ctx, vertx.getOrCreateContext());
          assertTrue(Thread.currentThread().getName().startsWith("vert.x-worker-thread"));
          assertTrue(Context.isOnWorkerThread());
          assertFalse(Context.isOnEventLoopThread());
          try {
            Thread.sleep(pause);
          } catch (Exception ignore) {
          }
          future.complete("done!");
        }, false, onSuccess(res -> {
          assertSame(ctx, vertx.getOrCreateContext());
          assertTrue(Thread.currentThread().getName().startsWith("vert.x-eventloop-thread"));
          assertFalse(Context.isOnWorkerThread());
          assertTrue(Context.isOnEventLoopThread());
          assertEquals("done!", res);
          latch.countDown();

        }));
      }
    });

    awaitLatch(latch);

    long now = System.currentTimeMillis();
    long leeway = 1000;
    assertTrue(now - start < pause + leeway);
  }
}
