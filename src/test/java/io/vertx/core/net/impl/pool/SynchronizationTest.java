/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.pool;

import io.vertx.test.core.AsyncTestBase;
import org.junit.Assume;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SynchronizationTest extends AsyncTestBase {

  private static Long iterationsForOneMilli;

  private static long iterationsForOneMilli() {
    Long val = iterationsForOneMilli;
    if (val == null) {
      val = Utils.calibrateBlackhole();
      iterationsForOneMilli = val;
    }
    return val;
  }

  private static void burnCPU(long cpu) {
    final long target_delay = Utils.ONE_MICRO_IN_NANO * cpu;
    long num_iters = Math.round(target_delay * 1.0 * iterationsForOneMilli() / Utils.ONE_MILLI_IN_NANO);
    Utils.blackholeCpu(num_iters);
  }

  @Test
  public void testActionReentrancy() throws Exception {
    AtomicBoolean isReentrant1 = new AtomicBoolean();
    AtomicBoolean isReentrant2 = new AtomicBoolean();
    Executor<Object> sync = new CombinerExecutor<>(new Object());
    CountDownLatch latch = new CountDownLatch(2);
    sync.submit(state1 -> {
      AtomicBoolean inCallback = new AtomicBoolean();
      inCallback.set(true);
      try {
        sync.submit(state2 -> {
          isReentrant1.set(inCallback.get());
          latch.countDown();
          return new Task() {
            @Override
            public void run() {
              isReentrant2.set(inCallback.get());
              latch.countDown();
            }
          };
        });
      } finally {
        inCallback.set(false);
      }
      return null;
    });
    awaitLatch(latch);
    assertFalse(isReentrant1.get());
    assertFalse(isReentrant2.get());
  }

  @Test
  public void testFoo() throws Exception {
    Assume.assumeFalse(io.vertx.core.impl.Utils.isWindows());
    int numThreads = 8;
    int numIter = 1_000 * 100;
    Executor<Object> sync = new CombinerExecutor<>(new Object());
    Executor.Action action = s -> {
      burnCPU(10);
      return null;
    };
    Thread[] threads = new Thread[numThreads];
    for (int i = 0;i < numThreads;i++) {
      threads[i] = new Thread(() -> {
        for (int j = 0;j < numIter;j++) {
          sync.submit(action);
        }
      });
    }
    for (Thread t : threads) {
      t.start();
    }
    for (Thread t : threads) {
      t.join();
    }
  }

  public static class Utils {
    public static long res = 0; // value sink
    public static long ONE_MILLI_IN_NANO = 1000000;
    public static long ONE_MICRO_IN_NANO = 1000;

    /* the purpose of this method is to consume pure CPU without
     * additional resources (memory, io).
     * We may need to simulate milliseconds of cpu usage so
     * base calculation is somewhat complex to avoid too many iterations
     */
    public static void blackholeCpu(long iterations) {
      long result = 0;
      for (int i=0; i < iterations; i++) {
        int next = (ThreadLocalRandom.current().nextInt() % 1019) / 17;
        result = result ^ (Math.round(Math.pow(next,3)) % 251);
      }
      res += result;
    }

    /* Estimates the number of iterations of blackholeCpu needed to
     * spend one milliseconds of CPU time
     */
    public static long calibrateBlackhole() {
      ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
      // Make the blackholeCpu method hot, to force C2 optimization
      for (int i=0; i < 50000; i++) {
        Utils.blackholeCpu(100);
      }
      // find the number of iterations needed to spend more than 1 milli
      // of cpu time
      final long[] iters = {1000,5000,10000,20000,50000,100000};
      long timing = 0;
      int i=-1;
      while (timing < ONE_MILLI_IN_NANO && ++i < iters.length) {
        long start_cpu = threadBean.getCurrentThreadCpuTime();
        Utils.blackholeCpu(iters[i]);
        timing=threadBean.getCurrentThreadCpuTime()-start_cpu;
      }
      // estimate the number of iterations for 1 milli
      return Math.round(Math.ceil((ONE_MILLI_IN_NANO*1.0/timing)*iters[i]));
    }
  }

  @Test
  public void testOrdering() throws Exception {
    Executor<Object> sync = new CombinerExecutor<>(new Object());
    AtomicInteger order = new AtomicInteger();
    sync.submit(s -> {
      sync.submit(s_ -> new Task() {
        @Override
        public void run() {
          order.compareAndSet(1, 2);
        }
      });
      sync.submit(s_ -> new Task() {
        @Override
        public void run() {
          order.compareAndSet(2, 3);
        }
      });
      return new Task() {
        @Override
        public void run() {
          order.compareAndSet(0, 1);
        }
      };
    });
    assertEquals(3, order.get());
  }
}
