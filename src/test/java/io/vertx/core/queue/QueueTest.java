/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.queue;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class QueueTest extends VertxTestBase {

  interface Mode {
    <T> Queue<T> queue();
    <T> Queue<T> queue(int highWaterMark);
    void checkThread();
  }

  private Mode SYNC, ASYNC;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    SYNC = new Mode() {
      final Thread t = Thread.currentThread();
      @Override
      public <T> Queue<T> queue() {
        return Queue.queue();
      }
      @Override
      public <T> Queue<T> queue(int highWaterMark) {
        return Queue.queue(highWaterMark);
      }
      @Override
      public void checkThread() {
        assertSame(t, Thread.currentThread());
      }
    };
    ASYNC = new Mode() {
      final Context ctx = vertx.getOrCreateContext();
      @Override
      public <T> Queue<T> queue() {
        return Queue.queue(ctx);
      }
      @Override
      public <T> Queue<T> queue(int highWaterMark) {
        return Queue.queue(ctx, highWaterMark);
      }
      @Override
      public void checkThread() {
        assertEquals(ctx, Vertx.currentContext());
      }
    };
  }

  @Test
  public void testFlowingSync() {
    testFlowing(SYNC);
  }

  @Test
  public void testFlowingAsync() {
    testFlowing(ASYNC);
  }

  private void testFlowing(Mode m) {
    Queue<String> queue = m.queue();
    AtomicInteger events = new AtomicInteger();
    queue.handler(s -> {
      m.checkThread();
      assertEquals(0, events.getAndIncrement());
      testComplete();
    });
    assertTrue(queue.add("0"));
    await();
  }

  @Test
  public void testTakeSync() {
    testTake(SYNC);
  }

  @Test
  public void testTakeAsync() {
    testTake(ASYNC);
  }

  private void testTake(Mode m) {
    Queue<String> queue = m.queue();
    AtomicInteger events = new AtomicInteger();
    queue.handler(s -> {
      m.checkThread();
      assertEquals(0, events.getAndIncrement());
      testComplete();
    });
    queue.pause();
    queue.take(1);
    assertTrue(queue.add("0"));
    await();
  }

  @Test
  public void testFlowingAddSync() {
    testFlowingAdd(SYNC);
  }

  @Test
  public void testFlowingAddAsync() {
    testFlowingAdd(ASYNC);
  }

  private void testFlowingAdd(Mode m) {
    Queue<String> queue = m.queue();
    AtomicInteger events = new AtomicInteger();
    queue.handler(s -> {
      m.checkThread();
      events.getAndIncrement();
    });
    assertTrue(queue.add("0"));
    waitUntilEquals(1, events::get);
    assertTrue(queue.add("1"));
    waitUntilEquals(2, events::get);
  }

  @Test
  public void testFlowingRefillSync() {
    testFlowingRefill(SYNC);
  }

  @Test
  public void testFlowingRefillAsync() {
    testFlowingRefill(ASYNC);
  }

  private void testFlowingRefill(Mode m) {
    Queue<String> queue = m.queue(4);
    AtomicInteger events = new AtomicInteger();
    queue.handler(s -> {
      m.checkThread();
      events.getAndIncrement();
    });
    AtomicInteger reads = new AtomicInteger();
    queue.writableHandler(v -> {
      m.checkThread();
      reads.incrementAndGet();
    });
    queue.pause();
    for (int i = 0;i < 8;i++) {
      assertEquals(i < 3, queue.add("" + i));
    }
    queue.resume();
    waitUntilEquals(1, reads::get);
    waitUntilEquals(8, events::get);
    assertTrue(reads.get() > 0);
  }

  @Test
  public void testPauseWhenFullSync() {
    testPauseWhenFull(SYNC);
  }

  @Test
  public void testPauseWhenFullAsync() {
    testPauseWhenFull(ASYNC);
  }

  private void testPauseWhenFull(Mode m) {
    Queue<String> queue = m.queue(4);
    AtomicInteger events = new AtomicInteger();
    queue.handler(s -> {
      m.checkThread();
      events.getAndIncrement();
    });
    AtomicInteger reads = new AtomicInteger();
    queue.writableHandler(v -> {
      m.checkThread();
      assertEquals(0, reads.getAndIncrement());
    });
    queue.pause();
    for (int i = 0; i < 5;i++) {
      assertEquals(i < 3, queue.add("" + i));
    }
    queue.take(1);
    assertEquals(0, reads.get());
    waitUntilEquals(1, events::get);
  }

  @Test
  public void testPausedResumeSync() {
    testPausedResume(SYNC);
  }

  @Test
  public void testPausedResumeAsync() {
    testPausedResume(ASYNC);
  }

  private void testPausedResume(Mode m) {
    Queue<String> queue = m.queue(4);
    AtomicInteger events = new AtomicInteger();
    queue.handler(s -> {
      m.checkThread();
      events.getAndIncrement();
    });
    AtomicInteger reads = new AtomicInteger();
    queue.writableHandler(v -> {
      m.checkThread();
      assertEquals(0, reads.getAndIncrement());
      assertEquals(4, events.get());
      testComplete();
    });
    queue.pause();
    for (int i = 0; i < 4;i++) {
      queue.add("" + i);
    }
    queue.resume();
    await();
  }

  @Test
  public void testPausedDrainSync() {
    testPausedDrain(SYNC);
  }

  @Test
  public void testPausedDrainAsync() {
    testPausedDrain(ASYNC);
  }

  private void testPausedDrain(Mode m) {
    Queue<String> queue = m.queue(4);
    AtomicInteger events = new AtomicInteger();
    queue.handler(s -> {
      m.checkThread();
      events.getAndIncrement();
    });
    AtomicInteger reads = new AtomicInteger();
    queue.writableHandler(v -> {
      m.checkThread();
      assertEquals(0, reads.getAndIncrement());
    });
    queue.pause();
    for (int i = 0; i < 4;i++) {
      assertEquals(i < 3, queue.add("" + i));
    }
    assertEquals(0, reads.get());
    assertEquals(0, events.get());
    for (int i = 0;i < 4;i++) {
      queue.take(1);
      waitUntilEquals(1 + i, events::get);
      if (i < 3) {
        assertEquals(0, reads.get());
      } else {
        waitUntilEquals(1, reads::get);
      }
    }
    assertEquals(4, events.get());
  }

  @Test
  public void testPausedRequestLimitedSync() {
    testPausedRequestLimited(SYNC);
  }

  @Test
  public void testPausedRequestLimitedAsync() {
    testPausedRequestLimited(ASYNC);
  }

  private void testPausedRequestLimited(Mode m) {
    Queue<String> queue = m.queue(2);
    AtomicInteger events = new AtomicInteger();
    queue.handler(s -> {
      m.checkThread();
      events.getAndIncrement();
    });
    AtomicInteger reads = new AtomicInteger();
    queue.writableHandler(v -> {
      m.checkThread();
      assertEquals(0, reads.getAndIncrement());
    });
    queue.pause();
    queue.take(1);
    assertEquals(0, reads.get());
    assertEquals(0, events.get());
    assertTrue(queue.add("0"));
    assertEquals(0, reads.get());
    waitUntilEquals(1, events::get);
    assertTrue(queue.add("1"));
    assertEquals(0, reads.get());
    assertEquals(1, events.get());
    assertFalse(queue.add("2"));
    assertEquals(0, reads.get());
    assertEquals(1, events.get());
  }

  @Test
  public void testPushReturnsTrueUntilHighWatermark() {
    Queue<String> queue = Queue.queue(0);
    AtomicInteger reads = new AtomicInteger();
    queue.writableHandler(v -> {
      assertEquals(0, reads.getAndIncrement());
    });
    queue.pause();
    queue.take(1);
    assertTrue(queue.add("0"));
    assertFalse(queue.add("1"));
  }

  @Test
  public void testHighWaterMark() {
    Queue<String> queue = Queue.queue(4);
    queue.pause();
    int count = 0;
    while (true) {
      if (!queue.add("" + count++)) {
        break;
      }
    }
    assertEquals(4, count);
  }

  @Test
  public void testProduceFasterThanConsumeAsync() throws Exception {
    Queue<Integer> queue = Queue.queue(vertx.getOrCreateContext(), 4);
    queue.writableHandler(v -> {});
    List<Integer> result = Collections.synchronizedList(new ArrayList<>());
    queue.handler(result::add);
    int count = 0;
    int extra = 50;
    while (true) {
      boolean full = !queue.add(count++);
      if (full) {
        if (extra-- == 0) {
          break;
        }
      }
      // This should produce a fair amount of items and the queue should be quickly full - we use it as safety net
      if (count > 100_000) {
        fail();
      }
    }
    waitUntilEquals(count, result::size);
  }

  @Test
  public void testEmptyHandlerSync() {
    testEmptyHandler(SYNC);
  }

  @Test
  public void testEmptyHandlerAsync() {
    testEmptyHandler(ASYNC);
  }

  private void testEmptyHandler(Mode mode) {
    Queue<String> queue = mode.queue(4);
    AtomicInteger emptyCount = new AtomicInteger();
    AtomicInteger itemCount = new AtomicInteger();
    queue.handler(item -> itemCount.incrementAndGet());
    queue.emptyHandler(v -> {
      emptyCount.incrementAndGet();
    });
    assertTrue(queue.add("0"));
    waitUntilEquals(1, itemCount::get);
    queue.pause();
    assertTrue(queue.add("1"));
    assertTrue(queue.add("2"));
    assertTrue(queue.add("3"));
    assertEquals(1, itemCount.get());
    assertFalse(queue.isEmpty());
    for (int i = 0;i < 3;i++) {
      assertEquals(0, emptyCount.get());
      queue.take(1);
      waitUntilEquals(2 + i, itemCount::get);
    }
    waitUntilEquals(1, emptyCount::get);
  }

  @Test
  public void testPushWhenHandlingLastItemSync() {
    testPushWhenHandlingLastItem(SYNC);
  }

  private void testPushWhenHandlingLastItem(Mode mode) {
    Queue<String> queue = mode.queue(4);
    queue.pause();
    int count = 0;
    while (queue.add("" + count++)) {
      //
    }
    int next = count;
    AtomicInteger received = new AtomicInteger(count);
    queue.handler(s -> {
      if (received.decrementAndGet() == 0) {
        queue.add("" + next);
      }
    });
    AtomicInteger writable = new AtomicInteger();
    queue.writableHandler(v -> {
      writable.incrementAndGet();
    });
    queue.take(count);
    assertEquals(0, writable.get());
  }

  @Test
  public void testPushAllPausedSync() {
    testPushAllWhenPaused(SYNC);
  }

  @Test
  public void testPushAllPausedAsync() {
    testPushAllWhenPaused(ASYNC);
  }

  private void testPushAllWhenPaused(Mode mode) {
    Queue<String> queue = mode.queue(4);
    AtomicInteger itemCount = new AtomicInteger();
    queue.handler(item -> itemCount.incrementAndGet());
    AtomicInteger emptyCount = new AtomicInteger();
    queue.emptyHandler(v -> emptyCount.incrementAndGet());
    AtomicInteger readCount = new AtomicInteger();
    queue.writableHandler(v -> readCount.incrementAndGet());
    queue.pause();
    assertFalse(queue.addAll(Arrays.asList("0", "1", "2", "3")));
    for (int i = 0;i < 4;i++) {
      assertEquals(0, readCount.get());
      assertEquals(0, emptyCount.get());
      waitUntilEquals(i, itemCount::get);
      queue.take(1);
    }
    waitUntilEquals(1, readCount::get);
    waitUntilEquals(1, emptyCount::get);
    waitUntilEquals(4, itemCount::get);
  }

  @Test
  public void testPushAllWhenFlowingSync() {
    testPushAllWhenFlowing(SYNC);
  }

  @Test
  public void testPushAllWhenFlowingAsync() {
    testPushAllWhenFlowing(ASYNC);
  }

  private void testPushAllWhenFlowing(Mode mode) {
    Queue<String> queue = mode.queue(4);
    AtomicInteger itemCount = new AtomicInteger();
    queue.handler(item -> itemCount.incrementAndGet());
    AtomicInteger emptyCount = new AtomicInteger();
    queue.emptyHandler(v -> emptyCount.incrementAndGet());
    AtomicInteger readCount = new AtomicInteger();
    queue.writableHandler(v -> readCount.incrementAndGet());
    assertFalse(queue.addAll(Arrays.asList("0", "1", "2", "3")));
    waitUntilEquals(1, readCount::get);
    waitUntilEquals(1, emptyCount::get);
    waitUntilEquals(4, itemCount::get);
  }

  @Test
  public void testMaxElementsPerTick() {
    Context ctx = vertx.getOrCreateContext();
    Queue<String> queue = Queue.queue(ctx, new QueueOptions().setMaxElementsPerTick(16));
    queue.pause();
    for (int i = 0;i < 100;i++) {
      queue.add("" + i);
    }
    AtomicInteger count = new AtomicInteger();
    AtomicInteger inTask = new AtomicInteger();
    queue.handler(val -> {
      if (inTask.incrementAndGet() == 1) {
        ctx.runOnContext(v -> {
          assertTrue("Was expecting to have less than " + inTask.get() + "", inTask.get() < 17);
          inTask.set(0);
        });
      }
      count.incrementAndGet();
    });
    queue.resume();
    waitUntilEquals(100, count::get);
  }

  @Test
  public void testAddWhenDeliveringSync() {
    testAddWhenDelivering(SYNC);
  }

  @Test
  public void testAddWhenDeliveringAsync() {
    testAddWhenDelivering(ASYNC);
  }

  private void testAddWhenDelivering(Mode mode) {
    Queue<Integer> queue = mode.queue(4);
    AtomicInteger count = new AtomicInteger();
    queue.handler(elt -> {
      count.incrementAndGet();
      if (elt == 0) {
        queue.add(1);
        assertEquals(1, count.get());
      }
    });
    queue.add(0);
    waitUntilEquals(2, count::get);
  }

  @Test
  public void testAddAllWhenDeliveringSync() {
    testAddAllWhenDelivering(SYNC);
  }

  @Test
  public void testAddAllWhenDeliveringAsync() {
    testAddAllWhenDelivering(ASYNC);
  }

  private void testAddAllWhenDelivering(Mode mode) {
    Queue<Integer> queue = mode.queue(4);
    List<Integer> consumed = Collections.synchronizedList(new ArrayList<>());
    queue.handler(elt -> {
      consumed.add(elt);
      if (elt == 2) {
        queue.addAll(Arrays.asList(4, 5));
        assertEquals(Arrays.asList(0, 1, 2), consumed);
      }
    });
    queue.addAll(Arrays.asList(0, 1, 2, 3));
    waitUntilEquals(Arrays.asList(0, 1, 2, 3, 4, 5), () -> new ArrayList<>(consumed));
  }

  @Test
  public void testPollAll() {
    Queue<Integer> queue = Queue.queue(4);
    AtomicInteger resumes = new AtomicInteger();
    queue.writableHandler(v -> resumes.incrementAndGet());
    queue.handler(elt -> fail());
    queue.pause();
    assertFalse(queue.addAll(Arrays.asList(0, 1, 2, 3)));
    List<Integer> res = new ArrayList<>();
    queue.pollAll().forEach(res::add);
    assertEquals(Arrays.asList(0, 1, 2, 3), res);
    queue.resume();
    waitUntilEquals(1, resumes::get);
  }

  @Test
  public void testPollAllWhenEmittingSync() {
    testPollAllWhenEmitting(SYNC);
  }

  @Test
  public void testPollAllWhenEmittingAsync() {
    testPollAllWhenEmitting(ASYNC);
  }

  private void testPollAllWhenEmitting(Mode mode) {
    Queue<Integer> queue = mode.queue(4);
    List<Integer> consumed = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger resumes = new AtomicInteger();
    queue.writableHandler(v -> resumes.incrementAndGet());
    queue.handler(elt -> {
      assertEquals((int)elt, 0);
      consumed.add(elt);
      List<Integer> res = new ArrayList<>();
      queue.pollAll().forEach(res::add);
      assertEquals(Arrays.asList(1, 2, 3), res);
      assertEquals(0, resumes.get());
    });
    queue.pause();
    queue.addAll(Arrays.asList(0, 1, 2, 3));
    queue.resume();
    waitUntilEquals(Collections.singletonList(0), () -> new ArrayList<>(consumed));
    waitUntilEquals(1, resumes::get);
  }

  @Test
  public void testPollWhenEmittingSync() {
    testPollWhenEmitting(SYNC);
  }

  @Test
  public void testPollWhenEmittingAsync() {
    testPollWhenEmitting(ASYNC);
  }

  private void testPollWhenEmitting(Mode mode) {
    Queue<Integer> queue = mode.queue(4);
    List<Integer> consumed = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger resumes = new AtomicInteger();
    queue.writableHandler(v -> resumes.incrementAndGet());
    queue.handler(elt -> {
      consumed.add(elt);
      if (elt == 2) {
        assertEquals(3, (int)queue.poll());
        assertEquals(0, resumes.get());
      } else {
        assertTrue(elt < 2);
      }
    });
    queue.pause();
    queue.addAll(Arrays.asList(0, 1, 2, 3));
    queue.resume();
    waitUntilEquals(Arrays.asList(0, 1, 2), () -> new ArrayList<>(consumed));
    waitUntilEquals(1, resumes::get);
  }

  @Test
  public void testClearWhenEmittingSync() {
    testClearWhenEmitting(SYNC);
  }

  @Test
  public void testClearWhenEmittingAsync() {
    testClearWhenEmitting(ASYNC);
  }

  private void testClearWhenEmitting(Mode mode) {
    Queue<Integer> queue = mode.queue(4);
    List<Integer> consumed = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger resumes = new AtomicInteger();
    queue.writableHandler(v -> resumes.incrementAndGet());
    queue.handler(elt -> {
      assertEquals((int)elt, 0);
      consumed.add(elt);
      queue.clear();
      assertEquals(0, resumes.get());
    });
    queue.pause();
    queue.addAll(Arrays.asList(0, 1, 2, 3));
    queue.resume();
    waitUntilEquals(Collections.singletonList(0), () -> new ArrayList<>(consumed));
    // We get a resume but it's a side effect of clearing the queue
    waitUntilEquals(1, resumes::get);
  }
}
