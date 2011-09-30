/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.tests.core;

import org.nodex.java.core.ConnectionPool;
import org.nodex.java.core.Handler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.SimpleHandler;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ConnectionPoolTest extends TestBase {

  int numLoops = 6;
  long numGets = 100000;
  TestPool pool;
  CountDownLatch latch = new CountDownLatch(numLoops);

  @Test
  public void test1() throws Exception {

    pool = new TestPool();
    pool.setMaxPoolSize(1000);

    long start = System.currentTimeMillis();

    Looper[] loops = new Looper[numLoops];

    for (int i = 0; i < numLoops; i++) {
      loops[i] = new Looper();
      Nodex.instance.go(loops[i]);
    }

//    Thread.sleep(10000);
//
//    long end = System.currentTimeMillis();
//
//    long totCount = 0;
//    for (int i = 0; i < numLoops; i++) {
//      totCount += loops[i].cnt;
//    }
//
//    double rate = 1000 * (double)totCount / (end - start);
//
//    System.out.println("Total rate: " + rate);

    azzert(latch.await(30, TimeUnit.SECONDS));
  }

  class Looper implements Runnable {

    long cnt;

    public void run() {
      getConnection(numGets);
    }
    private void getConnection(final long count) {
      pool.getConnection(new Handler<Integer>() {
        public void handle(Integer i) {
          cnt++;
          pool.returnConnection(0);
          if (count > 0) {
            Nodex.instance.nextTick(new SimpleHandler() {
              public void handle() {
                 getConnection(count - 1);
              }
            });
          } else {
            latch.countDown();
          }
        }
      }, Nodex.instance.getContextID());
    }
  }

  class TestPool extends ConnectionPool<Integer> {

    @Override
    protected void connect(Handler<Integer> connectHandler, long contextID) {
      connectHandler.handle(0);
    }
  }
}
