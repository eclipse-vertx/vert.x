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
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.StringStartsWith.startsWith;

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
        vertx.executeBlocking(future -> {
          assertSame(ctx, vertx.getOrCreateContext());
          assertTrue(Thread.currentThread().getName().startsWith("vert.x-worker-thread"));
          assertTrue(Context.isOnWorkerThread());
          assertFalse(Context.isOnEventLoopThread());
          try {
            Thread.sleep(pause);
          } catch (Exception ignore) {
          }
          future.complete("done!");
        }, false, onSuccess(res -> {
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

  @Test
  public void testExecuteBlockingWithName() throws InterruptedException {
    vertx.close(); // Close the instance automatically created
    vertx = Vertx.vertx(
        new VertxOptions().setNamedThreadPoolConfiguration(
            new JsonObject().put("pools", new JsonArray()
                .add(new JsonObject().put("name", "my-pool").put("size", 10))
                .add(new JsonObject().put("name", "tiny-pool").put("size", 1)))));

    // Test execution in my-pool
    AtomicReference<String> reference = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    vertx.executeBlocking(
        future -> {
          reference.set(Thread.currentThread().getName());
          future.complete();
        },
        ar -> latch.countDown(),
        "my-pool"
    );
    awaitLatch(latch);
    assertThat(reference.get(), startsWith("my-pool-"));

    // Test execution in tiny-pool
    AtomicReference<String> reference2 = new AtomicReference<>();
    CountDownLatch latch2 = new CountDownLatch(1);
    vertx.executeBlocking(
        future -> {
          reference2.set(Thread.currentThread().getName());
          future.complete();
        },
        ar -> latch2.countDown(),
        "tiny-pool"
    );
    awaitLatch(latch2);
    assertThat(reference2.get(), startsWith("tiny-pool-"));

    // Test execution in an unknown pool
    AtomicReference<String> reference3 = new AtomicReference<>();
    CountDownLatch latch3 = new CountDownLatch(1);
    vertx.executeBlocking(
        future -> {
          reference3.set(Thread.currentThread().getName());
          future.complete();
        },
        ar -> latch3.countDown(),
        "unknown"
    );
    awaitLatch(latch3);
    assertThat(reference3.get(), startsWith("vert.x-worker-thread-"));

    // Test ordering
    List<Integer> items = new CopyOnWriteArrayList<>();
    Counter counter = new Counter();
    AtomicBoolean done = new AtomicBoolean(false);
    CountDownLatch latch4 = new CountDownLatch(1);
    vertx.runOnContext(v -> {
      for (int i = 0; i < 100; i++) {
        vertx.executeBlocking(
            future -> {
              grace();
              items.add(counter.getAndIncrement());
              future.complete();
            },
            ar -> {
              if (!done.get() && items.size() >= 100) {
                done.set(true);
                latch4.countDown();
              }
            },
            "my-pool"
        );
      }
    });
    awaitLatch(latch4);
    assertThat(isOrdered(items), is(true));

    // Test non ordering
    items.clear();
    counter.reset();
    done.set(false);
    CountDownLatch latch5 = new CountDownLatch(1);
    vertx.runOnContext(v -> {
      for (int i = 0; i < 100; i++) {
        vertx.executeBlocking(
            future -> {
              grace();
              items.add(counter.getAndIncrement());
              future.complete();
            },
            false,
            ar -> {
              if (!done.get() && items.size() >= 100) {
                done.set(true);
                latch5.countDown();
              }
            },
            "my-pool"
        );
      }
    });
    awaitLatch(latch5);
    assertThat(isOrdered(items), is(false));

  }

  private void grace() {
    try {
      long millis = (long) (Math.random() * 10);
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().isInterrupted();
    }
  }

  /**
   * A non thread safe counter (on purpose).
   */
  private class Counter {
    int count = 0;

    int getAndIncrement() {
      int c = count;
      count = count + 1;
      return c;
    }

    void reset() {
      count = 0;
    }
  }

  private boolean isOrdered(List<Integer> list) {
    int previous = -1;
    for (Integer current : list) {
      if (current <= previous) {
        return false;
      }
      previous = current;
    }
    return true;
  }
}
