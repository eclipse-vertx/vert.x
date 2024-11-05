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
package io.vertx.tests.virtualthread;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.impl.WorkerExecutor;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.test.core.VertxTestBase;
import io.vertx.tests.deployment.VirtualThreadDeploymentTest;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
      assertSame(result, promise.future().await());
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
        promise.future().await();
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
      assertSame("HELLO", promise.future().map(res -> "HELLO").await());
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
          f.await();
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
      String res = promise.await();
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

  @Test
  public void testVirtualThreadInterruptOnClose() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    ContextInternal ctx = vertx.createVirtualThreadContext();
    ctx.exceptionHandler(err -> {

    });
    Promise<Void> promise = ctx.promise();
    AtomicReference<Thread> ref = new AtomicReference<>();
    AtomicBoolean interrupted = new AtomicBoolean();
    ctx.runOnContext(v -> {
      try {
        ref.set(Thread.currentThread());
        Future<Void> fut = promise.future();
        fut.await();
        fail();
      } catch (Throwable e) {
        if (e instanceof InterruptedException) {
          interrupted.set(true);
        }
        throw e;
      }
    });
    assertWaitUntil(() -> ref.get() != null && ref.get().getState() == Thread.State.WAITING);
    ctx.close().await();
    assertWaitUntil(interrupted::get);
  }

  @Test
  public void testVirtualThreadInterruptOnClose2() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    ContextInternal ctx = vertx.createVirtualThreadContext();
    AtomicReference<Thread> ref = new AtomicReference<>();
    AtomicBoolean interrupted = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    ctx.runOnContext(v -> {
      try {
        ref.set(Thread.currentThread());
        latch.await();
        fail();
      } catch (InterruptedException e) {
        interrupted.set(true);
      }
    });
    assertWaitUntil(() -> ref.get() != null && ref.get().getState() == Thread.State.WAITING);
    ctx.close().await();
    assertWaitUntil(interrupted::get);
  }

  @Test
  public void testContextCloseContextSerialization() throws Exception {
    int num = 4;
    Assume.assumeTrue(isVirtualThreadAvailable());
    ContextInternal ctx = vertx.createVirtualThreadContext();
    Thread[] threads = new Thread[num];
    List<Promise<Void>> promises = IntStream.range(0, num).mapToObj(idx -> Promise.<Void>promise()).collect(Collectors.toList());
    Deque<CyclicBarrier> latches = new ConcurrentLinkedDeque<>();
    CyclicBarrier[] l = new CyclicBarrier[num];
    AtomicInteger count = new AtomicInteger();
    for (int i = 0;i < num;i++) {
      int idx = i;
      CyclicBarrier latch = new CyclicBarrier(2);
      l[i] = latch;
      latches.add(latch);
      ctx.runOnContext(v -> {
        threads[idx] = Thread.currentThread();
        try {
          promises.get(idx).future().await();
          fail();
        } catch (Exception e) {
          assertTrue(e instanceof InterruptedException);
          CyclicBarrier barrier = latches.removeFirst();
          int val = count.addAndGet(1);
          assertTrue(val == 1);
          try {
            barrier.await();
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          } finally {
            count.decrementAndGet();
          }
        }
      });
    }
    assertWaitUntil(() -> {
      for (Thread thread : threads) {
        if (thread == null || thread.getState() != Thread.State.WAITING) {
          return false;
        }
      }
      return true;
    });
    Future<Void> f = ctx.close();
    for (int i = 0;i < num;i++) {
      try {
        l[i].await();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      } catch (BrokenBarrierException e) {
        throw new RuntimeException(e);
      }
    }
    f.await();
  }

  @Test
  public void testAwaitWhenClosed() throws Exception {
    Assume.assumeTrue(isVirtualThreadAvailable());
    ContextInternal ctx = vertx.createVirtualThreadContext();
    CountDownLatch latch = new CountDownLatch(1);
    ctx.runOnContext(v -> {
      latch.countDown();
      try {
        new CountDownLatch(1).await();
        fail();
      } catch (InterruptedException expected) {
        assertFalse(Thread.currentThread().isInterrupted());
      }
      try {
        Promise.promise().future().await();
        fail();
      } catch (Exception e) {
        assertEquals(InterruptedException.class, e.getClass());
        testComplete();
      }
    });
    awaitLatch(latch);
    // Interrupts virtual thread
    ctx.close();
    await();
  }

  @Test
  public void testSubmitAfterClose() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    ContextInternal ctx = vertx.createVirtualThreadContext();
    ctx.close();
    ctx.runOnContext(v -> {
      testComplete();
    });
    await();
  }
}
