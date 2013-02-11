/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  public void testOneOff() throws Exception {
    final AtomicLong id = new AtomicLong(-1);
    id.set(vertx.setTimer(1, new Handler<Long>() {
      int count;
      public void handle(Long timerID) {
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
    final int numFires = 10;
    final long delay = 100;
    final AtomicLong id = new AtomicLong(-1);
    id.set(vertx.setPeriodic(delay, new Handler<Long>() {
      int count;
      public void handle(Long timerID) {
        tu.checkThread();
        tu.azzert(id.get() == timerID.longValue());
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
    final long delay = 500;
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
