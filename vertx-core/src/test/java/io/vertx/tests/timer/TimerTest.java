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
import io.vertx.test.core.*;
import io.vertx.tests.vertx.VertxTest;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TimerTest extends VertxTestBase2 {

  @Test
  public void testTimer(Checkpoint checkpoint) {
    timer(checkpoint, 1);
  }

  @Test
  public void testPeriodic1(Checkpoint checkpoint) {
    periodic(checkpoint, new PeriodicArg(100, 100), (delay, handler) -> vertx.setPeriodic(delay.delay, handler));
  }

  @Test
  public void testPeriodic2(Checkpoint checkpoint) {
    periodic(checkpoint, new PeriodicArg(100, 100), (delay, handler) -> vertx.setPeriodic(delay.delay, delay.delay, handler));
  }

  @Test
  public void testPeriodicWithInitialDelay1(Checkpoint checkpoint) {
    periodic(checkpoint, new PeriodicArg(0, 100), (delay, handler) -> vertx.setPeriodic(delay.initialDelay, delay.delay, handler));
  }

  @Test
  public void testPeriodicWithInitialDelay2(Checkpoint checkpoint) {
    periodic(checkpoint, new PeriodicArg(100, 200), (delay, handler) -> vertx.setPeriodic(delay.initialDelay, delay.delay, handler));
  }

  /**
   * Test the timers fire with approximately the correct delay
   */
  @Test
  public void testTimings(Checkpoint checkpoint) {
    final long start = System.nanoTime();
    final long delay = 2000;
    vertx.setTimer(delay, timerID -> {
      long dur = System.nanoTime() - start;
      Assert.assertTrue(dur >= TimeUnit.MILLISECONDS.toNanos(delay));
      long maxDelay = delay * 2;
      Assert.assertTrue("Timer accuracy: " + dur + " vs " + TimeUnit.MILLISECONDS.toNanos(maxDelay), dur < TimeUnit.MILLISECONDS.toNanos(maxDelay)); // 100% margin of error (needed for CI)
      vertx.cancelTimer(timerID);
      checkpoint.succeed();
    });
  }

  @Test
  public void testInVerticle(Checkpoint checkpoint1) {
    class MyVerticle extends AbstractVerticle {
      @Override
      public void start() {
        Thread thr = Thread.currentThread();
        vertx.setTimer(1, id -> {
          Assert.assertSame(thr, Thread.currentThread());
          checkpoint1.succeed();
        });
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle);
  }

  static class PeriodicArg {
    final long initialDelay;
    final long delay;
    PeriodicArg(long initialDelay, long delay) {
      this.initialDelay = initialDelay;
      this.delay = delay;
    }
  }

  private void periodic(Checkpoint checkpoint, PeriodicArg delay, BiFunction<PeriodicArg, Handler<Long>, Long> abc) {
    final int numFires = 10;
    final AtomicLong id = new AtomicLong(-1);
    long now = System.nanoTime();
    id.set(abc.apply(delay, new Handler<>() {
      int count;
      public void handle(Long timerID) {
        Assertions.assertThat(System.nanoTime() - now).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(delay.initialDelay + count * delay.delay));
        Assert.assertEquals(id.get(), timerID.longValue());
        count++;
        if (count == numFires) {
          vertx.cancelTimer(timerID);
          setEndTimer(checkpoint);
        }
        if (count > numFires) {
          Assert.fail("Fired too many times");
        }
      }
    }));
  }

  private void timer(Checkpoint checkpoint, long delay) {
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
        setEndTimer(checkpoint);
      }
    }));
  }

  private void setEndTimer(Checkpoint checkpoint) {
    // Set another timer to trigger test complete - this is so if the first timer is called more than once we will
    // catch it
    vertx.setTimer(10, id -> checkpoint.succeed());
  }

  @Test
  public void testCancelTimerWhenScheduledOnWorker(Checkpoint checkpoint) {
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
          checkpoint.succeed();
        });
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
    checkpoint.awaitSuccess();
    Assert.assertFalse(TestUtils.waitUntil(() -> executed.get(), 25));
  }

  @Test
  public void testWorkerTimer(Checkpoint checkpoint) {
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        vertx.setTimer(10, id -> {
          Assert.assertTrue(Context.isOnWorkerThread());
          checkpoint.succeed();
        });
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER));
  }

  @Test
  public void testFailInTimer(Checkpoint checkpoint) {
    RuntimeException failure = new RuntimeException();
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      ctx.exceptionHandler(err -> {
        Assert.assertSame(err, failure);
        checkpoint.succeed();
      });
      vertx.setTimer(5, id -> {
        throw failure;
      });
    });
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
  public void testUndeployCancelTimer(Checkpoint checkpoint) {
    testUndeployCancellation(checkpoint, () -> vertx.setTimer(1000, id -> {}));
  }

  @Test
  public void testUndeployCancelPeriodic(Checkpoint checkpoint) {
    testUndeployCancellation(checkpoint, () -> vertx.setPeriodic(1000, id -> {}));
  }

  private void testUndeployCancellation(Checkpoint checkpoint, Supplier<Long> f) {
    AtomicLong timer = new AtomicLong();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        timer.set(f.get());
      }
    }).compose(deployment -> vertx.undeploy(deployment)).onComplete(TestUtils.onSuccess(v -> {
      Assert.assertFalse(vertx.cancelTimer(timer.get()));
      checkpoint.succeed();
    }));
  }

  @Test
  public void testTimerOnContext(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    ContextInternal ctx1 = ((VertxInternal)vertx).createEventLoopContext();
    ContextInternal ctx2 = ((VertxInternal)vertx).createEventLoopContext();
    Assert.assertNotSame(ctx1, ctx2);
    ctx2.runOnContext(v -> {
      vertx.setTimer(10, l -> {
        Assert.assertSame(ctx2, vertx.getOrCreateContext());
        checkpoint1.succeed();
      });
      ctx1.setTimer(10, l -> {
        Assert.assertSame(ctx1, vertx.getOrCreateContext());
        checkpoint2.succeed();
      });
    });
  }

  @Test
  public void testPeriodicOnContext(Checkpoint checkpoint) {
    testPeriodicOnContext(checkpoint, ((VertxInternal)vertx).createEventLoopContext());
  }

  @Test
  public void testPeriodicOnDuplicatedContext(Checkpoint checkpoint) {
    testPeriodicOnContext(checkpoint, ((VertxInternal)vertx).createEventLoopContext().duplicate());
  }

  private void testPeriodicOnContext(Checkpoint checkpoint,  ContextInternal ctx2) {
    CountDownLatch counting = checkpoint.asLatch(4);
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
          counting.countDown();
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
          counting.countDown();
        }
      });
    });
  }

  @Repeat(times = 100)
  @Test
  public void testRaceWhenTimerCreatedOutsideEventLoop(Checkpoint checkpoint) {
    int numThreads = 1000;
    int numIter = 1;
    CountDownLatch count = checkpoint.asLatch(numIter * numThreads);
    for (int i = 0;i < numThreads;i++) {
      Thread th = new Thread(() -> {
        // We need something more aggressive than a millisecond for this test
        ((VertxImpl)vertx).scheduleTimeout(((VertxImpl) vertx).getOrCreateContext(), false, 1, TimeUnit.NANOSECONDS, false, ignore -> {
          count.countDown();
        });
      });
      th.start();
    }
  }

  @Test
  public void testContextTimer(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        ((ContextInternal)context).setTimer(1000, id -> {
          checkpoint1.succeed();
        });
        context.runOnContext(v -> {
          vertx.undeploy(context.deploymentID()).onComplete(TestUtils.onSuccess(ar -> {
            ((ContextInternal)context).setTimer(1, id -> {
              checkpoint2.succeed();
            });
          }));
        });
      }
    });
  }

  @Test
  public void testTimerFire(Checkpoint checkpoint) {
    long now = System.nanoTime();
    Timer timer = vertx.timer(1, TimeUnit.SECONDS);
    timer.onComplete(TestUtils.onSuccess(v -> {
      Assert.assertTrue(System.nanoTime() - now >= TimeUnit.SECONDS.toNanos(1));
      checkpoint.succeed();
    }));
  }

  @Test
  public void testTimerFireOnContext1(Checkpoint checkpoint) {
    AtomicReference<ContextInternal> ref1 = new AtomicReference<>();
    AtomicReference<ContextInternal> ref2 = new AtomicReference<>();
    new Thread(() -> {
      ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
      ref2.set(ctx);
      Timer timer = vertx.timer(10, TimeUnit.MILLISECONDS);
      timer.onComplete(TestUtils.onSuccess(v -> {
        ref1.set((ContextInternal)Vertx.currentContext());
        checkpoint.succeed();
      }));
    }).start();
    checkpoint.awaitSuccess();
    Assert.assertNotNull(ref1.get());
    Assert.assertSame(ref2.get().nettyEventLoop(), ref1.get().nettyEventLoop());
  }

  @Test
  public void testTimerFireOnContext2(Checkpoint checkpoint) {
    vertx.runOnContext(v1 -> {
      Context current = vertx.getOrCreateContext();
      ContextInternal context = ((VertxInternal) vertx).createEventLoopContext();
      Assert.assertNotSame(context, current);
      Timer timer = context.timer(10, TimeUnit.MILLISECONDS);
      timer.onComplete(TestUtils.onSuccess(v2 -> {
        Assert.assertSame(context, Vertx.currentContext());
        checkpoint.succeed();
      }));
    });
  }

  @Test
  public void testFailTimerTaskWhenCancellingTimer() {
    Timer timer = vertx.timer(10_000);
    Assert.assertTrue(timer.cancel());
    TestUtils.waitUntil(timer::failed);
    Assert.assertTrue(timer.cause() instanceof CancellationException);
  }

  @Test
  public void testFailTimerTaskWhenClosingVertx() throws Exception {
    Vertx vertx = Vertx.vertx();
    Timer timer = vertx.timer(10_000);
    vertx.close().await();
    TestUtils.waitUntil(timer::failed);
    Assert.assertTrue(timer.cause() instanceof CancellationException);
  }

  private static class TimerHandler implements Handler<Long> {
    @Override
    public void handle(Long event) {
    }
  }

  @Test
  public void testClosingVertxDoesNotKeepRefToTimers() throws Exception {
    TimerHandler handler = new TimerHandler();
    WeakReference<Handler<Long>> ref = new WeakReference<>(handler);
    Vertx vertx = Vertx.vertx();
    try {
      vertx.setTimer(10_000_000, handler);
      handler = null;
    } finally {
      vertx.close().await();
    }
    long now = System.currentTimeMillis();
    while (true) {
      Assert.assertTrue((System.currentTimeMillis() - now) <= 20_00000);
      VertxTest.runGC();
      Thread.sleep(10);
      if (ref.get() == null) {
        break;
      }
    }
  }
}
