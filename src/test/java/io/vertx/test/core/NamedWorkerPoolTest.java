/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.impl.VertxThread;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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

  @Rule
  public BlockedThreadWarning blockedThreadWarning = new BlockedThreadWarning();

  @Test
  public void testMaxExecuteWorkerTime() throws Exception {
    String poolName = TestUtils.randomAlphaString(10);
    long maxWorkerExecuteTime = NANOSECONDS.convert(3, SECONDS);
    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setWorkerPoolName(poolName)
      .setMaxWorkerExecuteTime(maxWorkerExecuteTime);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Future<Void> startFuture) throws Exception {
        vertx.executeBlocking(fut -> {
          try {
            SECONDS.sleep(5);
            fut.complete();
          } catch (InterruptedException e) {
            fut.fail(e);
          }
        }, startFuture);
      }
    }, deploymentOptions, onSuccess(did -> {
      testComplete();
    }));
    await();
    blockedThreadWarning.expectMessage(poolName, maxWorkerExecuteTime, NANOSECONDS);
  }

  @Test
  public void testThread() {
    String poolName = TestUtils.randomAlphaString(10);
    WorkerExecutor worker = vertx.createSharedWorkerExecutor(poolName);
    AtomicBoolean onVertxThread = new AtomicBoolean();
    AtomicBoolean onWorkerThread = new AtomicBoolean();
    AtomicBoolean onEventLoopThread = new AtomicBoolean();
    AtomicReference<String> threadName = new AtomicReference<>();
    worker.executeBlocking(fut -> {
      onVertxThread.set(Context.isOnVertxThread());
      onWorkerThread.set(Context.isOnWorkerThread());
      onEventLoopThread.set(Context.isOnEventLoopThread());
      threadName.set(Thread.currentThread().getName());
      fut.complete(null);
    }, ar -> {
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
        worker.executeBlocking(fut -> {
          if (first) {
            try {
              awaitLatch(submitted);
            } catch (InterruptedException e) {
              fail(e);
              return;
            }
            assertNull(t.get());
            t.set(Thread.currentThread());
          } else {
            assertEquals(t.get(), Thread.currentThread());
          }
          assertTrue(Thread.currentThread().getName().startsWith(poolName + "-"));
          fut.complete(null);
        }, ar -> {
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
        worker.executeBlocking(fut -> {
          latch1.countDown();
          try {
            awaitLatch(latch2);
          } catch (InterruptedException e) {
            fail(e);
            return;
          }
          assertTrue(Thread.currentThread().getName().startsWith(poolName + "-"));
          fut.complete(null);
        }, false, ar -> {
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
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        WorkerExecutor exec = vertx.createSharedWorkerExecutor("vert.x-the-executor");
        Thread startThread = Thread.currentThread();
        AtomicReference<Thread> currentThread = new AtomicReference<>();
        for (int i = 0;i < count;i++) {
          int val = i;
          exec.executeBlocking(fut -> {
            Thread current = Thread.currentThread();
            assertNotSame(startThread, current);
            if (val == 0) {
              assertNull(currentThread.getAndSet(current));
            } else {
              assertSame(current, currentThread.get());
            }
            fut.complete();
          }, true, onSuccess(v -> complete()));
        }
      }
    }, new DeploymentOptions().setWorker(true), onSuccess(id -> {}));
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
      worker.executeBlocking(fut -> {
        names.add(Thread.currentThread().getName());
        latch1.countDown();
      }, false, ar -> {
      });
    }
    awaitLatch(latch1);
    assertEquals(5, names.size());
  }

  @Test
  public void testMaxExecuteTime() {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    int poolSize = 5;
    long maxExecuteTime = 60;
    TimeUnit maxExecuteTimeUnit = TimeUnit.SECONDS;
    WorkerExecutor worker = vertx.createSharedWorkerExecutor(poolName, poolSize, maxExecuteTime, maxExecuteTimeUnit);
    worker.executeBlocking(f -> {
      Thread t = Thread.currentThread();
      assertTrue(t instanceof VertxThread);
      VertxThread thread = (VertxThread) t;
      assertEquals(maxExecuteTime, thread.getMaxExecTime());
      assertEquals(maxExecuteTimeUnit, thread.getMaxExecTimeUnit());
      f.complete();
    }, res -> {
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
    worker1.executeBlocking(fut -> {
      thread.set(Thread.currentThread());
    }, ar -> {
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
    }, onSuccess(deploymentIdRef::complete));
    String deploymentId = deploymentIdRef.get(20, SECONDS);
    vertx.undeploy(deploymentId, onSuccess(v -> {
      try {
        pool.get().<String>executeBlocking(fut -> fail(), null);
        fail();
      } catch (IllegalStateException ignore) {
        testComplete();
      }
    }));
    await();
  }

  @Test
  public void testDeployUsingNamedPool() throws Exception {
    AtomicReference<Thread> thread = new AtomicReference<>();
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        vertx.executeBlocking(fut -> {
          thread.set(Thread.currentThread());
          assertTrue(Context.isOnVertxThread());
          assertTrue(Context.isOnWorkerThread());
          assertFalse(Context.isOnEventLoopThread());
          assertTrue(Thread.currentThread().getName().startsWith(poolName + "-"));
          fut.complete();
        }, onSuccess(v -> {
          vertx.undeploy(context.deploymentID());
        }));
      }
    }, new DeploymentOptions().setWorkerPoolName(poolName), onSuccess(v -> {}));
    assertWaitUntil(() -> thread.get() != null && thread.get().getState() == Thread.State.TERMINATED);
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
    }, new DeploymentOptions().setWorker(true).setWorkerPoolName(poolName), onSuccess(deployment::set));
    assertWaitUntil(() -> thread.get() != null && thread.get().getState() == Thread.State.TERMINATED);
  }

  @Test
  public void testCloseWorkerPoolsWhenVertxCloses() {
    Vertx vertx = Vertx.vertx();
    WorkerExecutor exec = vertx.createSharedWorkerExecutor("vert.x-123");
    vertx.close(v -> {
      try {
        vertx.executeBlocking(fut -> fail(), ar -> fail());
        fail();
      } catch (RejectedExecutionException ignore) {
      }
      try {
        exec.executeBlocking(fut -> fail(), ar -> fail());
        fail();
      } catch (RejectedExecutionException ignore) {
      }
      // Check we can still close
      exec.close();
      testComplete();
    });
    await();
  }
}
