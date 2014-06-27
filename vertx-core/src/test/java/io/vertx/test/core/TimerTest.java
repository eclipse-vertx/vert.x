/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TimerTest extends VertxTestBase {

  @Test
  public void testTimer() throws Exception {
    timer(1);
  }

  @Test
  public void testPeriodic() throws Exception {
    periodic(10);
  }

  @Test
  /**
   * Test the timers fire with approximately the correct delay
   */
  public void testTimings() throws Exception {
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
  public void testInVerticle() throws Exception {
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

  private void periodic(long delay) throws Exception {
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

  private void timer(long delay) throws Exception {
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

}
