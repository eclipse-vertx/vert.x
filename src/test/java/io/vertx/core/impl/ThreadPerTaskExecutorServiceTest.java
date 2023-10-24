package io.vertx.core.impl;

import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPerTaskExecutorServiceTest extends AsyncTestBase {

  @Test
  public void testExecute() throws Exception {
    ThreadPerTaskExecutorService exec = new ThreadPerTaskExecutorService(Executors.defaultThreadFactory());
    int numTasks = 100;
    Set<Thread> threads = Collections.synchronizedSet(new HashSet<>());
    for (int i = 0;i < numTasks;i++) {
      exec.execute(() -> threads.add(Thread.currentThread()));
    }
    exec.shutdown();
    exec.awaitTermination(5, TimeUnit.SECONDS);
    assertEquals(numTasks, threads.size());
  }

  @Test
  public void testShutdown() throws Exception {
    ThreadPerTaskExecutorService exec = new ThreadPerTaskExecutorService(Executors.defaultThreadFactory());
    int numTasks = 10;
    CountDownLatch latch = new CountDownLatch(1);
    CyclicBarrier barrier = new CyclicBarrier(numTasks + 1);
    for (int i = 0;i < numTasks;i++) {
      exec.execute(() -> {
        try {
          barrier.await();
          latch.await();
        } catch (Exception e) {
          fail(e);
        }
      });
    }
    barrier.await();
    exec.shutdown();
    latch.countDown();
    long now = System.currentTimeMillis();
    exec.awaitTermination(5, TimeUnit.SECONDS);
    assertTrue(System.currentTimeMillis() - now < 1000);
  }

  @Test
  public void testShutdownEmpty() throws Exception {
    ThreadPerTaskExecutorService exec = new ThreadPerTaskExecutorService(Executors.defaultThreadFactory());
    exec.shutdown();
    long now = System.currentTimeMillis();
    exec.awaitTermination(5, TimeUnit.SECONDS);
    assertTrue(System.currentTimeMillis() - now < 1000);
  }

  @Test
  public void testInterrupt() throws Exception {
    ThreadPerTaskExecutorService exec = new ThreadPerTaskExecutorService(Executors.defaultThreadFactory());
    int numTasks = 100;
    CyclicBarrier barrier = new CyclicBarrier(numTasks + 1);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger interrupts = new AtomicInteger();
    for (int i = 0;i < numTasks;i++) {
      exec.execute(() -> {
        try {
          barrier.await();
          latch.await();
        } catch (InterruptedException e) {
          interrupts.incrementAndGet();
        } catch (BrokenBarrierException e) {
          fail(e);
        }
      });
    }
    barrier.await();
    exec.shutdownNow();
    exec.awaitTermination(5, TimeUnit.SECONDS);
    assertEquals(numTasks, interrupts.get());
  }
}
