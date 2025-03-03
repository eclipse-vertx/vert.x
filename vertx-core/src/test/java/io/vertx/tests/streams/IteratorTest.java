/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.streams;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.streams.ReadStreamIterator;
import io.vertx.core.streams.ReadStream;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.RepeatRule;
import io.vertx.test.fakestream.FakeStream;
import org.junit.Rule;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class IteratorTest extends AsyncTestBase {

  @Test
  public void testIteratorResuming() {
    FakeStream<Integer> stream = new FakeStream<>();
    stream.setWriteQueueMaxSize(0);
    Iterator<Integer> iterator = ReadStreamIterator.iterator(stream);
    for (int i = 0;i < 16;i++) {
      assertFalse(stream.writeQueueFull());
      stream.write(i);
    }
    stream.write(17);
    assertTrue(stream.writeQueueFull());
    for (int i = 0;i < 16;i++) {
      iterator.next();
    }
    assertFalse(stream.writeQueueFull());
  }

  @Test
  public void testEnd() {
    FakeStream<Integer> stream = new FakeStream<>();
    Iterator<Integer> iterator = ReadStreamIterator.iterator(stream);
    for (int i = 0;i < 15;i++) {
      stream.write(i);
    }
    stream.end();
    for (int i = 0;i < 15;i++) {
      assertTrue(iterator.hasNext());
      iterator.next();
    }
    assertFalse(iterator.hasNext());
    try {
      iterator.next();
      fail();
    } catch (NoSuchElementException expected) {
    }
  }

  @Test
  public void testFail() {
    FakeStream<Integer> stream = new FakeStream<>();
    Iterator<Integer> iterator = ReadStreamIterator.iterator(stream);
    for (int i = 0;i < 15;i++) {
      stream.write(i);
    }
    Throwable cause = new Throwable();
    stream.fail(cause);
    for (int i = 0;i < 15;i++) {
      assertTrue(iterator.hasNext());
      iterator.next();
    }
    assertFalse(iterator.hasNext());
    try {
      iterator.next();
      fail();
    } catch (Throwable failure) {
      assertSame(cause, failure);
    }
  }

  @Test
  public void testHasNextSignal() throws Exception {
    FakeStream<Integer> stream = new FakeStream<>();
    Iterator<Integer> iterator = ReadStreamIterator.iterator(stream);
    int numThreads = 4;
    Thread[] consumers = new Thread[numThreads];
    for (int i = 0;i < numThreads;i++) {
      Thread consumer = new Thread(iterator::hasNext);
      consumers[i] = consumer;
      consumer.start();
      assertWaitUntil(() -> consumer.getState() == Thread.State.WAITING);
    }
    stream.end();
    for (Thread consumer : consumers) {
      consumer.join();
    }
  }

  @Rule
  public RepeatRule rule = new RepeatRule();

  @Repeat(times = 100)
  @Test
  public void testConcurrentReads() throws Exception {
    // While the iterator should not be used concurrently because of hasNext()/next() races
    // calling next() from multiple thread is possible
    class Stream implements ReadStream<Integer> {
      private Handler<Integer> handler;
      private Handler<Void> endHandler;
      private long demand = Long.MAX_VALUE;
      private final Lock lock = new ReentrantLock();
      private final Condition producerSignal = lock.newCondition();
      @Override
      public ReadStream<Integer> exceptionHandler(Handler<Throwable> handler) {
        return this;
      }
      public void write(Integer element) throws InterruptedException {
        lock.lock();
        Handler<Integer> h;
        try {
          while (true) {
            long d = demand;
            if (d > 0L) {
              if (d != Long.MAX_VALUE) {
                demand = d - 1;
              }
              h = handler;
              break;
            } else {
              producerSignal.await();
            }
          }
        } finally {
          lock.unlock();
        }
        if (h != null) {
          h.handle(element);
        }
      }
      public void end() throws InterruptedException {
        lock.lock();
        Handler<Void> h;
        try {
          while (true) {
            long d = demand;
            if (d > 0L) {
              h = endHandler;
              break;
            } else {
              producerSignal.await();
            }
          }
        } finally {
          lock.unlock();
        }
        if (h != null) {
          h.handle(null);
        }
      }
      @Override
      public ReadStream<Integer> handler(Handler<Integer> handler) {
        lock.lock();
        try {
          this.handler = handler;
        } finally {
          lock.unlock();
        }
        return this;
      }
      @Override
      public ReadStream<Integer> endHandler(Handler<Void> endHandler) {
        lock.lock();
        try {
          this.endHandler = endHandler;
        } finally {
          lock.unlock();
        }
        return this;
      }
      @Override
      public ReadStream<Integer> pause() {
        lock.lock();
        try {
          demand = 0L;
        } finally {
          lock.unlock();
        }
        return this;
      }
      @Override
      public ReadStream<Integer> resume() {
        return fetch(Long.MAX_VALUE);
      }
      @Override
      public ReadStream<Integer> fetch(long amount) {
        if (amount < 0L) {
          throw new IllegalArgumentException();
        }
        if (amount > 0L) {
          lock.lock();
          try {
            long d = demand;
            d += amount;
            if (d < 0L) {
              d = Long.MAX_VALUE;
            }
            demand = d;
            producerSignal.signal();
          } finally {
            lock.unlock();
          }
        }
        return this;
      }
    }

    Stream stream = new Stream();
    Iterator<Integer> iterator = ReadStreamIterator.iterator(stream);
    int numThreads = 8;
    int numElements = 16384;
    CyclicBarrier barrier = new CyclicBarrier(numThreads + 1);
    class Consumer extends Thread {
      final List<Integer> consumed = new ArrayList<>();
      @Override
      public void run() {
        try {
          barrier.await();
        } catch (Exception e) {
          return;
        }
        while (true) {
          try {
            Integer elt = iterator.next();
            consumed.add(elt);
          } catch (NoSuchElementException e) {
            // Done
            break;
          }
        }
      }
    }
    Consumer[] consumers = new Consumer[numElements];
    for (int i = 0;i < numThreads;i++) {
      Consumer consumer = new Consumer();
      consumer.start();
      consumers[i] = consumer;
    }
    barrier.await();
    for (int i = 0;i < numElements;i++) {
      stream.write(i);
    }
    stream.end();
    ArrayList<Integer> list = new ArrayList<>();
    for (int i = 0;i < numThreads;i++) {
      Consumer consumer = consumers[i];
      consumer.join(1000);
      if (consumer.getState() != Thread.State.TERMINATED) {
        System.out.println("Could not join timely " + consumer + ":");
        Exception where = new Exception();
        where.setStackTrace(consumer.getStackTrace());
        where.printStackTrace(System.out);
        fail();
      }
      list.addAll(consumer.consumed);
    }
    assertEquals(list.size(), numElements);
  }

  @Test
  public void testVirtualThread() {
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    try {
      Assume.assumeTrue(vertx.isVirtualThreadAvailable());
      doTestVirtualThread(vertx);
    } finally {
      vertx.close();
    }
  }

  private void doTestVirtualThread(VertxInternal vertx) {
    FakeStream<Integer> stream = new FakeStream<>();
    Iterator<Integer> iterator = ReadStreamIterator.iterator(stream);
    ContextInternal ctx = vertx.createVirtualThreadContext();
    AtomicInteger seq = new AtomicInteger();
    ctx.runOnContext(v1 -> {
      ctx.runOnContext(v2 -> {
        assertEquals(0, seq.getAndIncrement());
        stream.write(0);
      });
      assertEquals(0, seq.get());
      iterator.next();
      assertEquals(1, seq.getAndIncrement());
      testComplete();
    });
    await();
  }
}
