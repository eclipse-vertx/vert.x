/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.worker;

import io.vertx.core.*;
import io.vertx.core.impl.VertxThread;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NamedWorkerPoolTest extends VertxTestBase {

  @Test
  public void testThread() {
    String poolName = TestUtils.randomAlphaString(10);
    WorkerExecutor worker = vertx.createSharedWorkerExecutor(poolName);
    AtomicBoolean onVertxThread = new AtomicBoolean();
    AtomicBoolean onWorkerThread = new AtomicBoolean();
    AtomicBoolean onEventLoopThread = new AtomicBoolean();
    AtomicReference<String> threadName = new AtomicReference<>();
    worker.executeBlocking(() -> {
      onVertxThread.set(Context.isOnVertxThread());
      onWorkerThread.set(Context.isOnWorkerThread());
      onEventLoopThread.set(Context.isOnEventLoopThread());
      threadName.set(Thread.currentThread().getName());
      return null;
    }).onComplete(ar -> {
      testComplete();
    });
    // Use regular assertions because the thread name does not start with "vert.x-"
    // and it confuses the VertxTestBase asserts
    assertWaitUntil(() -> threadName.get() != null);
    assertTrue(onVertxThread.get());
    assertTrue(onWorkerThread.get());
    assertFalse(onEventLoopThread.get());
    assertTrue(threadName.get().startsWith(poolName + "-"));
  }

  @Test
  public void testOrdered() {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    WorkerExecutor worker = vertx.createSharedWorkerExecutor(poolName);
    int num = 1000;
    AtomicReference<Thread> t = new AtomicReference<>();
    CountDownLatch submitted = new CountDownLatch(1);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      for (int i = 0;i < num;i++) {
        boolean first = i == 0;
        boolean last = i == num - 1;
        worker.executeBlocking(() -> {
          if (first) {
            try {
              awaitLatch(submitted);
            } catch (InterruptedException e) {
              fail(e);
              return null;
            }
            assertNull(t.get());
            t.set(Thread.currentThread());
          } else {
            assertEquals(t.get(), Thread.currentThread());
          }
          assertTrue(Thread.currentThread().getName().startsWith(poolName + "-"));
          return null;
        }).onComplete(ar -> {
          if (last) {
            testComplete();
          }
        });
      }
      submitted.countDown();
    });
    await();
  }

  @Test
  public void testUnordered() throws Exception {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    int num = 5;
    waitFor(num);
    WorkerExecutor worker = vertx.createSharedWorkerExecutor(poolName);
    CountDownLatch latch1 = new CountDownLatch(num);
    CountDownLatch latch2 = new CountDownLatch(1);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      for (int i = 0; i < num; i++) {
        worker.executeBlocking(() -> {
          latch1.countDown();
          try {
            awaitLatch(latch2);
          } catch (InterruptedException e) {
            fail(e);
            return null;
          }
          assertTrue(Thread.currentThread().getName().startsWith(poolName + "-"));
          return null;
        }, false).onComplete(ar -> {
          complete();
        });
      }
    });
    awaitLatch(latch1);
    latch2.countDown();
    await();
  }

  @Test
  public void testUseDifferentExecutorWithSameTaskQueue() throws Exception {
    int count = 10;
    waitFor(count);
    WorkerExecutor exec = vertx.createSharedWorkerExecutor("vert.x-the-executor");
    Thread startThread = Thread.currentThread();
    AtomicReference<Thread> currentThread = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    for (int i = 0;i < count;i++) {
      int val = i;
      exec.executeBlocking(() -> {
        Thread current = Thread.currentThread();
        assertNotSame(startThread, current);
        if (val == 0) {
          assertNull(currentThread.getAndSet(current));
          awaitLatch(latch);
        } else {
          assertSame(current, currentThread.get());
        }
        return null;
      }, true).onComplete(onSuccess(v -> complete()));
      latch.countDown();
    }
    await();
  }

  @Test
  public void testPoolSize() throws Exception {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    int poolSize = 5;
    waitFor(poolSize);
    WorkerExecutor worker = vertx.createSharedWorkerExecutor(poolName, poolSize);
    CountDownLatch latch1 = new CountDownLatch(poolSize * 100);
    Set<String> names = Collections.synchronizedSet(new HashSet<>());
    for (int i = 0;i < poolSize * 100;i++) {
      worker.executeBlocking(() -> {
        names.add(Thread.currentThread().getName());
        latch1.countDown();
        return null;
      }, false);
    }
    awaitLatch(latch1);
    assertEquals(5, names.size());
  }

  @Test
  public void testMaxExecuteTime1() {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    int poolSize = 5;
    long maxExecuteTime = 60;
    TimeUnit maxExecuteTimeUnit = TimeUnit.SECONDS;
    Vertx vertx = Vertx.vertx(new VertxOptions().setMaxWorkerExecuteTime(maxExecuteTime).setMaxWorkerExecuteTimeUnit(maxExecuteTimeUnit));
    try {
      testMaxExecuteTime(vertx.createSharedWorkerExecutor(poolName, poolSize), maxExecuteTime, maxExecuteTimeUnit);
    } finally {
      vertx.close();
    }
  }

  @Test
  public void testMaxExecuteTime2() {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    int poolSize = 5;
    long maxExecuteTime = 60;
    TimeUnit maxExecuteTimeUnit = TimeUnit.SECONDS;
    Vertx vertx = Vertx.vertx(new VertxOptions().setMaxWorkerExecuteTime(maxExecuteTime).setMaxWorkerExecuteTimeUnit(maxExecuteTimeUnit));
    try {
      testMaxExecuteTime(vertx.createSharedWorkerExecutor(poolName, poolSize, maxExecuteTime), maxExecuteTime, maxExecuteTimeUnit);
    } finally {
      vertx.close();
    }
  }

  @Test
  public void testMaxExecuteTime3() {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    int poolSize = 5;
    long maxExecuteTime = 60;
    TimeUnit maxExecuteTimeUnit = TimeUnit.SECONDS;
    testMaxExecuteTime(vertx.createSharedWorkerExecutor(poolName, poolSize, maxExecuteTime, maxExecuteTimeUnit), maxExecuteTime, maxExecuteTimeUnit);
  }

  public void testMaxExecuteTime(WorkerExecutor worker, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
    worker.executeBlocking(() -> {
      Thread t = Thread.currentThread();
      assertTrue(t instanceof VertxThread);
      VertxThread thread = (VertxThread) t;
      assertEquals(maxExecuteTime, thread.maxExecTime());
      assertEquals(maxExecuteTimeUnit, thread.maxExecTimeUnit());
      return null;
    }).onComplete(res -> {
      testComplete();
    });
    await();
  }

  @Test
  public void testCloseWorkerPool() throws Exception {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    AtomicReference<Thread> thread = new AtomicReference<>();
    WorkerExecutor worker1 = vertx.createSharedWorkerExecutor(poolName);
    WorkerExecutor worker2 = vertx.createSharedWorkerExecutor(poolName);
    worker1.executeBlocking(() -> {
      thread.set(Thread.currentThread());
      return null;
    });
    assertWaitUntil(() -> thread.get() != null);
    worker1.close();
    assertNotSame(thread.get().getState(), Thread.State.TERMINATED);
    worker2.close();
    assertWaitUntil(() -> thread.get().getState() == Thread.State.TERMINATED);
  }

  @Test
  public void testDestroyWorkerPoolWhenVerticleUndeploys() throws Exception {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    CompletableFuture<String> deploymentIdRef = new CompletableFuture<>();
    AtomicReference<WorkerExecutor> pool = new AtomicReference<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        pool.set(vertx.createSharedWorkerExecutor(poolName));
      }
    }).onComplete(onSuccess(deploymentIdRef::complete));
    String deploymentId = deploymentIdRef.get(20, SECONDS);
    vertx.undeploy(deploymentId).onComplete(onSuccess(v -> {
      try {
        pool.get().<String>executeBlocking(() -> {
          fail();
          return null;
        });
        fail();
      } catch (RejectedExecutionException ignore) {
        testComplete();
      }
    }));
    await();
  }

  @Test
  public void testDeployUsingNamedPool() {
    AtomicReference<Thread> thread = new AtomicReference<>();
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    Promise<Void> undeployed = Promise.promise();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        vertx.runOnContext(v1 -> {
          vertx.executeBlocking(() -> {
            thread.set(Thread.currentThread());
            assertTrue(Context.isOnVertxThread());
            assertTrue(Context.isOnWorkerThread());
            assertFalse(Context.isOnEventLoopThread());
            assertTrue(Thread.currentThread().getName().startsWith(poolName + "-"));
            return null;
          }).onComplete(onSuccess(v2 -> {
            vertx.undeploy(context.deploymentID()).onComplete(undeployed);
          }));
        });
      }
    }, new DeploymentOptions().setWorkerPoolName(poolName));
    assertWaitUntil(() -> thread.get() != null && thread.get().getState() == Thread.State.TERMINATED);
  }

  @Test
  public void testNamedWorkerPoolShouldBeClosedAfterVerticleIsUndeployed() {
    AtomicReference<String> threadName = new AtomicReference<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
      }
      @Override
      public void stop() {
        threadName.set(Thread.currentThread().getName());
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER).setWorkerPoolName("test-worker")).onComplete(onSuccess(id -> {
      vertx.undeploy(id).onComplete(onSuccess(v -> {
        assertNotNull(threadName.get());
        assertTrue(threadName.get().startsWith("test-worker"));
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testDeployUsingNamedWorkerDoesNotCreateExtraEventLoop() {
    int instances = getOptions().getEventLoopPoolSize();
    Set<Thread> threads = Collections.synchronizedSet(new HashSet<>());
    vertx.deployVerticle(() -> new AbstractVerticle() {
      @Override
      public void start() {
        threads.add(Thread.currentThread());
      }
    }, new DeploymentOptions().setInstances(instances).setWorkerPoolName("the-pool")).onComplete(onSuccess(id -> {
      vertx.undeploy(id).onComplete(onSuccess(v -> {
        assertEquals(instances, threads.size());
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testDeployWorkerUsingNamedPool() throws Exception {
    AtomicReference<Thread> thread = new AtomicReference<>();
    AtomicReference<String> deployment = new AtomicReference<>();
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        thread.set(Thread.currentThread());
        assertTrue(Context.isOnVertxThread());
        assertTrue(Context.isOnWorkerThread());
        assertFalse(Context.isOnEventLoopThread());
        assertTrue(Thread.currentThread().getName().startsWith(poolName + "-"));
        context.runOnContext(v -> {
          vertx.undeploy(context.deploymentID());
        });
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER).setWorkerPoolName(poolName)).onComplete(onSuccess(deployment::set));
    assertWaitUntil(() -> thread.get() != null && thread.get().getState() == Thread.State.TERMINATED);
  }

  @Test
  public void testCloseWorkerPoolsWhenVertxCloses() {
    disableThreadChecks();
    Vertx vertx = Vertx.vertx();
    WorkerExecutor exec = vertx.createSharedWorkerExecutor("vert.x-123");
    vertx.close().onComplete(v -> {
      try {
        vertx.executeBlocking(() -> {
          fail();
          return null;
        }).onComplete(ar -> fail());
        fail();
      } catch (RejectedExecutionException ignore) {
      }
      try {
        exec.executeBlocking(() -> {
          fail();
          return null;
        }).onComplete(ar -> fail());
        fail();
      } catch (RejectedExecutionException ignore) {
      }
      // Check we can still close
      exec.close();
      testComplete();
    });
    await();
  }

  @Test
  public void testReuseWorkerPoolNameAfterVerticleIsUndeployed() throws Exception {
    CountDownLatch deployLatch1 = new CountDownLatch(1);
    AtomicReference<String> ref = new AtomicReference<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) {
        vertx.<Void>executeBlocking(() -> null).onComplete(startPromise);
      }
    }, new DeploymentOptions().setWorkerPoolName("foo")).onComplete(onSuccess(id -> {
      ref.set(id);
      deployLatch1.countDown();
    }));
    awaitLatch(deployLatch1);

    CountDownLatch unDeployLatch = new CountDownLatch(1);
    vertx.undeploy(ref.get()).onComplete(onSuccess(v -> unDeployLatch.countDown()));
    awaitLatch(unDeployLatch);

    CountDownLatch deployLatch2 = new CountDownLatch(1);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) {
        vertx.<Void>executeBlocking(() -> null).onComplete(startPromise);
      }
    }, new DeploymentOptions().setWorkerPoolName("foo")).onComplete(onSuccess(id -> {
      deployLatch2.countDown();
    }));
    awaitLatch(deployLatch2);
  }
}
