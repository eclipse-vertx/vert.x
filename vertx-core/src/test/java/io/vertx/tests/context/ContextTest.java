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

package io.vertx.tests.context;

import io.netty.channel.EventLoop;
import io.vertx.core.*;
import io.vertx.core.Future;
import io.vertx.core.impl.*;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.core.spi.context.storage.ContextLocal;
import io.vertx.test.core.ContextLocalHelper;
import io.vertx.test.core.VertxTestBase;
import org.junit.Assume;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ContextTest extends VertxTestBase {

  private ContextLocal<Object> contextLocal;
  private ExecutorService workerExecutor;

  private ContextInternal createWorkerContext() {
    return ((VertxInternal) vertx).createWorkerContext(null, new CloseFuture(), new WorkerPool(workerExecutor, null), Thread.currentThread().getContextClassLoader());
  }

  @Override
  public void setUp() throws Exception {
    contextLocal = ContextLocal.registerLocal(Object.class);
    workerExecutor = Executors.newFixedThreadPool(2, r -> new VertxThread(r, "vert.x-worker-thread", true, 10, TimeUnit.SECONDS));
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    ContextLocalHelper.reset();
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
    context.<Integer>executeBlocking(() -> {
      assertTrue(Context.isOnWorkerThread());
      return 1 + 2;
    }).onComplete(onSuccess(r -> {
      assertTrue(Context.isOnEventLoopThread());
      assertEquals((int)r, 3);
      testComplete();
    }));
    await();
  }

  @Test
  public void testExecuteUnorderedBlocking() throws Exception {
    Context context = vertx.getOrCreateContext();
    context.executeBlocking(() -> {
        assertTrue(Context.isOnWorkerThread());
        return 1 + 2;
      }, false)
      .onComplete(onSuccess(r -> {
        assertTrue(Context.isOnEventLoopThread());
        assertEquals((int)r, 3);
        testComplete();
      }));
    await();
  }

  @Test
  public void testExecuteBlockingThreadSyncComplete() throws Exception {
    Context context = vertx.getOrCreateContext();
    context.<Void>runOnContext(v -> {
      Thread expected = Thread.currentThread();
      context.executeBlocking(() -> null).onComplete(onSuccess(r -> {
        assertSame(expected, Thread.currentThread());
        testComplete();
      }));
    });
    await();
  }

  @Test
  public void testExecuteBlockingThreadAsyncComplete() throws Exception {
    Context context = vertx.getOrCreateContext();
    context.<Void>runOnContext(v -> {
      Thread expected = Thread.currentThread();
      context.executeBlocking(() -> {
        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> {
          try {
            // Wait some time to allow the worker thread to set the handler on the future and have the future
            // handler callback to be done this thread
            Thread.sleep(200);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          latch.countDown();
        }).start();
        latch.await(20, TimeUnit.SECONDS);
        return null;
      }).onComplete(onSuccess(r -> {
        assertSame(context, Vertx.currentContext());
        assertSame(expected, Thread.currentThread());
        testComplete();
      }));
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
  public void testExecuteBlockingClose() {
    CountDownLatch latch = new CountDownLatch(1);
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    AtomicReference<Thread> thread = new AtomicReference<>();
    Future<String> fut1 = ctx.executeBlocking(() -> {
      thread.set(Thread.currentThread());
      latch.await();
      return "";
    });
    Future<String> fut2 = ctx.executeBlocking(() -> "");
    assertWaitUntil(() -> thread.get() != null && thread.get().getState() == Thread.State.WAITING);
    ctx.close();
    assertWaitUntil(fut1::isComplete);
    assertTrue(fut1.failed());
    assertTrue(fut1.cause() instanceof InterruptedException);
    assertWaitUntil(fut2::isComplete);
    assertTrue(fut2.failed());
    assertTrue(fut2.cause() instanceof RejectedExecutionException);
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
      }).onComplete(ar -> {
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
      }).onComplete(ar -> {
        throw failure;
      });
    });
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
        vertx.executeBlocking(() -> {
          latch1.countDown();
          awaitLatch(latch2);
          return null;
        }).onComplete(onSuccess(v -> complete()));
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    awaitLatch(latch1);
    CountDownLatch latch3 = new CountDownLatch(1);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        vertx.executeBlocking(() -> {
          latch3.countDown();
          return null;
        }).onComplete(onSuccess(v -> {
          complete();
        }));
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    awaitLatch(latch3);
    latch2.countDown();
    await();
  }

  public void testInternalExecuteBlockingWithQueue(List<Consumer<Callable<Object>>> lst) {
    AtomicReference<Thread>[] current = new AtomicReference[lst.size()];
    waitFor(lst.size());
    for (int i = 0;i < current.length;i++) {
      current[i] = new AtomicReference<>();
    }
    CyclicBarrier barrier = new CyclicBarrier(2);
    CountDownLatch latch = new CountDownLatch(3);
    int numTasks = 10;
    for (int i = 0;i < numTasks;i++) {
      int ival = i;
      for (int j = 0;j < lst.size();j++) {
        int jval = j;
        Callable<Object> task = () -> {
          if (ival == 0) {
            current[jval].set(Thread.currentThread());
            latch.countDown();
            try {
              latch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              fail(e);
            }
          } else {
            assertSame(Thread.currentThread(), current[jval].get());
            try {
              barrier.await();
            } catch (Exception e) {
              fail(e);
            }
          }
          if (ival == numTasks - 1) {
            complete();
          }
          return null;
        };
        lst.get(j).accept(task);
      }
    }
    latch.countDown();
    await();
  }

  @Test
  public void testExecuteBlockingUseItsOwnTaskQueue() {
    Context ctx = ((VertxInternal)vertx).createWorkerContext();
    CountDownLatch latch = new CountDownLatch(1);
    ctx.runOnContext(v -> {
      ctx.executeBlocking(() -> {
        latch.countDown();
        return 0;
      });
      boolean timedOut;
      try {
        timedOut = !latch.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      assertFalse(timedOut);
      testComplete();
    });
    await();
  }

  @Test
  public void testEventLoopContextDispatchReportsFailure() {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    RuntimeException failure = new RuntimeException();
    AtomicReference<Throwable> caught = new AtomicReference<>();
    ctx.exceptionHandler(caught::set);
    ctx.emit(new Object(), event -> {
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
    ctx.emit(new Object(), event -> {
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
    assertSame(ctx.deployment(), duplicated.deployment());
    assertSame(ctx.classLoader(), duplicated.classLoader());
    assertSame(ctx.owner(), duplicated.owner());
    Object shared = new Object();
    Object local = new Object();
    ctx.put("key", shared);
    contextLocal.put(ctx, local);
    assertSame(shared, duplicated.get("key"));
    assertNull(duplicated.getLocal(contextLocal));
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
      vertx.executeBlocking(() -> null).onComplete(onSuccess(res -> {
        assertSame(duplicated, Vertx.currentContext());
        latch4.countDown();
      }));
    });
    awaitLatch(latch4);
  }

  @Test
  public void testDuplicateWorkerConcurrency() throws Exception {
    testDuplicateWorkerConcurrency((ctx, task) -> ctx.runOnContext(v -> task.run()));
    testDuplicateWorkerConcurrency((ctx, task) -> ctx.execute(v -> task.run()));
    testDuplicateWorkerConcurrency((ctx, task) -> ctx.execute(null, v -> task.run()));
    testDuplicateWorkerConcurrency(ContextInternal::execute);
    testDuplicateWorkerConcurrency((ctx, task) -> ctx.emit(v -> task.run()));
    testDuplicateWorkerConcurrency((ctx, task) -> ctx.emit(null, v -> task.run()));
  }

  private void testDuplicateWorkerConcurrency(BiConsumer<ContextInternal, Runnable> task) throws Exception {
    ContextInternal worker = ((VertxInternal)vertx).createWorkerContext();
    ContextInternal[] contexts = new ContextInternal[] { worker.duplicate(), worker.duplicate()};
    waitFor(contexts.length);
    AtomicBoolean owner = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(contexts.length);
    for (ContextInternal context : contexts) {
      task.accept(context, () -> {
        try {
          assertTrue(owner.compareAndSet(false, true));
          Thread.sleep(200);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          owner.set(false);
        }
        latch.countDown();
      });
    }
    awaitLatch(latch);
  }

  @Test
  public void testEventLoopExecuteBlockingOrdered() {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    testDuplicateExecuteBlocking(() -> ctx, true);
  }

  @Test
  public void testWorkerExecuteBlockingOrdered() {
    ContextInternal ctx = createWorkerContext();
    testDuplicateExecuteBlocking(() -> ctx, true);
  }

  @Test
  public void testEventLoopExecuteBlockingUnordered() {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    testDuplicateExecuteBlocking(() -> ctx, false);
  }

  @Test
  public void testWorkerExecuteBlockingUnordered() {
    ContextInternal ctx = createWorkerContext();
    testDuplicateExecuteBlocking(() -> ctx, false);
  }

  @Test
  public void testDuplicateEventLoopExecuteBlockingOrdered() {
    testDuplicateExecuteBlocking(((ContextInternal) vertx.getOrCreateContext())::duplicate, true);
  }

  @Test
  public void testDuplicateWorkerExecuteBlockingOrdered() {
    testDuplicateExecuteBlocking(createWorkerContext()::duplicate, true);
  }

  @Test
  public void testDuplicateEventLoopExecuteBlockingUnordered() {
    testDuplicateExecuteBlocking(((ContextInternal) vertx.getOrCreateContext())::duplicate, false);
  }

  @Test
  public void testDuplicateWorkerExecuteBlockingUnordered() {
    testDuplicateExecuteBlocking(createWorkerContext()::duplicate, false);
  }

  private void testDuplicateExecuteBlocking(Supplier<ContextInternal> supplier, boolean ordered) {
    int n = 2;
    List<ContextInternal> dup1 = Stream.generate(supplier).limit(n).collect(Collectors.toList());
    AtomicInteger cnt = new AtomicInteger();
    List<io.vertx.core.Future<?>> futures = dup1.stream().map(c -> c.<Void>executeBlocking(() -> {
      assertTrue(Context.isOnWorkerThread());
      int val = cnt.incrementAndGet();
      if (ordered) {
        assertEquals(1, val);
      } else {
        assertWaitUntil(() -> cnt.get() == 2, 2000);
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        fail(e);
      } finally {
        cnt.decrementAndGet();
      }
      return null;
    }, ordered)).collect(Collectors.toList());
    io.vertx.core.Future.all(futures).onComplete(onSuccess(v -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testReentrantDispatch() {
    ClassLoader prev = Thread.currentThread().getContextClassLoader();
    try {
      ClassLoader cl = new URLClassLoader(new URL[0]);
      Thread.currentThread().setContextClassLoader(cl);
      ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
      ctx.runOnContext(v1 -> {
        assertSame(ctx, Vertx.currentContext());
        assertSame(cl, Thread.currentThread().getContextClassLoader());
        int[] called = new int[1];
        VertxThread thread = (VertxThread) Thread.currentThread();
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
    } finally {
      Thread.currentThread().setContextClassLoader(prev);
    }
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
    testEventLoopContextPromiseCompletedByWorkerThread(() -> "the-value");
  }

  @Test
  public void testEventLoopContextPromiseFailedByWorkerThread() {
    testEventLoopContextPromiseCompletedByWorkerThread(() -> {
      throw new Exception();
    });
  }

  private void testEventLoopContextPromiseCompletedByWorkerThread(Callable<String> action) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = context.promise();
    context.runOnContext(v -> {
      Thread th = Thread.currentThread();
      promise.future().onComplete(ar -> {
        assertSame(th, Thread.currentThread());
        testComplete();
      });
      context.executeBlocking(() -> {
        String res;
        try {
          res = action.call();
        } catch (Exception e) {
          promise.fail(e);
          return null;
        }
        promise.tryComplete(res);
        return null;
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
    io.vertx.core.Future<String> future = promise.future().compose(res -> {
      assertEquals(context, Vertx.currentContext());
      return io.vertx.core.Future.succeededFuture("value-2");
    });
    promise.complete("value-1");
    future.onComplete(ar -> {
      assertSame(context, Vertx.currentContext());
      testComplete();
    });
    await();
  }

  @Test
  public void testComposeContextPropagation2() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = context.promise();
    io.vertx.core.Future<String> future = promise.future().compose(res -> {
      assertSame(context, Vertx.currentContext());
      return io.vertx.core.Future.succeededFuture("value-2");
    });
    future.onComplete(ar -> {
      assertSame(context, Vertx.currentContext());
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
    io.vertx.core.Future<String> future = promise.future().compose(res -> anotherPromise.future());
    promise.complete("value-1");
    future.onComplete(ar -> {
      assertSame(context, Vertx.currentContext());
      testComplete();
    });
    anotherPromise.complete("value-2");
    await();
  }

  @Test
  public void testSucceededFutureContextPropagation1() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    io.vertx.core.Future<String> future = context.succeededFuture();
    future.onComplete(ar -> {
      assertSame(context, Vertx.currentContext());
      testComplete();
    });
    await();
  }

  @Test
  public void testSucceededFutureContextPropagation2() throws Exception {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    io.vertx.core.Future<String> future = context.succeededFuture();
    future = future.compose(value -> {
      assertSame(context, Vertx.currentContext());
      return io.vertx.core.Future.succeededFuture("value-2");
    });
    Thread.sleep(100);
    future.onComplete(ar -> {
      assertSame(context, Vertx.currentContext());
      testComplete();
    });
    await();
  }

  @Test
  public void testFailedFutureContextPropagation1() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    io.vertx.core.Future<String> future = context.failedFuture("error");
    future.onComplete(ar -> {
      assertSame(context, Vertx.currentContext());
      testComplete();
    });
    await();
  }

  @Test
  public void testFailedFutureContextPropagation2() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    io.vertx.core.Future<String> future = context.failedFuture("error");
    future = future.recover(err -> {
      assertSame(context, Vertx.currentContext());
      return io.vertx.core.Future.succeededFuture("value-2");
    });
    future.onComplete(ar -> {
      assertSame(context, Vertx.currentContext());
      testComplete();
    });
    await();
  }

  @Test
  public void testSticky() {
    Context ctx = vertx.getOrCreateContext();
    assertSame(ctx, vertx.getOrCreateContext());
    assertSame(((ContextInternal)ctx).nettyEventLoop(), ((ContextInternal)vertx.getOrCreateContext()).nettyEventLoop());
  }

  @Test
  public void testUnwrapPromiseWithoutContext() {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    List<Function<Promise<Object>, PromiseInternal<Object>>> suppliers = new ArrayList<>();
    suppliers.add(ctx::promise);
    suppliers.add(((VertxInternal)vertx)::promise);
    for (Function<Promise<Object>, PromiseInternal<Object>> supplier : suppliers) {
      Promise<Object> p1 = Promise.promise();
      PromiseInternal<Object> p2 = supplier.apply(p1);
      assertNotSame(p1, p2);
      // assertSame(ctx, p2.context());
      Object result = new Object();
      p2.complete(result);
      assertWaitUntil(() -> p1.future().isComplete());
      assertSame(result, p1.future().result());
    }
  }

  @Test
  public void testTopLevelContextClassLoader() {
    ClassLoader cl = new URLClassLoader(new URL[0]);
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    EventLoop el = ctx.nettyEventLoop();
    el.execute(() -> {
      Thread.currentThread().setContextClassLoader(cl);
      ctx.runOnContext(v -> {
        el.execute(() -> {
          assertSame(cl, Thread.currentThread().getContextClassLoader());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testIsDuplicatedContext() {
    Context context = vertx.getOrCreateContext();
    assertFalse(((ContextInternal) context).isDuplicate());
    context.runOnContext(x -> {
      assertFalse(((ContextInternal) Vertx.currentContext()).isDuplicate());

      ContextInternal duplicate = ((ContextInternal) Vertx.currentContext()).duplicate();
      assertTrue(duplicate.isDuplicate());
      assertSame(duplicate.unwrap(), context);
      duplicate.runOnContext(z -> {
        assertTrue(((ContextInternal) Vertx.currentContext()).isDuplicate());
        assertSame(Vertx.currentContext(), duplicate);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testIsDuplicatedContextFromWorkerContext() {
    ContextInternal context = createWorkerContext();
    assertFalse(context.isDuplicate());
    context.runOnContext(x -> {
      assertFalse(((ContextInternal) Vertx.currentContext()).isDuplicate());

      ContextInternal duplicate = ((ContextInternal) Vertx.currentContext()).duplicate();
      assertSame(duplicate.unwrap(), context);
      assertTrue(duplicate.isDuplicate());
      duplicate.runOnContext(z -> {
        assertTrue(((ContextInternal) Vertx.currentContext()).isDuplicate());
        assertSame(Vertx.currentContext(), duplicate);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDispatchContextOnAnyThread() {
    ClassLoader tccl1 = new URLClassLoader(new URL[0]);
    ClassLoader tccl2 = new URLClassLoader(new URL[0]);
    VertxImpl impl = (VertxImpl) vertx;
    ContextInternal ctx1 = impl.createEventLoopContext(impl.getEventLoopGroup().next(), null, tccl1);
    ContextInternal ctx2 = impl.createEventLoopContext(impl.getEventLoopGroup().next(), null, tccl2);
    AtomicInteger exec = new AtomicInteger();
    Thread thread = Thread.currentThread();
    ClassLoader current = thread.getContextClassLoader();
    ctx1.dispatch(() -> {
      assertSame(thread, Thread.currentThread());
      assertSame(ctx1, Vertx.currentContext());
      assertSame(tccl1, thread.getContextClassLoader());
      assertEquals(1, exec.incrementAndGet());
      ctx2.dispatch(() -> {
        assertSame(thread, Thread.currentThread());
        assertSame(ctx2, Vertx.currentContext());
        assertSame(tccl2, thread.getContextClassLoader());
        assertEquals(2, exec.incrementAndGet());
      });
      assertSame(ctx1, Vertx.currentContext());
      assertSame(tccl1, thread.getContextClassLoader());
      assertEquals(2, exec.get());
    });
    assertNull(Vertx.currentContext());
    assertSame(current, thread.getContextClassLoader());
    assertEquals(2, exec.get());
  }

  @Test
  public void testAwaitFromEventLoopThread() {
    testAwaitFromContextThread(ThreadingModel.EVENT_LOOP, true);
  }

  @Test
  public void testAwaitFromWorkerThread() {
    testAwaitFromContextThread(ThreadingModel.WORKER, true);
  }

  @Test
  public void testAwaitFromVirtualThreadThread() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    testAwaitFromContextThread(ThreadingModel.VIRTUAL_THREAD, false);
  }

  private void testAwaitFromContextThread(ThreadingModel threadMode, boolean fail) {
    vertx.deployVerticle(() -> new AbstractVerticle() {
      @Override
      public void start() {
        Promise<String> promise = Promise.promise();
        vertx.setTimer(10, id -> promise.complete("foo"));
        try {
          String res = promise.future().await();
          assertFalse(fail);
          assertEquals("foo", res);
        } catch (IllegalStateException e) {
          assertTrue(fail);
        }
      }
    }, new DeploymentOptions().setThreadingModel(threadMode)).onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testInterruptThreadOnAwait() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    vertx.deployVerticle(() -> new AbstractVerticle() {
      @Override
      public void start() {
        Thread current = Thread.currentThread();
        Promise<String> promise = Promise.promise();
        new Thread(() -> {
          while (current.getState() != Thread.State.WAITING) {
            try {
              Thread.sleep(10);
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
          current.interrupt();
        }).start();
        try {
          Future.await(promise.future());
          fail();
        } catch (Exception expected) {
          assertFalse(current.isInterrupted());
          assertEquals(expected.getClass(), InterruptedException.class);
          testComplete();
        }
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD));
    await();
  }

  @Test
  public void testConcurrentLocalAccess() throws Exception {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    int numThreads = 10;
    Thread[] threads = new Thread[numThreads];
    int[] values = new int[numThreads];
    CyclicBarrier barrier = new CyclicBarrier(numThreads);
    for (int i = 0;i < numThreads;i++) {
      values[i] = -1;
      int val = i;
      Supplier<Object> supplier = () -> val;
      threads[i] = new Thread(() -> {
        try {
          barrier.await();
        } catch (Exception e) {
          return;
        }
        values[val] = (int)ctx.getLocal(contextLocal, AccessMode.CONCURRENT, supplier);
      });
    }
    for (int i = 0;i < numThreads;i++) {
      threads[i].start();
    }
    for (int i = 0;i < numThreads;i++) {
      threads[i].join();
    }
    assertTrue(values[0] >= 0);
    for (int i = 0;i < numThreads;i++) {
      assertEquals(values[i], values[0]);
    }
  }

  @Test
  public void testContextShouldNotBeStickyFromUnassociatedEventLoopThread() {
    ContextInternal ctx = ((VertxInternal)vertx).createEventLoopContext();
    testContextShouldNotBeStickyFromUnassociatedWorkerThread(ctx);
  }

  @Test
  public void testContextShouldNotBeStickyFromUnassociatedWorkerThreadAndIsCurrentlyNotSupported() {
    ContextInternal ctx = ((VertxInternal)vertx).createWorkerContext();
    testContextShouldNotBeStickyFromUnassociatedWorkerThread(ctx);
  }

  private void testContextShouldNotBeStickyFromUnassociatedWorkerThread(ContextInternal ctx) {
    ctx.execute(() -> {
      assertEquals(null, Vertx.currentContext());
      ContextInternal created1 = (ContextInternal) vertx.getOrCreateContext();
      assertNotSame(ctx, created1);
      assertNotNull(created1.nettyEventLoop());
      ctx.execute(() -> {
        assertEquals(null, Vertx.currentContext());
        Context created2 = vertx.getOrCreateContext();
        assertSame(ctx.threadingModel(), created2.threadingModel());
        assertNotSame(ctx, created2);
        assertNotSame(created1, created2);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testInterruptActiveWorkerTask() throws Exception {
    ContextInternal ctx = ((VertxInternal)vertx).createWorkerContext();
    testInterruptTask(ctx, task -> {
      ctx.runOnContext(v -> {
        task.run();
      });
    });
  }

  @Test
  public void testInterruptExecuteBlockingTask() throws Exception {
    ContextInternal ctx = ((VertxInternal)vertx).createWorkerContext();
    testInterruptTask(ctx, task -> {
      ctx.executeBlocking(() -> {
        task.run();
        return null;
      });
    });
  }

  @Test
  public void testInterruptSuspendedVirtualThreadTask() throws Exception {
    Assume.assumeTrue(isVirtualThreadAvailable());
    ContextInternal ctx = ((VertxInternal)vertx).createVirtualThreadContext();
    testInterruptTask(ctx, (task) -> {
      ctx.runOnContext(v -> {
        task.run();
      });
    });
  }

  public void testInterruptTask(ContextInternal context, Consumer<Runnable> actor) throws Exception {
    CountDownLatch blockingLatch = new CountDownLatch(1);
    CountDownLatch closeLatch = new CountDownLatch(1);
    AtomicBoolean interrupted = new AtomicBoolean();
    actor.accept(() -> {
      try {
        closeLatch.countDown();
        blockingLatch.await();
      } catch (InterruptedException e) {
        interrupted.set(true);
      }
    });
    awaitLatch(closeLatch);
    Future<Void> fut = context.close();
    long now = System.currentTimeMillis();
    fut.await(20, TimeUnit.SECONDS);
    assertTrue((System.currentTimeMillis() - now) < 2000);
    assertTrue(interrupted.get());
  }
}
