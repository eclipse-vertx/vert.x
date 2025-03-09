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

package io.vertx.tests.worker;

import io.vertx.core.Context;
import io.vertx.core.VertxException;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ExecuteBlockingTest extends VertxTestBase {

  @Test
  public void testExecuteBlockingSuccess() {

    vertx.executeBlocking(() -> {
      try {
        Thread.sleep(1000);
      } catch (Exception ignore) {
      }
      return "done!";
    }).onComplete(onSuccess(res -> {
      assertEquals("done!", res);
      testComplete();
    }));
    await();
  }

  @Test
  public void testExecuteBlockingFailed() {

    vertx.executeBlocking(() -> {
      try {
        Thread.sleep(1000);
      } catch (Exception ignore) {
      }
      throw VertxException.noStackTrace("failed!");
    }).onComplete(onFailure(t -> {
      assertEquals("failed!", t.getMessage());
      testComplete();
    }));
    await();
  }

  @Test
  public void testExecuteBlockingThrowsRTE() {

    vertx.executeBlocking(() -> {
      throw new RuntimeException("rte");
    }).onComplete(onFailure(t -> {
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
      vertx.executeBlocking(() -> {
        assertSame(ctx, vertx.getOrCreateContext());
        assertTrue(Thread.currentThread().getName().startsWith("vert.x-worker-thread"));
        assertTrue(Context.isOnWorkerThread());
        assertFalse(Context.isOnEventLoopThread());
        try {
          Thread.sleep(1000);
        } catch (Exception ignore) {
        }
        CountDownLatch latch = new CountDownLatch(1);
        vertx.runOnContext(v2 -> {
          assertSame(ctx, vertx.getOrCreateContext());
          assertTrue(Thread.currentThread().getName().startsWith("vert.x-eventloop-thread"));
          assertFalse(Context.isOnWorkerThread());
          assertTrue(Context.isOnEventLoopThread());
          latch.countDown();
        });
        assertTrue(latch.await(20, TimeUnit.SECONDS));
        return "done!";
      }).onComplete(onSuccess(res -> {
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
    vertx.<String>executeBlocking(() -> {
      blockingTCCL.set(Thread.currentThread().getContextClassLoader());
      return "whatever";
    }).onComplete(onSuccess(res -> {
      assertEquals("whatever", res);
      latch.countDown();
    }));
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
        vertx.executeBlocking(() -> {
          assertSame(ctx, vertx.getOrCreateContext());
          assertTrue(Thread.currentThread().getName().startsWith("vert.x-worker-thread"));
          assertTrue(Context.isOnWorkerThread());
          assertFalse(Context.isOnEventLoopThread());
          try {
            Thread.sleep(pause);
          } catch (Exception ignore) {
          }
          return "done!";
        }, false).onComplete(onSuccess(res -> {
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
