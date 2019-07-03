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

import io.vertx.core.impl.*;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ContextTest extends VertxTestBase {

  private ExecutorService workerExecutor;

  private ContextInternal createWorkerContext() {
    return ((VertxInternal) vertx).createWorkerContext(null, new WorkerPool(workerExecutor, null), Thread.currentThread().getContextClassLoader());
  }

  @Override
  public void setUp() throws Exception {
    workerExecutor = Executors.newSingleThreadExecutor(r -> new VertxThread(r, "vert.x-worker-thread", true, 10, TimeUnit.SECONDS));
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    workerExecutor.shutdown();
    super.tearDown();
  }

  @Test
  public void testRunOnContext() throws Exception {
    vertx.runOnContext(v -> {
      Thread th = Thread.currentThread();
      Context ctx = Vertx.currentContext();
      ctx.runOnContext(v2 -> {
        assertEquals(th, Thread.currentThread());
        // Execute it a few times to make sure it returns same context
        for (int i = 0; i < 10; i++) {
          Context c = Vertx.currentContext();
          assertEquals(ctx, c);
        }
        // And simulate a third party thread - e.g. a 3rd party async library wishing to return a result on the
        // correct context
        new Thread() {
          public void run() {
            ctx.runOnContext(v3 -> {
              assertEquals(th, Thread.currentThread());
              assertEquals(ctx, Vertx.currentContext());
              testComplete();
            });
          }
        }.start();
      });
    });
    await();
  }

  @Test
  public void testNoContext() throws Exception {
    assertNull(Vertx.currentContext());
  }

  class SomeObject {
  }

  @Test
  public void testPutGetRemoveData() throws Exception {
    SomeObject obj = new SomeObject();
    vertx.runOnContext(v -> {
      Context ctx = Vertx.currentContext();
      ctx.put("foo", obj);
      ctx.runOnContext(v2 -> {
        assertEquals(obj, ctx.get("foo"));
        assertTrue(ctx.remove("foo"));
        ctx.runOnContext(v3 -> {
          assertNull(ctx.get("foo"));
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testGettingContextContextUnderContextAnotherInstanceShouldReturnDifferentContext() throws Exception {
    Vertx other = vertx();
    Context context = vertx.getOrCreateContext();
    context.runOnContext(v -> {
      Context otherContext = other.getOrCreateContext();
      assertNotSame(otherContext, context);
      testComplete();
    });
    await();
  }

  @Test
  public void testExecuteOrderedBlocking() throws Exception {
    Context context = vertx.getOrCreateContext();
    context.executeBlocking(f -> {
      assertTrue(Context.isOnWorkerThread());
      f.complete(1 + 2);
    }, r -> {
      assertTrue(Context.isOnEventLoopThread());
      assertEquals(r.result(), 3);
      testComplete();
    });
    await();
  }

  @Test
  public void testExecuteUnorderedBlocking() throws Exception {
    Context context = vertx.getOrCreateContext();
    context.executeBlocking(f -> {
      assertTrue(Context.isOnWorkerThread());
      f.complete(1 + 2);
    }, false, r -> {
      assertTrue(Context.isOnEventLoopThread());
      assertEquals(r.result(), 3);
      testComplete();
    });
    await();
  }

  @Test
  public void testExecuteBlockingThreadSyncComplete() throws Exception {
    Context context = vertx.getOrCreateContext();
    context.<Void>runOnContext(v -> {
      Thread expected = Thread.currentThread();
      context.executeBlocking(Promise::complete, r -> {
        assertSame(expected, Thread.currentThread());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testExecuteBlockingThreadAsyncComplete() throws Exception {
    Context context = vertx.getOrCreateContext();
    context.<Void>runOnContext(v -> {
      Thread expected = Thread.currentThread();
      context.executeBlocking(fut -> {
        new Thread(() -> {
          try {
            // Wait some time to allow the worker thread to set the handler on the future and have the future
            // handler callback to be done this thread
            Thread.sleep(200);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          fut.complete();
        }).start();
      }, r -> {
        assertSame(context, Vertx.currentContext());
        assertSame(expected, Thread.currentThread());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testEventLoopExecuteFromIo() throws Exception {
    ContextInternal eventLoopContext = (ContextInternal) vertx.getOrCreateContext();

    // Check from other thread
    try {
      eventLoopContext.executeFromIO(v -> fail());
      fail();
    } catch (IllegalStateException expected) {
    }

    // Check from event loop thread
    eventLoopContext.nettyEventLoop().execute(() -> {
      // Should not be set yet
      assertNull(Vertx.currentContext());
      Thread vertxThread = Thread.currentThread();
      AtomicBoolean nested = new AtomicBoolean(true);
      eventLoopContext.executeFromIO(v -> {
        assertTrue(nested.get());
        assertSame(eventLoopContext, Vertx.currentContext());
        assertSame(vertxThread, Thread.currentThread());
      });
      nested.set(false);
      testComplete();
    });
    await();
  }

  @Test
  public void testWorkerExecuteFromIo() {
    ContextInternal workerContext = createWorkerContext();
    workerContext.nettyEventLoop().execute(() -> {
      assertNull(Vertx.currentContext());
      workerContext.nettyEventLoop().execute(() -> {
        workerContext.executeFromIO(v -> {
          assertSame(workerContext, Vertx.currentContext());
          assertTrue(Context.isOnWorkerThread());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testContextExceptionHandler() {
    RuntimeException failure = new RuntimeException();
    Context context = vertx.getOrCreateContext();
    context.exceptionHandler(err -> {
      assertSame(context, Vertx.currentContext());
      assertSame(failure, err);
      testComplete();
    });
    context.runOnContext(v -> {
      throw failure;
    });
    await();
  }

  @Test
  public void testContextExceptionHandlerFailing() {
    RuntimeException failure = new RuntimeException();
    Context context = vertx.getOrCreateContext();
    AtomicInteger count = new AtomicInteger();
    context.exceptionHandler(err -> {
      if (count.getAndIncrement() == 0) {
        throw new RuntimeException();
      } else {
        assertSame(failure, err);
        testComplete();
      }
    });
    context.runOnContext(v -> {
      throw new RuntimeException();
    });
    context.runOnContext(v -> {
      throw failure;
    });
    await();
  }

  @Test
  public void testDefaultContextExceptionHandler() {
    RuntimeException failure = new RuntimeException();
    Context context = vertx.getOrCreateContext();
    vertx.exceptionHandler(err -> {
      assertSame(failure, err);
      testComplete();
    });
    context.runOnContext(v -> {
      throw failure;
    });
    await();
  }

  @Test
  public void testExceptionHandlerOnDeploymentAsyncResultHandlerFailure() {
    RuntimeException failure = new RuntimeException();
    Context ctx = vertx.getOrCreateContext();
    ctx.exceptionHandler(err -> {
      assertSame(failure, err);
      testComplete();
    });
    ctx.runOnContext(v -> {
      vertx.deployVerticle(new AbstractVerticle() {
        @Override
        public void start() throws Exception {
        }
      }, ar -> {
        throw failure;
      });
    });
    await();
  }

  @Test
  public void testExceptionHandlerOnAsyncDeploymentAsyncResultHandlerFailure() {
    RuntimeException failure = new RuntimeException();
    Context ctx = vertx.getOrCreateContext();
    ctx.exceptionHandler(err -> {
      assertSame(failure, err);
      testComplete();
    });
    ctx.runOnContext(v -> {
      vertx.deployVerticle(new AbstractVerticle() {
        @Override
        public void start(Promise<Void> startFuture) throws Exception {
          context.runOnContext(startFuture::complete);
        }
      }, ar -> {
        throw failure;
      });
    });
    await();
  }

  @Test
  public void testExceptionInExecutingBlockingWithContextExceptionHandler() {
    RuntimeException expected = new RuntimeException("test");
    Context context = vertx.getOrCreateContext();
    context.exceptionHandler(t -> {
      assertSame(expected, t);
      complete();
    });
    vertx.exceptionHandler(t -> {
      fail("Should not be invoked");
    });
    context.executeBlocking(promise -> {
      throw expected;
    }, null);
    await();
  }

  @Test
  public void testExceptionInExecutingBlockingWithVertxExceptionHandler() {
    RuntimeException expected = new RuntimeException("test");
    Context context = vertx.getOrCreateContext();
    vertx.exceptionHandler(t -> {
      assertSame(expected, t);
      complete();
    });
    context.executeBlocking(promise -> {
      throw expected;
    }, null);
    await();
  }

  @Test
  public void testVerticleUseDifferentExecuteBlockingOrderedExecutor() throws Exception {
    testVerticleUseDifferentOrderedExecutor(false);
  }

  @Test
  public void testWorkerVerticleUseDifferentExecuteBlockingOrderedExecutor() throws Exception {
    testVerticleUseDifferentOrderedExecutor(true);
  }

  private void testVerticleUseDifferentOrderedExecutor(boolean worker) throws Exception {
    waitFor(2);
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        vertx.executeBlocking(fut -> {
          latch1.countDown();
          try {
            awaitLatch(latch2);
            fut.complete();
          } catch (InterruptedException e) {
            fut.fail(e);
          }
        }, ar -> {
          assertTrue(ar.succeeded());
          complete();
        });
      }
    }, new DeploymentOptions().setWorker(worker));
    awaitLatch(latch1);
    CountDownLatch latch3 = new CountDownLatch(1);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        vertx.executeBlocking(fut -> {
          latch3.countDown();
          fut.complete();
        }, ar -> {
          assertTrue(ar.succeeded());
          complete();
        });
      }
    }, new DeploymentOptions().setWorker(worker));
    awaitLatch(latch3);
    latch2.countDown();
    await();
  }

  @Test
  public void testInternalExecuteBlockingWithQueue() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    TaskQueue[] queues = new TaskQueue[] { new TaskQueue(), new TaskQueue()};
    AtomicReference<Thread>[] current = new AtomicReference[queues.length];
    waitFor(queues.length);
    for (int i = 0;i < queues.length;i++) {
      current[i] = new AtomicReference<>();
    }
    CyclicBarrier barrier = new CyclicBarrier(queues.length);
    int numTasks = 10;
    for (int i = 0;i < numTasks;i++) {
      int ival = i;
      for (int j = 0;j < queues.length;j++) {
        int jval = j;
        context.executeBlocking(fut -> {
          if (ival == 0) {
            current[jval].set(Thread.currentThread());
          } else {
            assertSame(Thread.currentThread(), current[jval].get());
          }
          try {
            barrier.await();
          } catch (Exception e) {
            fail(e);
          }
          if (ival == numTasks - 1) {
            complete();
          }
        }, queues[j], ar -> {});
      }
    }
    await();
  }

  @Test
  public void testExecuteFromIOEventLoopFromNonVertxThread() {
    assertEquals("true", System.getProperty("vertx.threadChecks"));
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    AtomicBoolean called = new AtomicBoolean();
    try {
      ctx.executeFromIO(v -> {
        called.set(true);
      });
      fail();
    } catch (IllegalStateException ignore) {
      //
    }
    assertFalse(called.get());
  }

  @Test
  public void testExecuteFromIOWorkerFromNonVertxThread() {
    assertEquals("true", System.getProperty("vertx.threadChecks"));
    ContextInternal ctx = createWorkerContext();
    AtomicBoolean called = new AtomicBoolean();
    try {
      ctx.executeFromIO(v -> {
        called.set(true);
      });
      fail();
    } catch (IllegalStateException ignore) {
      //
    }
    assertFalse(called.get());
  }

  @Test
  public void testReportExceptionToContext() {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    RuntimeException expected = new RuntimeException();
    AtomicReference<Throwable> err = new AtomicReference<>();
    ctx.exceptionHandler(err::set);
    ctx.reportException(expected);
    assertSame(expected, err.get());
  }

  @Test
  public void testDuplicate() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ContextInternal duplicate = ctx.duplicate();
    checkDuplicate(ctx, duplicate);
  }

  @Test
  public void testDuplicateWorker() throws Exception {
    ContextInternal ctx = createWorkerContext();
    ContextInternal duplicate = ctx.duplicate();
    checkDuplicate(ctx, duplicate);
  }

  @Test
  public void testDuplicateTwice() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ContextInternal duplicated = ctx.duplicate().duplicate();
    checkDuplicate(ctx, duplicated);
  }

  @Test
  public void testDuplicateWith() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ContextInternal other = (ContextInternal) vertx.getOrCreateContext();
    ContextInternal duplicated = ctx.duplicate(other);
    checkDuplicate(ctx, duplicated);
    checkDuplicateWith(other, duplicated);
  }

  private void checkDuplicate(ContextInternal ctx, ContextInternal duplicated) throws Exception {
    assertSame(ctx.nettyEventLoop(), duplicated.nettyEventLoop());
    assertSame(ctx.getDeployment(), duplicated.getDeployment());
    assertSame(ctx.classLoader(), duplicated.classLoader());
    assertSame(ctx.owner(), duplicated.owner());
    Object shared = new Object();
    Object local = new Object();
    ctx.put("key", shared);
    ctx.putLocal("key", local);
    assertSame(shared, duplicated.get("key"));
    assertNull(duplicated.getLocal("key"));
    assertTrue(duplicated.remove("key"));
    assertNull(ctx.get("key"));

    CountDownLatch latch1 = new CountDownLatch(1);
    duplicated.runOnContext(v -> {
      assertSame(Vertx.currentContext(), duplicated);
      latch1.countDown();
    });
    awaitLatch(latch1);

    CountDownLatch latch2 = new CountDownLatch(1);
    Throwable failure = new Throwable();
    ctx.exceptionHandler(err -> {
      assertSame(failure, err);
      latch2.countDown();
    });
    duplicated.reportException(failure);
    awaitLatch(latch2);

    CountDownLatch latch3 = new CountDownLatch(1);
    duplicated.runOnContext(v -> {
      vertx.setTimer(10, id -> {
        assertSame(duplicated, Vertx.currentContext());
        latch3.countDown();
      });
    });
    awaitLatch(latch3);

    CountDownLatch latch4 = new CountDownLatch(1);
    duplicated.runOnContext(v -> {
      vertx.executeBlocking(Promise::complete, res -> {
        assertSame(duplicated, Vertx.currentContext());
        latch4.countDown();
      });
    });
    awaitLatch(latch4);
  }

  @Test
  public void testDuplicateWithTwice() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ContextInternal other = (ContextInternal) vertx.getOrCreateContext();
    ContextInternal duplicated = ctx.duplicate().duplicate(other);
    checkDuplicate(ctx, duplicated);
    checkDuplicateWith(other, duplicated);
  }

  private void checkDuplicateWith(ContextInternal ctx, ContextInternal duplicated) {
    Object val = new Object();
    ctx.putLocal("key", val);
    assertSame(val, duplicated.getLocal("key"));
    duplicated.removeLocal("key");
    assertNull(ctx.getLocal("key"));
  }

  @Test
  public void testReentrantDispatch() {
    ClassLoader cl = new URLClassLoader(new URL[0]);
    Thread.currentThread().setContextClassLoader(cl);
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ctx.runOnContext(v1 -> {
      assertSame(ctx, Vertx.currentContext());
      assertSame(cl, Thread.currentThread().getContextClassLoader());
      int[] called = new int[1];
      VertxThread thread = VertxThread.current();
      long start = thread.startTime();
      ctx.dispatch(v2 -> {
        called[0]++;
        assertSame(cl, Thread.currentThread().getContextClassLoader());
        try {
          Thread.sleep(2);
        } catch (InterruptedException e) {
          fail(e);
        }
      });
      assertEquals(start, thread.startTime());
      assertEquals(1, called[0]);
      assertSame(ctx, Vertx.currentContext());
      assertSame(cl, Thread.currentThread().getContextClassLoader());
      testComplete();
    });
    await();
  }
}
