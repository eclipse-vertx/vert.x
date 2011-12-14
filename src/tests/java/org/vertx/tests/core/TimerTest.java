/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.tests.core;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.internal.VertxInternal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TimerTest extends TestBase {

  @Test
  /*
  Test that can't set a timer without a context
   */
  public void testNoContext() throws Exception {
    final AtomicBoolean fired = new AtomicBoolean(false);
    try {
      Vertx.instance.setTimer(1, new Handler<Long>() {
        public void handle(Long timerID) {
          fired.set(true);
        }
      });
      assert false : "Should throw Exception";
    } catch (IllegalStateException e) {
      //OK
    }
    Thread.sleep(100);
    assert !fired.get();
  }

  @Test
  public void testOneOff() throws Exception {
    final CountDownLatch endLatch = new CountDownLatch(1);

    VertxInternal.instance.go(new Runnable() {
      public void run() {
        final Thread th = Thread.currentThread();
        final long contextID = Vertx.instance.getContextID();

        Vertx.instance.setTimer(1, new Handler<Long>() {
          public void handle(Long timerID) {
            azzert(th == Thread.currentThread());
            azzert(contextID == Vertx.instance.getContextID());
            endLatch.countDown();
          }
        });
      }
    });

    azzert(endLatch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }

  @Test
  public void testPeriodic() throws Exception {
    final int numFires = 10;

    final CountDownLatch endLatch = new CountDownLatch(1);

    final long delay = 100;

    VertxInternal.instance.go(new Runnable() {
      public void run() {

        final Thread th = Thread.currentThread();
        final long contextID = Vertx.instance.getContextID();

        long id = Vertx.instance.setPeriodic(delay, new Handler<Long>() {
          int count;

          public void handle(Long timerID) {
            azzert(th == Thread.currentThread());
            azzert(contextID == Vertx.instance.getContextID());
            count++;
            if (count == numFires) {
              Vertx.instance.cancelTimer(timerID);
              endLatch.countDown();
            }
            if (count > numFires) {
              azzert(false, "Fired too many times");
            }
          }
        });
      }
    });

    azzert(endLatch.await(5, TimeUnit.SECONDS));

    //Wait a little bit longer in case it fires again
    Thread.sleep(250);

    throwAssertions();
  }


  @Test
  /*
  Test the timers fire with approximately the correct delay
   */
  public void testTimings() throws Exception {
    final CountDownLatch endLatch = new CountDownLatch(1);

    VertxInternal.instance.go(new Runnable() {
      public void run() {

        final Thread th = Thread.currentThread();
        final long contextID = Vertx.instance.getContextID();
        final long start = System.nanoTime();
        final long delay = 500;
        Vertx.instance.setTimer(delay, new Handler<Long>() {
          public void handle(Long timerID) {
            long dur = (System.nanoTime() - start) / 1000000;
            azzert(dur >= delay);
            azzert(dur < delay * 1.5); // 50% margin of error
            azzert(th == Thread.currentThread());
            azzert(contextID == Vertx.instance.getContextID());
            endLatch.countDown();
          }
        });
      }
    });

    azzert(endLatch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }

}
