/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.WorkerExecutor;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NamedWorkerPoolTest extends VertxTestBase {

  @Test
  public void testThread() {
    String poolName = TestUtils.randomAlphaString(10);
    WorkerExecutor worker = vertx.createWorkerExecutor(poolName);
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
    waitUntil(() -> threadName.get() != null);
    assertTrue(onVertxThread.get());
    assertTrue(onWorkerThread.get());
    assertFalse(onEventLoopThread.get());
    assertTrue(threadName.get().startsWith(poolName + "-"));
  }

  @Test
  public void testOrdered() {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    WorkerExecutor worker = vertx.createWorkerExecutor(poolName);
    int num = 1000;
    AtomicReference<Thread> t = new AtomicReference<>();
    CountDownLatch submitted = new CountDownLatch(1);
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
    await();
  }

  @Test
  public void testUnordered() throws Exception {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    int num = 5;
    waitFor(num);
    WorkerExecutor worker = vertx.createWorkerExecutor(poolName);
    CountDownLatch latch1 = new CountDownLatch(num);
    CountDownLatch latch2 = new CountDownLatch(1);
    for (int i = 0;i < num;i++) {
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
    awaitLatch(latch1);
    latch2.countDown();
    await();
  }

  @Test
  public void testPoolSize() throws Exception {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    int poolSize = 5;
    waitFor(poolSize);
    WorkerExecutor worker = vertx.createWorkerExecutor(poolName, poolSize);
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
  public void testCloseWorkerPool() throws Exception {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    AtomicReference<Thread> thread = new AtomicReference<>();
    WorkerExecutor worker1 = vertx.createWorkerExecutor(poolName);
    WorkerExecutor worker2 = vertx.createWorkerExecutor(poolName);
    worker1.executeBlocking(fut -> {
      thread.set(Thread.currentThread());
    }, ar -> {
    });
    waitUntil(() -> thread.get() != null);
    worker1.close();
    assertNotSame(thread.get().getState(), Thread.State.TERMINATED);
    worker2.close();
    waitUntil(() -> thread.get().getState() == Thread.State.TERMINATED);
  }

  @Test
  public void testDestroyWorkerPoolWhenVerticleUndeploys() throws Exception {
    String poolName = "vert.x-" + TestUtils.randomAlphaString(10);
    AtomicReference<Thread> thread = new AtomicReference<>();
    CompletableFuture<String> deploymentIdRef = new CompletableFuture<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        WorkerExecutor pool = vertx.createWorkerExecutor(poolName);
        pool.executeBlocking(fut -> {
          thread.set(Thread.currentThread());
        }, ar -> {
        });
      }
    }, onSuccess(deploymentIdRef::complete));
    waitUntil(() -> thread.get() != null);
    String deploymentId = deploymentIdRef.get(20, TimeUnit.SECONDS);
    vertx.undeploy(deploymentId, onSuccess(v -> {}));
    waitUntil(() -> thread.get().getState() == Thread.State.TERMINATED);
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
    waitUntil(() -> thread.get() != null && thread.get().getState() == Thread.State.TERMINATED);
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
    waitUntil(() -> thread.get() != null && thread.get().getState() == Thread.State.TERMINATED);
  }
}
