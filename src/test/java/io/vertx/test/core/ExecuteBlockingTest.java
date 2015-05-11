/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.Context;
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
}
