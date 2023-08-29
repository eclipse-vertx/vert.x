package io.vertx.core.streams;

import io.vertx.core.streams.impl.OutboundWriteQueue;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static io.vertx.core.streams.impl.OutboundWriteQueue.numberOfUnwritableSignals;

public class OutboundWriteQueueStressTest extends VertxTestBase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    disableThreadChecks();
  }

  @Test
  public void testSimple() throws Exception {
    LongAdder counter = new LongAdder();
    OutboundWriteQueue<Object> queue = new OutboundWriteQueue<>(foo -> {
      counter.increment();
      return true;
    });
    int numThreads = 10;
    int numEmissions = 10;
    int numReps = 1000;
    Object elt = new Object();
    Thread[] threads = new Thread[numThreads];
    CyclicBarrier barrier = new CyclicBarrier(1 + numThreads);
    for (int i = 0;i < numThreads;i++) {
      Thread thread = new Thread(() -> {
        try {
          barrier.await();
        } catch (Exception e) {
          fail(e);
        }
        for (int j = 0; j < numReps; j++) {
          for (int k = 0;k < numEmissions;k++) {
            int flags = queue.submit(elt);
            if ((flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0) {
              flags = queue.drain();
              assertEquals(0, flags & (OutboundWriteQueue.DRAIN_REQUIRED_MASK));
            }
          }
          try {
            Thread.sleep(1);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      });
      thread.start();
      threads[i] = thread;
    }
    barrier.await();
    for (int i = 0;i < numThreads;i++) {
      threads[i].join();
    }
    assertEquals(numThreads * numEmissions * numReps, counter.intValue());
  }

  @Test
  public void testWriteQueueFull() throws Exception {
    OutboundWriteQueue<Object> queue = new OutboundWriteQueue<>(elt -> true);
    // Simulate pending elements that are drained when the queue becomes writable
    AtomicInteger pending = new AtomicInteger();
    // Write queue full
    AtomicInteger wqf = new AtomicInteger();
    int reps = 10000;
    Thread[] producers = new Thread[10];
    CyclicBarrier start = new CyclicBarrier(1 + producers.length);
    CyclicBarrier stop = new CyclicBarrier(1 + producers.length);
    for (int i = 0;i < producers.length;i++) {
      int val = i;
      String name = "producer-" + val;
      Thread producer = new Thread(() -> {
        try {
          start.await();
        } catch (Exception e) {
          fail(e);
        }
        int iter = reps;
        while (iter-- > 0) {
          int flags = queue.submit(val);
          if ((flags & OutboundWriteQueue.QUEUE_UNWRITABLE_MASK) != 0) {
            wqf.decrementAndGet();
            pending.incrementAndGet(); // Simulate pending elements
          }
          if ((flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0) {
            int flags2;
            // We synchronize to simulate single consumer with respect to internal queue state
            // todo : we should sync that although in practice this is always the same thread (event-loop)
            // it's just more convenient to do that for writing this test
            synchronized (OutboundWriteQueueStressTest.class) {
              flags2 = queue.drain();
            }
            if ((flags2 & OutboundWriteQueue.QUEUE_WRITABLE_MASK) != 0) {
              int unwritable = numberOfUnwritableSignals(flags2);
              int writable = wqf.addAndGet(unwritable);
              if (writable - unwritable < 0 && writable == 0) {
                // Drain pending elements
                pending.set(0);
              }
            }
          }
        }
        try {
          stop.await();
        } catch (Exception e) {
          fail(e);
        }
      }, name);
      producer.start();;
      producers[i] = producer;
    }
    start.await();
    stop.await();
    assertEquals(0, wqf.get());
    assertEquals(0, pending.get());
  }
}
