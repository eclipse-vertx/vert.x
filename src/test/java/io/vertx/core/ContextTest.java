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

package io.vertx.core;

import io.netty.channel.EventLoop;
import io.vertx.core.impl.*;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ContextTest extends VertxTestBase {

  private ExecutorService workerExecutor;

  private ContextInternal createWorkerContext() {
    return ((VertxInternal) vertx).createWorkerContext(null, null, new WorkerPool(workerExecutor, null), Thread.currentThread().getContextClassLoader());
  }

  @Override
  public void setUp() throws Exception {
    workerExecutor = Executors.newFixedThreadPool(2, r -> new VertxThread(r, "vert.x-worker-thread", true, 10, TimeUnit.SECONDS));
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
        public void start(Promise<Void> startPromise) throws Exception {
          context.runOnContext(startPromise::complete);
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
  public void testEventLoopContextDispatchReportsFailure() {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    RuntimeException failure = new RuntimeException();
    AtomicReference<Throwable> caught = new AtomicReference<>();
    ctx.exceptionHandler(caught::set);
    ctx.dispatch(new Object(), event -> {
      throw failure;
    });
    assertWaitUntil(() -> caught.get() == failure);
  }

  @Test
  public void testWorkerContextDispatchReportsFailure() {
    ContextInternal ctx = createWorkerContext();
    RuntimeException failure = new RuntimeException();
    AtomicReference<Throwable> caught = new AtomicReference<>();
    ctx.exceptionHandler(caught::set);
    ctx.dispatch(new Object(), event -> {
      throw failure;
    });
    assertWaitUntil(() -> caught.get() == failure);
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
  public void testDuplicateWorkerConcurrency() throws Exception {
    ContextInternal ctx = createWorkerContext();
    ContextInternal dup1 = ctx.duplicate();
    ContextInternal dup2 = ctx.duplicate();
    CyclicBarrier barrier = new CyclicBarrier(3);
    dup1.runOnContext(v -> {
      assertTrue(Context.isOnWorkerThread());
      try {
        barrier.await(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        fail(e);
      }
    });
    dup2.runOnContext(v -> {
      assertTrue(Context.isOnWorkerThread());
      try {
        barrier.await(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        fail(e);
      }
    });
    barrier.await(10, TimeUnit.SECONDS);
  }

  @Test
  public void testDuplicateEventLoopExecuteBlocking() throws Exception {
    testDuplicateExecuteBlocking((ContextInternal) vertx.getOrCreateContext());
  }

  @Test
  public void testDuplicateWorkerExecuteBlocking() throws Exception {
    testDuplicateExecuteBlocking(createWorkerContext());
  }

  private void testDuplicateExecuteBlocking(ContextInternal ctx) throws Exception {
    ContextInternal dup1 = ctx.duplicate();
    ContextInternal dup2 = ctx.duplicate();
    CyclicBarrier barrier = new CyclicBarrier(3);
    dup1.executeBlocking(p -> {
      assertTrue(Context.isOnWorkerThread());
      try {
        barrier.await(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        fail(e);
      }
      p.complete();
    });
    dup2.executeBlocking(p -> {
      assertTrue(Context.isOnWorkerThread());
      try {
        barrier.await(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        fail(e);
      }
      p.complete();
    });
    barrier.await(10, TimeUnit.SECONDS);
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
      BlockedThreadChecker.Task thread = (BlockedThreadChecker.Task) Thread.currentThread();
      long start = thread.startTime();
      ctx.emit(v2 -> {
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

  @Test
  public void testEventLoopContextPromiseReentrantSuccess() {
    testEventLoopContextPromiseReentrantCompletion(p -> p.complete("the-value"));
  }

  private void testEventLoopContextPromiseReentrantCompletion(Consumer<Promise<String>> action) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = context.promise();
    context.runOnContext(v -> {
      Thread th = Thread.currentThread();
      promise.future().onComplete(ar -> {
        assertSame(th, Thread.currentThread());
        testComplete();
      });
      action.accept(promise);
    });
    await();
  }

  @Test
  public void testEventLoopContextPromiseReentrantFailingSuccess() {
    testEventLoopContextPromiseReentrantFailingCompletion(p -> p.complete("the-value"));
  }

  @Test
  public void testEventLoopContextPromiseReentrantFailingFailure() {
    testEventLoopContextPromiseReentrantFailingCompletion(p -> p.fail(new Exception()));
  }

  private void testEventLoopContextPromiseReentrantFailingCompletion(Consumer<Promise<String>> action) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = context.promise();
    context.runOnContext(v1 -> {
      List<Throwable> exceptions = new ArrayList<>();
      context.exceptionHandler(exceptions::add);
      RuntimeException failure = new RuntimeException();
      promise.future().onComplete(ar -> {
        context.runOnContext(v2 -> {
          assertEquals(1, exceptions.size());
          assertSame(failure, exceptions.get(0));
          testComplete();
        });
        throw failure;
      });
      action.accept(promise);
    });
    await();
  }

  @Test
  public void testEventLoopContextPromiseSucceededByAnotherEventLoopThread() {
    testEventLoopContextPromiseCompletedByAnotherEventLoopThread(p -> p.complete("the-value"));
  }

  @Test
  public void testEventLoopContextPromiseFailedByAnotherEventLoopThread() {
    testEventLoopContextPromiseCompletedByAnotherEventLoopThread(p -> p.fail(new Exception()));
  }

  void testEventLoopContextPromiseCompletedByAnotherEventLoopThread(Consumer<Promise<String>> action) {
    Context any = vertx.getOrCreateContext();
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = context.promise();
    context.runOnContext(v1 -> {
      Thread th = Thread.currentThread();
      promise.future().onComplete(ar -> {
        assertSame(th, Thread.currentThread());
        testComplete();
      });
      any.runOnContext(v2 -> {
        action.accept(promise);
      });
    });
    await();
  }

  @Test
  public void testEventLoopContextPromiseSucceededByWorkerThread() {
    testEventLoopContextPromiseCompletedByWorkerThread(p -> p.complete("the-value"));
  }

  @Test
  public void testEventLoopContextPromiseFailedByWorkerThread() {
    testEventLoopContextPromiseCompletedByWorkerThread(p -> p.fail(new Exception()));
  }

  private void testEventLoopContextPromiseCompletedByWorkerThread(Consumer<Promise<String>> action) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = context.promise();
    context.runOnContext(v -> {
      Thread th = Thread.currentThread();
      promise.future().onComplete(ar -> {
        assertSame(th, Thread.currentThread());
        testComplete();
      });
      context.executeBlocking(fut -> {
        action.accept(promise);
      });
    });
    await();
  }

  @Test
  public void testEventLoopContextPromiseSucceededByNonVertxThread() {
    testEventLoopContextPromiseCompletedByNonVertxThread(p -> p.complete("the-value"));
  }

  @Test
  public void testEventLoopContextPromiseFailedByNonVertxThread() {
    testEventLoopContextPromiseCompletedByNonVertxThread(p -> p.fail(new Exception()));
  }

  private void testEventLoopContextPromiseCompletedByNonVertxThread(Consumer<Promise<String>> action) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = context.promise();
    context.runOnContext(v -> {
      Thread th = Thread.currentThread();
      promise.future().onComplete(ar -> {
        assertSame(th, Thread.currentThread());
        testComplete();
      });
      new Thread(() -> action.accept(promise)).start();
    });
    await();
  }

  @Test
  public void testEventLoopContextPromiseListenerSuccess() {
    testEventLoopContextPromiseListenerCompletion(p -> p.setSuccess("the-value"));
  }

  @Test
  public void testEventLoopContextPromiseListenerFailure() {
    testEventLoopContextPromiseListenerCompletion(p -> p.setFailure(new Exception()));
  }

  private void testEventLoopContextPromiseListenerCompletion(Consumer<io.netty.util.concurrent.Promise<String>> action) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    PromiseInternal<String> promise = context.promise();
    promise.future().onComplete(ar -> {
      assertSame(context, Vertx.currentContext());
      testComplete();
    });
    EventLoop eventLoop = context.nettyEventLoop();
    action.accept(eventLoop.<String>newPromise().addListener(promise));
    await();
  }

  @Test
  public void testComposeContextPropagation1() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = context.promise();
    Future<String> future = promise.future().compose(res -> Future.succeededFuture("value-2"));
    promise.complete("value-1");
    future.onComplete(ar -> {
      assertSame(context, vertx.getOrCreateContext());
      testComplete();
    });
    await();
  }

  @Test
  public void testComposeContextPropagation2() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = context.promise();
    Future<String> future = promise.future().compose(res -> Future.succeededFuture("value-2"));
    future.onComplete(ar -> {
      assertSame(context, vertx.getOrCreateContext());
      testComplete();
    });
    promise.complete("value-1");
    await();
  }

  @Test
  public void testComposeContextPropagation3() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = context.promise();
    ContextInternal anotherContext = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> anotherPromise = anotherContext.promise();
    Future<String> future = promise.future().compose(res -> anotherPromise.future());
    promise.complete("value-1");
    future.onComplete(ar -> {
      assertSame(context, vertx.getOrCreateContext());
      testComplete();
    });
    anotherPromise.complete("value-2");
    await();
  }

  @Test
  public void testEventLoopExecutor() {
    waitFor(2);
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    context.execute(() -> {
      assertTrue(Context.isOnEventLoopThread());
      assertSame(context, Vertx.currentContext());
      complete();
    });
    RuntimeException failure = new RuntimeException();
    context.exceptionHandler(err -> {
      assertSame(failure, err);
      complete();
    });
    context.execute(() -> {
      throw failure;
    });
    await();
  }

  @Test
  public void testWorkerExecutor() {
    waitFor(2);
    ContextInternal context = createWorkerContext();
    context.execute(() -> {
      assertTrue(Context.isOnWorkerThread());
      assertSame(context, Vertx.currentContext());
      complete();
    });
    RuntimeException failure = new RuntimeException();
    context.exceptionHandler(err -> {
      assertSame(failure, err);
      complete();
    });
    context.execute(() -> {
      throw failure;
    });
    await();
  }

  @Test
  public void testSticky() {
    Context ctx = vertx.getOrCreateContext();
    assertSame(ctx, vertx.getOrCreateContext());
  }
}
