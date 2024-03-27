/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core;

import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.WorkerExecutor;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadContextTest extends VertxTestBase {

  VertxInternal vertx;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    vertx = (VertxInternal) super.vertx;
  }

  @Test
  public void testContext() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    vertx.createVirtualThreadContext().runOnContext(v -> {
      Thread thread = Thread.currentThread();
      assertTrue(VirtualThreadDeploymentTest.isVirtual(thread));
      ContextInternal context = vertx.getOrCreateContext();
      Executor executor = context.executor();
      assertTrue(executor instanceof WorkerExecutor);
      context.runOnContext(v2 -> {
        // assertSame(thread, Thread.currentThread());
        assertSame(context, vertx.getOrCreateContext());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAwaitFutureSuccess() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    Object result = new Object();
    vertx.createVirtualThreadContext().runOnContext(v -> {
      ContextInternal context = vertx.getOrCreateContext();
      PromiseInternal<Object> promise = context.promise();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        promise.complete(result);
      }).start();
      assertSame(result, Future.await(promise.future()));
      testComplete();
    });
    await();
  }

  @Test
  public void testAwaitFutureFailure() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    Exception failure = new Exception();
    vertx.createVirtualThreadContext().runOnContext(v -> {
      ContextInternal context = vertx.getOrCreateContext();
      PromiseInternal<Object> promise = context.promise();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        promise.fail(failure);
      }).start();
      try {
        Future.await(promise.future());
      } catch (Exception e) {
        assertSame(failure, e);
        testComplete();
        return;
      }
      fail();
    });
    await();
  }

  @Test
  public void testAwaitCompoundFuture() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    Object result = new Object();
    vertx.createVirtualThreadContext().runOnContext(v -> {
      ContextInternal context = vertx.getOrCreateContext();
      PromiseInternal<Object> promise = context.promise();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        promise.complete(result);
      }).start();
      assertSame("HELLO", Future.await(promise.future().map(res -> "HELLO")));
      testComplete();
    });
    await();
  }

  @Test
  public void testDuplicateUseSameThread() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    int num = 1000;
    waitFor(num);
    vertx.createVirtualThreadContext().runOnContext(v -> {
      ContextInternal context = vertx.getOrCreateContext();
      Thread th = Thread.currentThread();
      for (int i = 0;i < num;i++) {
        ContextInternal duplicate = context.duplicate();
        duplicate.runOnContext(v2 -> {
          // assertSame(th, Thread.currentThread());
          complete();
        });
      }
    });
    await();
  }

  @Test
  public void testDuplicateConcurrentAwait() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    int num = 1000;
    waitFor(num);
    vertx.createVirtualThreadContext().runOnContext(v -> {
      ContextInternal context = vertx.getOrCreateContext();
      Object lock = new Object();
      List<Promise<Void>> list = new ArrayList<>();
      for (int i = 0;i < num;i++) {
        ContextInternal duplicate = context.duplicate();
        duplicate.runOnContext(v2 -> {
          Promise<Void> promise = duplicate.promise();
          boolean complete;
          synchronized (lock) {
            list.add(promise);
            complete = list.size() == num;
          }
          if (complete) {
            context.runOnContext(v3 -> {
              synchronized (lock) {
                list.forEach(p -> p.complete(null));
              }
            });
          }
          Future<Void> f = promise.future();
          Future.await(f);
          complete();
        });
      }
    });
    await();
  }

  @Test
  public void testTimer() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    vertx.createVirtualThreadContext().runOnContext(v -> {
      ContextInternal context = vertx.getOrCreateContext();
      PromiseInternal<String> promise = context.promise();
      vertx.setTimer(100, id -> {
        promise.complete("foo");
      });
      String res = Future.await(promise);
      assertEquals("foo", res);
      testComplete();
    });
    await();
  }

  @Test
  public void testInThread() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    vertx.createVirtualThreadContext().runOnContext(v1 -> {
      ContextInternal context = vertx.getOrCreateContext();
      assertTrue(context.inThread());
      new Thread(() -> {
        boolean wasNotInThread = !context.inThread();
        context.runOnContext(v2 -> {
          assertTrue(wasNotInThread);
          assertTrue(context.inThread());
          testComplete();
        });
      }).start();
    });
    await();
  }

  private void sleep(AtomicInteger inflight) {
    assertEquals(0, inflight.getAndIncrement());
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      inflight.decrementAndGet();
    }
  }

  @Test
  public void testSerializeBlocking() throws Exception {
    Assume.assumeTrue(isVirtualThreadAvailable());
    AtomicInteger inflight = new AtomicInteger();
    vertx.createVirtualThreadContext().runOnContext(v1 -> {
      Context ctx = vertx.getOrCreateContext();
      for (int i = 0;i < 10;i++) {
        ctx.runOnContext(v2 -> sleep(inflight));
      }
      ctx.runOnContext(v -> testComplete());
    });
    await();
  }

  @Test
  public void testVirtualThreadsNotAvailable() {
    Assume.assumeFalse(isVirtualThreadAvailable());
    try {
      vertx.createVirtualThreadContext();
      fail();
    } catch (IllegalStateException expected) {
    }
  }
}
