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

package io.vertx.tests.timer;

import io.vertx.core.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.core.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TimerTest extends VertxTestBase {

  public TimerTest() {
    super(true);
  }

  @Test
  public void testTimer() {
    timer(1);
  }

  @Test
  public void testPeriodic1() {
    periodic(new PeriodicArg(100, 100), (delay, handler) -> vertx.setPeriodic(delay.delay, handler));
  }

  @Test
  public void testPeriodic2() {
    periodic(new PeriodicArg(100, 100), (delay, handler) -> vertx.setPeriodic(delay.delay, delay.delay, handler));
  }

  @Test
  public void testPeriodicWithInitialDelay1() {
    periodic(new PeriodicArg(0, 100), (delay, handler) -> vertx.setPeriodic(delay.initialDelay, delay.delay, handler));
  }

  @Test
  public void testPeriodicWithInitialDelay2() {
    periodic(new PeriodicArg(100, 200), (delay, handler) -> vertx.setPeriodic(delay.initialDelay, delay.delay, handler));
  }

  /**
   * Test the timers fire with approximately the correct delay
   */
  @Test
  public void testTimings() {
    final long start = System.nanoTime();
    final long delay = 2000;
    vertx.setTimer(delay, timerID -> {
      long dur = System.nanoTime() - start;
      Assert.assertTrue(dur >= TimeUnit.MILLISECONDS.toNanos(delay));
      long maxDelay = delay * 2;
      Assert.assertTrue("Timer accuracy: " + dur + " vs " + TimeUnit.MILLISECONDS.toNanos(maxDelay), dur < TimeUnit.MILLISECONDS.toNanos(maxDelay)); // 100% margin of error (needed for CI)
      vertx.cancelTimer(timerID);
      testComplete();
    });
    await();
  }

  @Test
  public void testInVerticle() {
    class MyVerticle extends AbstractVerticle {
      AtomicInteger cnt = new AtomicInteger();
      @Override
      public void start() {
        Thread thr = Thread.currentThread();
        vertx.setTimer(1, id -> {
          Assert.assertSame(thr, Thread.currentThread());
          if (cnt.incrementAndGet() == 5) {
            testComplete();
          }
        });
        vertx.setPeriodic(2, id -> {
          Assert.assertSame(thr, Thread.currentThread());
          if (cnt.incrementAndGet() == 5) {
            testComplete();
          }
        });
        vertx.setPeriodic(3, 4, id -> {
          Assert.assertSame(thr, Thread.currentThread());
          if (cnt.incrementAndGet() == 5) {
            testComplete();
          }
        });
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle);
    await();
  }

  static class PeriodicArg {
    final long initialDelay;
    final long delay;
    PeriodicArg(long initialDelay, long delay) {
      this.initialDelay = initialDelay;
      this.delay = delay;
    }
  }

  private void periodic(PeriodicArg delay, BiFunction<PeriodicArg, Handler<Long>, Long> abc) {
    final int numFires = 10;
    final AtomicLong id = new AtomicLong(-1);
    long now = System.nanoTime();
    id.set(abc.apply(delay, new Handler<Long>() {
      int count;
      public void handle(Long timerID) {
        assertThatComparable(System.nanoTime() - now, a -> a.isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(delay.initialDelay + count * delay.delay)));
        Assert.assertEquals(id.get(), timerID.longValue());
        count++;
        if (count == numFires) {
          vertx.cancelTimer(timerID);
          setEndTimer();
        }
        if (count > numFires) {
          Assert.fail("Fired too many times");
        }
      }
    }));
    await();
  }

  private void timer(long delay) {
    final AtomicLong id = new AtomicLong(-1);
    id.set(vertx.setTimer(delay, new Handler<Long>() {
      int count;
      boolean fired;
      public void handle(Long timerID) {
        Assert.assertFalse(fired);
        fired = true;
        Assert.assertEquals(id.get(), timerID.longValue());
        Assert.assertEquals(0, count);
        count++;
        setEndTimer();
      }
    }));
    await();
  }

  private void setEndTimer() {
    // Set another timer to trigger test complete - this is so if the first timer is called more than once we will
    // catch it
    vertx.setTimer(10, id -> testComplete());
  }

  @Test
  public void testCancelTimerWhenScheduledOnWorker() {
    AtomicBoolean executed = new AtomicBoolean();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        vertx.runOnContext(v -> {
          long id = vertx.setTimer(100, id_ -> {
            executed.set(true);
          });
          try {
            Thread.sleep(200);
          } catch (InterruptedException ignore) {
          }
          Assert.assertTrue(vertx.cancelTimer(id));
          testComplete();
        });
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    await();
    Assert.assertFalse(waitUntil(() -> executed.get(), 25));
  }

  @Test
  public void testWorkerTimer() {
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        vertx.setTimer(10, id -> {
          Assert.assertTrue(Context.isOnWorkerThread());
          testComplete();
        });
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    await();
  }

  @Test
  public void testFailInTimer() {
    RuntimeException failure = new RuntimeException();
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      ctx.exceptionHandler(err -> {
        Assert.assertSame(err, failure);
        testComplete();
      });
      vertx.setTimer(5, id -> {
        throw failure;
      });
    });
    await();
  }

  @Test
  public void testCancellationRace() throws Exception {
    for (int i = 0;i < 200;i++) {
      AtomicBoolean fired = new AtomicBoolean();
      long timerId = vertx.setTimer(5, id -> {
        fired.set(true);
      });
      Thread.sleep(5);
      boolean res = vertx.cancelTimer(timerId);
      if (res && fired.get()) {
        throw new AssertionError("It failed " + i);
      }
    }
  }

  @Test
  public void testUndeployCancelTimer() {
    testUndeployCancellation(() -> vertx.setTimer(1000, id -> {}));
  }

  @Test
  public void testUndeployCancelPeriodic() {
    testUndeployCancellation(() -> vertx.setPeriodic(1000, id -> {}));
  }

  private void testUndeployCancellation(Supplier<Long> f) {
    AtomicLong timer = new AtomicLong();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        timer.set(f.get());
      }
    }).compose(deployment -> vertx.undeploy(deployment)).onComplete(TestUtils.onSuccess(v -> {
      Assert.assertFalse(vertx.cancelTimer(timer.get()));
      testComplete();
    }));
    await();
  }

  @Test
  public void testTimerOnContext() {
    disableThreadChecks();
    ContextInternal ctx1 = ((VertxInternal)vertx).createEventLoopContext();
    waitFor(2);
    ContextInternal ctx2 = ((VertxInternal)vertx).createEventLoopContext();
    Assert.assertNotSame(ctx1, ctx2);
    ctx2.runOnContext(v -> {
      vertx.setTimer(10, l -> {
        Assert.assertSame(ctx2, vertx.getOrCreateContext());
        complete();
      });
      ctx1.setTimer(10, l -> {
        Assert.assertSame(ctx1, vertx.getOrCreateContext());
        complete();
      });
    });
    await();
  }

  @Test
  public void testPeriodicOnContext() {
    testPeriodicOnContext(((VertxInternal)vertx).createEventLoopContext());
  }

  @Test
  public void testPeriodicOnDuplicatedContext() {
    testPeriodicOnContext(((VertxInternal)vertx).createEventLoopContext().duplicate());
  }

  private void testPeriodicOnContext(ContextInternal ctx2) {
    disableThreadChecks();
    waitFor(4);
    ContextInternal ctx1 = ((VertxInternal)vertx).createEventLoopContext();
    Assert.assertNotSame(ctx1, ctx2);
    ctx2.runOnContext(v -> {
      Thread th = Thread.currentThread();
      vertx.setPeriodic(10, new Handler<>() {
        int count;

        @Override
        public void handle(Long l) {
          Assert.assertSame(th, Thread.currentThread());
          ContextInternal current = (ContextInternal) vertx.getOrCreateContext();
          Assert.assertNotNull(current);
          Assert.assertTrue(current.isDuplicate());
          Assert.assertNotSame(ctx2, current);
          Assert.assertSame(ctx2.unwrap(), current.unwrap());
          if (++count == 2) {
            vertx.cancelTimer(l);
          }
          complete();
        }
      });
      ctx1.setPeriodic(10, new Handler<>() {
        int count;

        @Override
        public void handle(Long l) {
          ContextInternal current = (ContextInternal) vertx.getOrCreateContext();
          Assert.assertNotNull(current);
          Assert.assertTrue(current.isDuplicate());
          Assert.assertNotSame(ctx1, current);
          Assert.assertSame(ctx1, current.unwrap());
          if (++count == 2) {
            vertx.cancelTimer(l);
          }
          complete();
        }
      });
    });
    await();
  }

  @Repeat(times = 100)
  @Test
  public void testRaceWhenTimerCreatedOutsideEventLoop() {
    int numThreads = 1000;
    int numIter = 1;
    Thread[] threads = new Thread[numThreads];
    AtomicInteger count = new AtomicInteger(numIter * numThreads);
    for (int i = 0;i < numThreads;i++) {
      Thread th = new Thread(() -> {
        // We need something more aggressive than a millisecond for this test
        ((VertxImpl)vertx).scheduleTimeout(((VertxImpl) vertx).getOrCreateContext(), false, 1, TimeUnit.NANOSECONDS, false, ignore -> {
          count.decrementAndGet();
        });
      });
      th.start();
      threads[i] = th;
    }
    waitUntil(() -> count.get() == 0);
  }

  @Test
  public void testContextTimer() {
    waitFor(2);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        ((ContextInternal)context).setTimer(1000, id -> {
          complete();
        });
        context.runOnContext(v -> {
          vertx.undeploy(context.deploymentID()).onComplete(TestUtils.onSuccess(ar -> {
            ((ContextInternal)context).setTimer(1, id -> {
              complete();
            });
          }));
        });
      }
    });
    await();
  }

  @Test
  public void testTimerFire() {
    long now = System.nanoTime();
    Timer timer = vertx.timer(1, TimeUnit.SECONDS);
    timer.onComplete(TestUtils.onSuccess(v -> {
      Assert.assertTrue(System.nanoTime() - now >= TimeUnit.SECONDS.toNanos(1));
      testComplete();
    }));
    await();
  }

  @Test
  public void testTimerFireOnContext1() {
    AtomicReference<ContextInternal> ref1 = new AtomicReference<>();
    AtomicReference<ContextInternal> ref2 = new AtomicReference<>();
    new Thread(() -> {
      ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
      ref2.set(ctx);
      Timer timer = vertx.timer(10, TimeUnit.MILLISECONDS);
      timer.onComplete(TestUtils.onSuccess(v -> {
        ref1.set((ContextInternal)Vertx.currentContext());
      }));
    }).start();
    waitUntil(() -> ref1.get() != null);
    Assert.assertSame(ref2.get().nettyEventLoop(), ref1.get().nettyEventLoop());
  }

  @Test
  public void testTimerFireOnContext2() {
    vertx.runOnContext(v1 -> {
      Context current = vertx.getOrCreateContext();
      ContextInternal context = ((VertxInternal) vertx).createEventLoopContext();
      Assert.assertNotSame(context, current);
      Timer timer = context.timer(10, TimeUnit.MILLISECONDS);
      timer.onComplete(TestUtils.onSuccess(v2 -> {
        Assert.assertSame(context, Vertx.currentContext());
        testComplete();
      }));
    });
    await();
  }

  @Test
  public void testFailTimerTaskWhenCancellingTimer() {
    Timer timer = vertx.timer(10_000);
    Assert.assertTrue(timer.cancel());
    waitUntil(timer::failed);
    Assert.assertTrue(timer.cause() instanceof CancellationException);
  }

  @Test
  public void testFailTimerTaskWhenClosingVertx() throws Exception {
    Vertx vertx = Vertx.vertx();
    Timer timer = vertx.timer(10_000);
    vertx.close().await();
    waitUntil(timer::failed);
    Assert.assertTrue(timer.cause() instanceof CancellationException);
  }
}
