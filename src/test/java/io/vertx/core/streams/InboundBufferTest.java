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
package io.vertx.core.streams;

import io.vertx.core.Context;
import io.vertx.core.streams.impl.InboundBuffer;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class InboundBufferTest extends VertxTestBase {

  private volatile Runnable contextChecker;
  private Context context;
  private InboundBuffer<Integer> buffer;
  private AtomicInteger sequence;

  private boolean emit() {
    return buffer.write(sequence.getAndIncrement());
  }

  private boolean emit(int count) {
    List<Integer> list = new ArrayList<>(count);
    for (int i = 0;i < count;i++) {
      list.add(sequence.getAndIncrement());
    }
    return buffer.write(list);
  }

  private void fill() {
    while (emit()) {
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    context = vertx.getOrCreateContext();
    sequence = new AtomicInteger();
    context.runOnContext(v -> {
      Thread contextThread = Thread.currentThread();
      contextChecker = () -> {
        assertSame(contextThread, Thread.currentThread());
      };
    });
    waitUntil(() -> contextChecker != null);
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  private void checkContext() {
    contextChecker.run();
  }

  @Test
  public void testFlowing() {
    context.runOnContext(v -> {
      buffer = new InboundBuffer<>(context);
      AtomicInteger events = new AtomicInteger();
      buffer.handler(elt -> {
        checkContext();
        assertEquals(0, (int)elt);
        assertEquals(0, events.getAndIncrement());
        testComplete();
      });
      assertTrue(emit());
    });
    await();
  }

  @Test
  public void testTake() {
    context.runOnContext(v -> {
      buffer = new InboundBuffer<>(context);
      AtomicInteger events = new AtomicInteger();
      buffer.handler(elt -> {
        checkContext();
        assertEquals(0, (int)elt);
        assertEquals(0, events.getAndIncrement());
        testComplete();
      });
      buffer.pause();
      buffer.fetch(1);
      assertTrue(emit());
    });
    await();
  }

  @Test
  public void testFlowingAdd() {
    context.runOnContext(v -> {
      buffer = new InboundBuffer<>(context);
      AtomicInteger events = new AtomicInteger();
      buffer.handler(elt -> {
        checkContext();
        events.getAndIncrement();
      });
      assertTrue(emit());
      assertEquals(1, events.get());
      assertTrue(emit());
      assertEquals(2, events.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testFlowingRefill() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 5L);
      AtomicInteger events = new AtomicInteger();
      buffer.handler(s -> {
        checkContext();
        events.getAndIncrement();
      });
      buffer.drainHandler(v2 -> {
        checkContext();
        assertEquals(8, events.get());
        testComplete();
      });
      buffer.pause();
      for (int i = 0;i < 8;i++) {
        assertEquals("Expected " + i + " to be bilto", i < 4, emit());
      }
      buffer.resume();
    });
    await();
  }

  @Test
  public void testPauseWhenFull() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 5L);
      AtomicInteger events = new AtomicInteger();
      AtomicInteger reads = new AtomicInteger();
      buffer.drainHandler(v2 -> {
        checkContext();
        assertEquals(0, reads.getAndIncrement());
      });
      buffer.handler(s -> {
        checkContext();
        assertEquals(0, reads.get());
        assertEquals(0, events.getAndIncrement());
        testComplete();
      });
      buffer.pause();
      for (int i = 0; i < 5;i++) {
        assertEquals(i < 4, emit());
      }
      buffer.fetch(1);
    });
    await();
  }

  @Test
  public void testPausedResume() {
    context.runOnContext(v -> {
      buffer = new InboundBuffer<>(context, 5L);
      AtomicInteger events = new AtomicInteger();
      buffer.handler(s -> {
        checkContext();
        events.getAndIncrement();
      });
      AtomicInteger reads = new AtomicInteger();
      buffer.drainHandler(v2 -> {
        checkContext();
        assertEquals(0, reads.getAndIncrement());
        assertEquals(5, events.get());
        testComplete();
      });
      buffer.pause();
      fill();
      buffer.resume();
    });
    await();
  }

  @Test
  public void testPausedDrain() {
    waitFor(2);
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 5L);
      AtomicInteger drained = new AtomicInteger();
      AtomicInteger emitted = new AtomicInteger();
      buffer.drainHandler(v2 -> {
        checkContext();
        assertEquals(0, drained.getAndIncrement());
        assertEquals(5, emitted.get());
        complete();
      });
      buffer.handler(s -> {
        checkContext();
        assertEquals(0, drained.get());
        emitted.getAndIncrement();
      });
      buffer.pause();
      fill();
      assertEquals(0, drained.get());
      assertEquals(0, emitted.get());
      buffer.resume();
      complete();
    });
    await();
  }

  @Test
  public void testPausedRequestLimited() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 3L);
      AtomicInteger events = new AtomicInteger();
      buffer.handler(s -> {
        checkContext();
        events.getAndIncrement();
      });
      AtomicInteger reads = new AtomicInteger();
      buffer.drainHandler(v2 -> {
        checkContext();
        assertEquals(0, reads.getAndIncrement());
      });
      buffer.pause();
      buffer.fetch(1);
      assertEquals(0, reads.get());
      assertEquals(0, events.get());
      assertTrue(emit());
      assertEquals(0, reads.get());
      waitUntilEquals(1, events::get);
      assertTrue(emit());
      assertEquals(0, reads.get());
      assertEquals(1, events.get());
      assertTrue(emit());
      assertEquals(0, reads.get());
      assertEquals(1, events.get());
      assertFalse(emit());
      assertEquals(0, reads.get());
      assertEquals(1, events.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testPushReturnsTrueUntilHighWatermark() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 2L);
      buffer.pause();
      buffer.fetch(1);
      assertTrue(emit());
      assertTrue(emit());
      assertFalse(emit());
      testComplete();
    });
    await();
  }

  @Test
  public void testHighWaterMark() {
    context.runOnContext(v -> {
      buffer = new InboundBuffer<>(context, 5L);
      buffer.pause();
      fill();
      assertEquals(5, sequence.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testEmptyHandler() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 4L);
      AtomicInteger emptyCount = new AtomicInteger();
      AtomicInteger itemCount = new AtomicInteger();
      buffer.handler(item -> itemCount.incrementAndGet());
      buffer.emptyHandler(v2 -> {
        assertEquals(0, emptyCount.getAndIncrement());
        testComplete();
      });
      assertTrue(emit());
      assertEquals(1, itemCount.get());
      buffer.pause();
      assertTrue(emit());
      assertTrue(emit());
      assertTrue(emit());
      assertEquals(1, itemCount.get());
      assertFalse(buffer.isEmpty());
      for (int i = 0;i < 3;i++) {
        assertEquals(0, emptyCount.get());
        buffer.fetch(1);
      }
    });
    await();
  }

  @Test
  public void testEmitWhenHandlingLastItem() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 4L);
      buffer.pause();
      fill();
      int next = sequence.get();
      AtomicInteger received = new AtomicInteger(next);
      buffer.handler(s -> {
        if (received.decrementAndGet() == 0) {
          buffer.write(next);
        }
      });
      AtomicInteger writable = new AtomicInteger();
      buffer.drainHandler(v -> {
        writable.incrementAndGet();
      });
      buffer.fetch(sequence.get());
      assertEquals(0, writable.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testEmitInElementHandler() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 5L);
      AtomicInteger events = new AtomicInteger();
      AtomicBoolean receiving = new AtomicBoolean();
      buffer.handler(s -> {
        checkContext();
        assertFalse(receiving.getAndSet(true));
        events.incrementAndGet();
        if (s == 0) {
          fill();
        }
        receiving.set(false);
      });
      buffer.pause();
      buffer.fetch(1);
      assertFalse(emit());
      assertEquals(5, buffer.size());
      assertEquals(1, events.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testEmitInElementHandler1() {
    testEmitInElementHandler(n -> {
      assertFalse(emit(n));
    });
  }

  @Test
  public void testEmitInElementHandler2() {
    testEmitInElementHandler(n -> {
      for (int i = 0;i < n - 1;i++) {
        assertTrue(emit());
      }
      assertFalse(emit());
    });
  }

  private void testEmitInElementHandler(Consumer<Integer> emit) {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 5L);
      AtomicInteger events = new AtomicInteger();
      AtomicInteger drained = new AtomicInteger();
      AtomicBoolean draining = new AtomicBoolean();
      buffer.drainHandler(v -> {
        // Check reentrancy
        assertFalse(draining.get());
        draining.set(true);
        assertEquals(0, drained.getAndIncrement());
        assertFalse(emit());
        draining.set(false);
      });
      buffer.handler(s -> {
        checkContext();
        switch (s) {
          case 5:
            // Emitted in drain handler
            emit.accept(9);
            break;
          case 9:
            vertx.runOnContext(v -> {
              assertEquals(1, drained.get());
              assertEquals(10, events.get());
              assertEquals(5, buffer.size());
              testComplete();
            });
            break;
        }
        events.incrementAndGet();
      });
      buffer.pause();
      fill();
      buffer.fetch(10);
    });
    await();
  }

  @Test
  public void testEmitInDrainHandler1() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 4L);
      AtomicInteger drained = new AtomicInteger();
      AtomicInteger expectedDrained = new AtomicInteger();
      buffer.drainHandler(v2 -> {
        switch (drained.getAndIncrement()) {
          case 0:
            // Check that emitting again will not drain again
            expectedDrained.set(1);
            fill();
            context.runOnContext(v -> {
              assertEquals(1, drained.get());
              testComplete();
            });
            break;
        }
      });
      buffer.handler(val -> {
        if (val == 0) {
          // This will set writable to false
          fill();
        }
        assertEquals(expectedDrained.get(), drained.get());
      });
      buffer.pause();
      buffer.fetch(1);
      emit();
      buffer.fetch(4L);
    });
    await();
  }


  @Test
  public void testEmitInDrainHandler2() {
    waitFor(2);
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 5L);
      AtomicInteger drained = new AtomicInteger();
      AtomicBoolean draining = new AtomicBoolean();
      AtomicInteger emitted = new AtomicInteger();
      buffer.drainHandler(v2 -> {
        assertFalse(draining.get());
        draining.set(true);
        switch (drained.getAndIncrement()) {
          case 0:
            // This will trigger a new asynchronous drain
            fill();
            buffer.fetch(5);
            break;
          case 1:
            assertEquals(10, emitted.get());
            complete();
            break;
        }
        draining.set(false);
      });
      buffer.handler(val -> {
        emitted.incrementAndGet();
        if (val == 0) {
          assertEquals(0, drained.get());
        } else if (val == 6) {
          assertEquals(1, drained.get());
        }
      });
      buffer.pause();
      fill();
      buffer.fetch(5);
      complete();
    });
    await();
  }

  @Test
  public void testDrainAfter() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 5L);
      AtomicInteger events = new AtomicInteger();
      AtomicBoolean receiving = new AtomicBoolean();
      buffer.handler(s -> {
        checkContext();
        assertFalse(receiving.getAndSet(true));
        events.incrementAndGet();
        if (s == 0) {
          emit(5);
        }
        receiving.set(false);
      });
      assertTrue(emit());
      assertEquals(6, sequence.get());
      assertEquals(6, events.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testPauseInElementHandler() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 5L);
      AtomicInteger events = new AtomicInteger();
      buffer.handler(s -> {
        events.incrementAndGet();
        if (s == 0) {
          buffer.pause();
          fill();
        }
      });
      assertFalse(emit());
      assertEquals(1, events.get());
      assertEquals(5, buffer.size());
      testComplete();
    });
    await();
  }

  @Test
  public void testAddAllEmitInHandler() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 4L);
      List<Integer> emitted = new ArrayList<>();
      buffer.handler(elt -> {
        switch (elt) {
          case 0:
            emit();
        }
        emitted.add(elt);
      });
      assertTrue(emit(3));
      assertEquals(Arrays.asList(0, 1, 2, 3), emitted);
      testComplete();
    });
    await();
  }

  @Test
  public void testAddAllWhenPaused() {
    waitFor(3);
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 4L);
      AtomicInteger emitted = new AtomicInteger();
      AtomicInteger emptied = new AtomicInteger();
      AtomicInteger drained = new AtomicInteger();
      buffer.handler(item -> {
        emitted.incrementAndGet();
        assertEquals(0, drained.get());
        assertEquals(0, emptied.get());
        buffer.fetch(1);

      });
      buffer.emptyHandler(v -> {
        assertEquals(5, emitted.get());
        emptied.incrementAndGet();
        complete();
      });
      buffer.drainHandler(v -> {
        assertEquals(5, emitted.get());
        drained.incrementAndGet();
        complete();
      });
      buffer.pause();
      assertFalse(emit(5));
      buffer.fetch(1);
      complete();
    });
    await();
  }

  @Test
  public void testAddAllWhenFlowing() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 4L);
      AtomicInteger emitted = new AtomicInteger();
      AtomicInteger emptied = new AtomicInteger();
      AtomicInteger drained = new AtomicInteger();
      buffer.handler(item -> emitted.incrementAndGet());
      buffer.emptyHandler(v2 -> emptied.incrementAndGet());
      buffer.drainHandler(v2 -> drained.incrementAndGet());
      assertTrue(emit(4));
      context.runOnContext(v -> {
        waitUntilEquals(0, drained::get);
        waitUntilEquals(0, emptied::get);
        waitUntilEquals(4, emitted::get);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAddAllWhenDelivering() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 4L);
      List<Integer> emitted = new ArrayList<>();
      buffer.handler(elt -> {
        emitted.add(elt);
        if (elt == 2) {
          buffer.write(Arrays.asList(4, 5));
          // Check that we haven't re-entered the handler
          assertEquals(Arrays.asList(0, 1, 2), emitted);
        }
      });
      emit(4);
      assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), emitted);
      testComplete();
    });
    await();
  }

  @Test
  public void testPollDuringEmission() {
    waitFor(2);
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 4L);
      List<Integer> polled = new ArrayList<>();
      List<Integer> emitted = new ArrayList<>();
      AtomicInteger drained = new AtomicInteger();
      buffer.drainHandler(v -> {
        assertEquals(Arrays.asList(0, 1, 2, 3), emitted);
        assertEquals(Arrays.asList(4, 5), polled);
        complete();
      });
      buffer.handler(elt -> {
        emitted.add(elt);
        if (elt == 3) {
          Integer p;
          while ((p = buffer.read()) != null) {
            polled.add(p);
          }
          assertEquals(Arrays.asList(4, 5), polled);
          assertEquals(0, drained.get());
          complete();
        } else {
          assertTrue(elt < 3);
        }
      });
      buffer.pause();
      assertFalse(emit(6));
      buffer.resume();
    });
    await();
  }

  @Test
  public void testCheckThatPauseAfterResumeWontDoAnyEmission() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 4L);
      AtomicInteger emitted = new AtomicInteger();
      buffer.handler(elt -> emitted.incrementAndGet());
      buffer.pause();
      fill();
      // Resume will execute an asynchronous drain operation
      buffer.resume();
      // Pause just after to ensure that no elements will be delivered to he handler
      buffer.pause();
      // Give enough time to have elements delivered
      vertx.setTimer(20, id -> {
        // Check we haven't received anything
        assertEquals(0, emitted.get());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testBufferSignalingFullImmediately() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 0L);
      List<Integer> emitted = new ArrayList<>();
      buffer.drainHandler(v -> {
        assertEquals(Arrays.asList(0, 1), emitted);
        testComplete();
      });
      buffer.handler(emitted::add);
      assertTrue(emit());
      assertEquals(Collections.singletonList(0), emitted);
      buffer.pause();
      assertFalse(emit());
      buffer.resume();
    });
    await();
  }

  @Test
  public void testPauseInHandlerSignalsFullImmediately() {
    context.runOnContext(v -> {
      buffer = new InboundBuffer<>(context, 0);
      buffer.handler(elt -> {
        checkContext();
        buffer.pause();
      });
      assertFalse(emit());
      testComplete();
    });
    await();
  }

  @Test
  public void testFetchWhenNotEmittingWithNoPendingElements() {
    context.runOnContext(v1 -> {
      buffer = new InboundBuffer<>(context, 0);
      AtomicInteger drained = new AtomicInteger();
      buffer.drainHandler(v2 -> {
        context.runOnContext(v -> {
          assertEquals(0, drained.getAndIncrement());
          testComplete();
        });
      });
      buffer.emptyHandler(v -> {
        fail();
      });
      buffer.handler(elt -> {
        checkContext();
        switch (elt) {
          case 0:
            buffer.pause();
            break;
        }
      });
      assertFalse(emit());
      buffer.fetch(1);
    });
    await();
  }

  @Test
  public void testRejectWrongThread() {
    buffer = new InboundBuffer<>(context);
    try {
      buffer.write(0);
      fail();
    } catch (IllegalStateException ignore) {
    }
    try {
      buffer.write(Arrays.asList(0, 1, 2));
      fail();
    } catch (IllegalStateException ignore) {
    }
  }
}
