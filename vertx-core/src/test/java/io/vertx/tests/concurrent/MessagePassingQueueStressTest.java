package io.vertx.tests.concurrent;

import io.vertx.core.VertxOptions;
<<<<<<<< HEAD:vertx-core/src/test/java/io/vertx/tests/concurrent/MessagePassingQueueStressTest.java
import io.vertx.core.streams.impl.MessagePassingQueue;
========
import io.vertx.core.streams.impl.MessageChannel;
>>>>>>>> 7a216501b (Rename more appropriately the queue to channel):vertx-core/src/test/java/io/vertx/tests/concurrent/MessageChannelStressTest.java
import io.vertx.test.core.Repeat;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

<<<<<<<< HEAD:vertx-core/src/test/java/io/vertx/tests/concurrent/MessagePassingQueueStressTest.java
import static io.vertx.core.streams.impl.MessagePassingQueue.numberOfUnwritableSignals;

public class MessagePassingQueueStressTest extends VertxTestBase {
========
import static io.vertx.core.streams.impl.MessageChannel.numberOfUnwritableSignals;

public class MessageChannelStressTest extends VertxTestBase {
>>>>>>>> 7a216501b (Rename more appropriately the queue to channel):vertx-core/src/test/java/io/vertx/tests/concurrent/MessageChannelStressTest.java

  @Override
  public void setUp() throws Exception {
    super.setUp();
    disableThreadChecks();
  }

  @Test
  public void testSimple() throws Exception {
    LongAdder counter = new LongAdder();
<<<<<<<< HEAD:vertx-core/src/test/java/io/vertx/tests/concurrent/MessagePassingQueueStressTest.java
    MessagePassingQueue.MpSc<Object> queue = new MessagePassingQueue.MpSc<>(foo -> {
========
    MessageChannel.MpSc<Object> queue = new MessageChannel.MpSc<>(foo -> {
>>>>>>>> 7a216501b (Rename more appropriately the queue to channel):vertx-core/src/test/java/io/vertx/tests/concurrent/MessageChannelStressTest.java
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
            int flags = queue.add(elt);
<<<<<<<< HEAD:vertx-core/src/test/java/io/vertx/tests/concurrent/MessagePassingQueueStressTest.java
            if ((flags & MessagePassingQueue.DRAIN_REQUIRED_MASK) != 0) {
              flags = queue.drain();
              assertEquals(0, flags & (MessagePassingQueue.DRAIN_REQUIRED_MASK));
========
            if ((flags & MessageChannel.DRAIN_REQUIRED_MASK) != 0) {
              flags = queue.drain();
              assertEquals(0, flags & (MessageChannel.DRAIN_REQUIRED_MASK));
>>>>>>>> 7a216501b (Rename more appropriately the queue to channel):vertx-core/src/test/java/io/vertx/tests/concurrent/MessageChannelStressTest.java
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

  @Repeat(times = 50)
  @Test
  public void testWriteQueueFull() throws Exception {
    int numProducers = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE / 2;
    int numReps = 10000;
    int[] consumedLocal = new int[1];
<<<<<<<< HEAD:vertx-core/src/test/java/io/vertx/tests/concurrent/MessagePassingQueueStressTest.java
    MessagePassingQueue.MpSc<Object> queue = new MessagePassingQueue.MpSc<>(elt -> {
========
    MessageChannel.MpSc<Object> queue = new MessageChannel.MpSc<>(elt -> {
>>>>>>>> 7a216501b (Rename more appropriately the queue to channel):vertx-core/src/test/java/io/vertx/tests/concurrent/MessageChannelStressTest.java
      consumedLocal[0]++;
      return true;
    });
    // The number of consumed elements
    AtomicInteger numOfConsumedElements = new AtomicInteger();
    AtomicInteger numOfUnwritableSignalsFromDrain = new AtomicInteger();
    AtomicInteger numOfUnwritableSignalsFromSubmit = new AtomicInteger();
    Thread[] producers = new Thread[numProducers];
    CyclicBarrier start = new CyclicBarrier(1 + producers.length);
    for (int i = 0;i < producers.length;i++) {
      int val = i;
      String name = "producer-" + val;
      Thread producer = new Thread(() -> {
        try {
          start.await();
        } catch (Exception e) {
          fail(e);
          return;
        }
        int iter = numReps;
        while (iter-- > 0) {
          int flags = queue.add(val);
<<<<<<<< HEAD:vertx-core/src/test/java/io/vertx/tests/concurrent/MessagePassingQueueStressTest.java
          if ((flags & MessagePassingQueue.UNWRITABLE_MASK) != 0) {
            numOfUnwritableSignalsFromSubmit.incrementAndGet();
          }
          if ((flags & MessagePassingQueue.DRAIN_REQUIRED_MASK) != 0) {
========
          if ((flags & MessageChannel.UNWRITABLE_MASK) != 0) {
            numOfUnwritableSignalsFromSubmit.incrementAndGet();
          }
          if ((flags & MessageChannel.DRAIN_REQUIRED_MASK) != 0) {
>>>>>>>> 7a216501b (Rename more appropriately the queue to channel):vertx-core/src/test/java/io/vertx/tests/concurrent/MessageChannelStressTest.java
            int flags2;
            // We synchronize to simulate single consumer with respect to internal queue state
            // todo : we should sync that although in practice this is always the same thread (event-loop)
            // it's just more convenient to do that for writing this test
<<<<<<<< HEAD:vertx-core/src/test/java/io/vertx/tests/concurrent/MessagePassingQueueStressTest.java
            synchronized (MessagePassingQueueStressTest.class) {
========
            synchronized (MessageChannelStressTest.class) {
>>>>>>>> 7a216501b (Rename more appropriately the queue to channel):vertx-core/src/test/java/io/vertx/tests/concurrent/MessageChannelStressTest.java
              consumedLocal[0] = 0;
              flags2 = queue.drain();
              numOfConsumedElements.addAndGet(consumedLocal[0]);
            }
<<<<<<<< HEAD:vertx-core/src/test/java/io/vertx/tests/concurrent/MessagePassingQueueStressTest.java
            if ((flags2 & MessagePassingQueue.WRITABLE_MASK) != 0) {
========
            if ((flags2 & MessageChannel.WRITABLE_MASK) != 0) {
>>>>>>>> 7a216501b (Rename more appropriately the queue to channel):vertx-core/src/test/java/io/vertx/tests/concurrent/MessageChannelStressTest.java
              int unwritable = numberOfUnwritableSignals(flags2);
              numOfUnwritableSignalsFromDrain.addAndGet(unwritable);
            }
          }
        }
      }, name);
      producer.start();
      producers[i] = producer;
    }
    start.await();
    for (int i = 0;i < numProducers;i++) {
      producers[i].join(10_000);
    }
    assertEquals((long) numProducers * numReps, numOfConsumedElements.get());
    assertEquals(numOfUnwritableSignalsFromSubmit.get(), numOfUnwritableSignalsFromDrain.get());
  }
}
