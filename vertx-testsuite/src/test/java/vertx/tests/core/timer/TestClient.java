/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package vertx.tests.core.timer;

import org.vertx.java.core.Handler;
import org.vertx.java.testframework.TestClientBase;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testTimer() throws Exception {
    timer(1);
  }

  private void timer(long delay) throws Exception {
    final AtomicLong id = new AtomicLong(-1);
    id.set(vertx.setTimer(delay, new Handler<Long>() {
      int count;
      boolean fired;
      public void handle(Long timerID) {
        tu.azzert(!fired);
        fired = true;
        tu.checkThread();
        tu.azzert(id.get() == timerID.longValue());
        tu.azzert(count == 0);
        count++;
        setEndTimer();
      }
    }));
  }

  private void setEndTimer() {
    // Set another timer to trigger test complete - this is so if the first timer is called more than once we will
    // catch it
    vertx.setTimer(10, new Handler<Long>() {
      public void handle(Long timerID) {
        tu.checkThread();
        tu.testComplete();
      }
    });
  }

  public void testPeriodic() throws Exception {
    periodic(10);
  }

  private void periodic(long delay) throws Exception {
    final int numFires = 10;
    final AtomicLong id = new AtomicLong(-1);
    id.set(vertx.setPeriodic(delay, new Handler<Long>() {
      int count;
      public void handle(Long timerID) {
        tu.checkThread();
        tu.azzert(id.get() == timerID);
        count++;
        if (count == numFires) {
          vertx.cancelTimer(timerID);
          setEndTimer();
        }
        if (count > numFires) {
          tu.azzert(false, "Fired too many times");
        }
      }
    }));
  }

  /**
   * Test the timers fire with approximately the correct delay
   */
  public void testTimings() throws Exception {
    final long start = System.currentTimeMillis();
    final long delay = 2000;
    vertx.setTimer(delay, new Handler<Long>() {
      public void handle(Long timerID) {
        tu.checkThread();
        long dur = System.currentTimeMillis() - start;
        tu.azzert(dur >= delay);
        long maxDelay = delay * 2;
        tu.azzert(dur < maxDelay, "Timer accuracy: " + dur + " vs " + maxDelay); // 100% margin of error (needed for CI)
        vertx.cancelTimer(timerID);
        tu.testComplete();
      }
    });
  }

}
