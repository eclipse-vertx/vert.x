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

import io.vertx.core.internal.streams.ReadStreamIterator;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.fakestream.FakeStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CyclicBarrier;

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

  @Test
  public void testConcurrentReads() throws Exception {
    // While the iterator should not be used concurrently because of hasNext()/next() races
    // calling next() from multiple thread is possible
    FakeStream<Integer> stream = new FakeStream<>();
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
      consumer.join();
      list.addAll(consumer.consumed);
    }
    assertEquals(list.size(), numElements);
  }
}
