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

import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.streams.ReadStream;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TimerTest extends VertxTestBase {

  @Test
  public void testTimer() {
    timer(1);
  }

  @Test
  public void testPeriodic() {
    periodic(10);
  }

  /**
   * Test the timers fire with approximately the correct delay
   */
  @Test
  public void testTimings() {
    final long start = System.currentTimeMillis();
    final long delay = 2000;
    vertx.setTimer(delay, timerID -> {
      long dur = System.currentTimeMillis() - start;
      assertTrue(dur >= delay);
      long maxDelay = delay * 2;
      assertTrue("Timer accuracy: " + dur + " vs " + maxDelay, dur < maxDelay); // 100% margin of error (needed for CI)
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
          assertSame(thr, Thread.currentThread());
          if (cnt.incrementAndGet() == 5) {
            testComplete();
          }
        });
        vertx.setPeriodic(2, id -> {
          assertSame(thr, Thread.currentThread());
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

  private void periodic(long delay) {
    final int numFires = 10;
    final AtomicLong id = new AtomicLong(-1);
    id.set(vertx.setPeriodic(delay, new Handler<Long>() {
      int count;

      public void handle(Long timerID) {
        assertEquals(id.get(), timerID.longValue());
        count++;
        if (count == numFires) {
          vertx.cancelTimer(timerID);
          setEndTimer();
        }
        if (count > numFires) {
          fail("Fired too many times");
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
        assertFalse(fired);
        fired = true;
        assertEquals(id.get(), timerID.longValue());
        assertEquals(0, count);
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
  public void testTimerStreamSetHandlerSchedulesTheTimer() {
    vertx.runOnContext(v -> {
      ReadStream<Long> timer = vertx.timerStream(200);
      AtomicBoolean handled = new AtomicBoolean();
      timer.handler(l -> {
        assertFalse(handled.get());
        handled.set(true);
      });
      timer.endHandler(v2 -> {
        assertTrue(handled.get());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testTimerStreamExceptionDuringHandle() {
    vertx.runOnContext(v -> {
      ReadStream<Long> timer = vertx.timerStream(200);
      AtomicBoolean handled = new AtomicBoolean();
      timer.handler(l -> {
        assertFalse(handled.get());
        handled.set(true);
        throw new RuntimeException();
      });
      timer.endHandler(v2 -> {
        assertTrue(handled.get());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testTimerStreamCallingWithNullHandlerCancelsTheTimer() {
    vertx.runOnContext(v -> {
      ReadStream<Long> timer = vertx.timerStream(200);
      AtomicInteger count = new AtomicInteger();
      timer.handler(l -> {
        if (count.incrementAndGet() == 1) {
          timer.handler(null);
          vertx.setTimer(200, id -> {
            assertEquals(1, count.get());
            testComplete();
          });
        } else {
          fail();
        }
      });
    });
    await();
  }

  @Test
  public void testTimerStreamCancellation() {
    vertx.runOnContext(v -> {
      TimeoutStream timer = vertx.timerStream(200);
      AtomicBoolean called = new AtomicBoolean();
      timer.handler(l -> {
        called.set(true);
      });
      timer.cancel();
      vertx.setTimer(500, id -> {
        assertFalse(called.get());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testTimerSetHandlerTwice() {
    vertx.runOnContext(v -> {
      ReadStream<Long> timer = vertx.timerStream(200);
      timer.handler(l -> testComplete());
      try {
        timer.handler(l -> fail());
        fail();
      } catch (IllegalStateException ignore) {
      }
    });
    await();
  }

  @Test
  public void testTimerPauseResume() {
    ReadStream<Long> timer = vertx.timerStream(10);
    timer.handler(l -> testComplete());
    timer.pause();
    timer.resume();
    await();
  }

  @Test
  public void testTimerPause() {
    vertx.runOnContext(v -> {
      ReadStream<Long> timer = vertx.timerStream(10);
      timer.handler(l -> fail());
      timer.endHandler(l -> testComplete());
      timer.pause();
    });
    await();
  }

  @Test
  public void testPeriodicStreamHandler() {
    TimeoutStream timer = vertx.periodicStream(10);
    AtomicInteger count = new AtomicInteger();
    timer.handler(l -> {
      int value = count.incrementAndGet();
      switch (value) {
        case 0:
          break;
        case 1:
          throw new RuntimeException();
        case 2:
          timer.cancel();
          testComplete();
          break;
        default:
          fail();
      }
    });
    timer.endHandler(v -> {
      fail();
    });
    await();
  }

  @Test
  public void testPeriodicSetHandlerTwice() {
    vertx.runOnContext(v -> {
      ReadStream<Long> timer = vertx.periodicStream(200);
      timer.handler(l -> testComplete());
      try {
        timer.handler(l -> fail());
        fail();
      } catch (IllegalStateException ignore) {
      }
    });
    await();
  }

  @Test
  public void testPeriodicPauseResume() {
    ReadStream<Long> timer = vertx.periodicStream(200);
    AtomicInteger count = new AtomicInteger();
    timer.handler(id -> {
      int cnt = count.incrementAndGet();
      if (cnt == 2) {
        timer.pause();
        vertx.setTimer(500, id2 -> {
          assertEquals(2, count.get());
          timer.resume();
        });
      } else if (cnt == 3) {
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testTimeoutStreamEndCallbackAsynchronously() {
    TimeoutStream stream = vertx.timerStream(200);
    ThreadLocal<Object> stack = new ThreadLocal<>();
    stack.set(true);
    stream.endHandler(v2 -> {
      assertTrue(Vertx.currentContext().isEventLoopContext());
      assertNull(stack.get());
      testComplete();
    });
    stream.handler(id -> {
    });
    await();
  }

  @Test
  public void testCancelTimerWhenScheduledOnWorker() {
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        long id = vertx.setTimer(100, id_ -> {
          fail();
        });
        Thread.sleep(200);
        assertTrue(vertx.cancelTimer(id));
        testComplete();
      }
    }, new DeploymentOptions().setWorker(true));
    await();
  }

  @Test
  public void testWorkerTimer() {
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        vertx.setTimer(10, id -> {
          assertTrue(Context.isOnWorkerThread());
          testComplete();
        });
      }
    }, new DeploymentOptions().setWorker(true));
    await();
  }

  @Test
  public void testFailInTimer() {
    RuntimeException failure = new RuntimeException();
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      ctx.exceptionHandler(err -> {
        assertSame(err, failure);
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
    }, onSuccess(deployment -> {
      vertx.undeploy(deployment, v -> {
        assertFalse(vertx.cancelTimer(timer.get()));
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testTimerOnContext() {
    disableThreadChecks();
    ContextInternal ctx1 = ((VertxInternal)vertx).createEventLoopContext();
    waitFor(2);
    ContextInternal ctx2 = ((VertxInternal)vertx).createEventLoopContext();
    assertNotSame(ctx1, ctx2);
    ctx2.runOnContext(v -> {
      vertx.setTimer(10, l -> {
        assertSame(ctx2, vertx.getOrCreateContext());
        complete();
      });
      ctx1.setTimer(10, l -> {
        assertSame(ctx1, vertx.getOrCreateContext());
        complete();
      });
    });
    await();
  }

  @Test
  public void testPeriodicOnContext() {
    disableThreadChecks();
    waitFor(4);
    ContextInternal ctx1 = ((VertxInternal)vertx).createEventLoopContext();
    ContextInternal ctx2 = ((VertxInternal)vertx).createEventLoopContext();
    assertNotSame(ctx1, ctx2);
    ctx2.runOnContext(v -> {
      vertx.setPeriodic(10, new Handler<Long>() {
        int count;

        @Override
        public void handle(Long l) {
          assertSame(ctx2, vertx.getOrCreateContext());
          if (++count == 2) {
            vertx.cancelTimer(l);
          }
          complete();
        }
      });
      ctx1.setPeriodic(10, new Handler<Long>() {
        int count;

        @Override
        public void handle(Long l) {
          assertSame(ctx1, vertx.getOrCreateContext());
          if (++count == 2) {
            vertx.cancelTimer(l);
          }
          complete();
        }
      });
    });
    await();
  }
}
