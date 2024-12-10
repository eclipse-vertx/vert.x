/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.concurrent;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

public class InboundMessageQueueTest extends VertxTestBase {

  private volatile Runnable contextChecker;
  private Context context;
  private TestQueue queue;
  private AtomicInteger sequence;

  class TestQueue extends InboundMessageQueue<Integer> {

    final IntConsumer consumer;
    private Handler<Void> drainHandler;
    private boolean writable;
    private int size;

    public TestQueue(IntConsumer consumer) {
      super(((ContextInternal) context).eventLoop(), ((ContextInternal) context).executor());
      this.consumer = consumer;
      this.writable = true;
    }

    public TestQueue(IntConsumer consumer, int lwm, int hwm) {
      super(((ContextInternal) context).eventLoop(), ((ContextInternal) context).executor(), lwm, hwm);
      this.consumer = consumer;
      this.writable = true;
    }

    int size() {
      return size;
    }

    @Override
    protected void handleMessage(Integer msg) {
      size--;
      consumer.accept(msg);
    }

    @Override
    protected void handleResume() {
      writable = true;
      Handler<Void> handler = drainHandler;
      if (handler != null) {
        handler.handle(null);
      }
    }

    @Override
    protected void handlePause() {
      writable = false;
    }

    final void resume() {
      fetch(Long.MAX_VALUE);
    }

    final int fill() {
      int count = 0;
      boolean drain = false;
      while (writable) {
        drain |= add(sequence.getAndIncrement());
        count++;
      }
      size += count;
      if (drain) {
        drain();
      }
      return count;
    }

    final boolean emit() {
      size++;
      write(sequence.getAndIncrement());
      return writable;
    }

    final boolean emit(int count) {
      size += count;
      boolean drain = false;
      for (int i = 0; i < count; i++) {
        drain |= add(sequence.getAndIncrement());
      }
      if (drain) {
        drain();
      }
      return writable;
    }

    TestQueue drainHandler(Handler<Void> handler) {
      this.drainHandler = handler;
      return this;
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
      AtomicInteger events = new AtomicInteger();
      queue = buffer(elt -> {
        checkContext();
        assertEquals(0, elt);
        assertEquals(0, events.getAndIncrement());
        testComplete();
      });
      assertTrue(queue.emit());
    });
    await();
  }

  private TestQueue buffer(IntConsumer consumer) {
    return new TestQueue(consumer);
  }

  private TestQueue buffer(IntConsumer consumer, int lwm, int hwm) {
    return new TestQueue(consumer, lwm, hwm);
  }

  @Test
  public void testTake() {
    context.runOnContext(v -> {
      AtomicInteger events = new AtomicInteger();
      queue = buffer(elt -> {
        checkContext();
        assertEquals(0, elt);
        assertEquals(0, events.getAndIncrement());
        testComplete();
      });
      queue.pause();
      queue.fetch(1);
      assertTrue(queue.emit());
    });
    await();
  }

  @Test
  public void testFlowingAdd() {
    context.runOnContext(v -> {
      AtomicInteger events = new AtomicInteger();
      queue = buffer(elt -> {
        checkContext();
        events.getAndIncrement();
      });
      assertTrue(queue.emit());
      assertEquals(1, events.get());
      assertTrue(queue.emit());
      assertEquals(2, events.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testFlowingRefill() {
    context.runOnContext(v1 -> {
      AtomicInteger events = new AtomicInteger();
      queue = buffer(elt -> {
        checkContext();
        events.getAndIncrement();
      }, 5, 5).drainHandler(v -> {
        checkContext();
        assertEquals(8, events.get());
        testComplete();
      });
      queue.pause();
      for (int i = 0; i < 8; i++) {
        assertEquals("Expected " + i + " to be bilto", i < 4, queue.emit());
      }
      queue.resume();
    });
    await();
  }

  @Test
  public void testPauseWhenFull() {
    context.runOnContext(v1 -> {
      AtomicInteger events = new AtomicInteger();
      AtomicInteger reads = new AtomicInteger();
      queue = buffer(elt -> {
        checkContext();
        assertEquals(0, reads.get());
        assertEquals(0, events.getAndIncrement());
        testComplete();
      }, 5, 5).drainHandler(v2 -> {
        checkContext();
        assertEquals(0, reads.getAndIncrement());
      });
      queue.pause();
      for (int i = 0; i < 5; i++) {
        assertEquals(i < 4, queue.emit());
      }
      queue.fetch(1);
    });
    await();
  }

  @Test
  public void testPausedResume() {
    context.runOnContext(v1 -> {
      AtomicInteger reads = new AtomicInteger();
      AtomicInteger events = new AtomicInteger();
      queue = buffer(elt -> {
        checkContext();
        events.getAndIncrement();
      }, 5, 5).drainHandler(v2 -> {
        checkContext();
        assertEquals(0, reads.getAndIncrement());
        assertEquals(5, events.get());
        testComplete();
      });
      queue.pause();
      queue.fill();
      queue.resume();
    });
    await();
  }

  @Test
  public void testPausedDrain() {
    waitFor(2);
    context.runOnContext(v1 -> {
      AtomicInteger drained = new AtomicInteger();
      AtomicInteger emitted = new AtomicInteger();
      queue = buffer(elt -> {
        checkContext();
        assertEquals(0, drained.get());
        emitted.getAndIncrement();
      }, 5, 5);
      queue.drainHandler(v2 -> {
        checkContext();
        assertEquals(0, drained.getAndIncrement());
        assertEquals(5, emitted.get());
        complete();
      });
      queue.pause();
      queue.fill();
      assertEquals(0, drained.get());
      assertEquals(0, emitted.get());
      queue.resume();
      complete();
    });
    await();
  }

  @Test
  public void testPausedRequestLimited() {
    context.runOnContext(v1 -> {
      AtomicInteger events = new AtomicInteger();
      AtomicInteger reads = new AtomicInteger();
      queue = buffer(elt -> {
        checkContext();
        events.getAndIncrement();
      }, 3, 3)
        .drainHandler(v2 -> {
          checkContext();
          assertEquals(0, reads.getAndIncrement());
        });
      queue.pause();
      queue.fetch(1);
      assertEquals(0, reads.get());
      assertEquals(0, events.get());
      assertTrue(queue.emit());
      assertEquals(0, reads.get());
      waitUntilEquals(1, events::get);
      assertTrue(queue.emit());
      assertEquals(0, reads.get());
      assertEquals(1, events.get());
      assertTrue(queue.emit());
      assertEquals(0, reads.get());
      assertEquals(1, events.get());
      assertFalse(queue.emit());
      assertEquals(0, reads.get());
      assertEquals(1, events.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testPushReturnsTrueUntilHighWatermark() {
    context.runOnContext(v1 -> {
      queue = buffer(elt -> {

      }, 2, 2);
      queue.pause();
      queue.fetch(1);
      assertTrue(queue.emit());
      assertTrue(queue.emit());
      assertFalse(queue.emit());
      testComplete();
    });
    await();
  }

  @Test
  public void testHighWaterMark() {
    context.runOnContext(v -> {
      queue = buffer(elt -> {
      }, 5, 5);
      queue.pause();
      queue.fill();
      assertEquals(5, sequence.get());
      testComplete();
    });
    await();
  }

  /*

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
  */
  @Test
  public void testEmitWhenHandlingLastItem() {
    context.runOnContext(v1 -> {
      int next = sequence.get();
      AtomicInteger received = new AtomicInteger(next);
      AtomicInteger writable = new AtomicInteger();
      queue = buffer(elt -> {
        if (received.decrementAndGet() == 0) {
          queue.write(next);
        }
      }, 4, 4)
        .drainHandler(v2 -> {
          writable.incrementAndGet();
        });
      queue.pause();
      queue.fill();
      queue.fetch(sequence.get());
      assertEquals(0, writable.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testEmitInElementHandler() {
    context.runOnContext(v1 -> {
      AtomicInteger events = new AtomicInteger();
      AtomicBoolean receiving = new AtomicBoolean();
      queue = buffer(elt -> {
        checkContext();
        assertFalse(receiving.getAndSet(true));
        events.incrementAndGet();
        if (elt == 0) {
          queue.fill();
        }
        receiving.set(false);
      }, 5, 5);
      queue.pause();
      queue.fetch(1);
      assertFalse(queue.emit());
      assertEquals(5 - 1, queue.size());
      assertEquals(1, events.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testEmitInElementHandler1() {
    testEmitInElementHandler(n -> {
      assertFalse(queue.emit(n));
    });
  }

/*
  @Test
  public void testEmitInElementHandler2() {
    testEmitInElementHandler(n -> {
      for (int i = 0;i < n - 1;i++) {
        assertTrue(buffer.emit());
      }
      assertFalse(buffer.emit());
    });
  }
*/

  private void testEmitInElementHandler(IntConsumer emit) {
    context.runOnContext(v1 -> {
      AtomicInteger events = new AtomicInteger();
      AtomicInteger drained = new AtomicInteger();
      AtomicBoolean draining = new AtomicBoolean();
      queue = buffer(elt -> {
        checkContext();
        switch (elt) {
          case 5:
            // Emitted in drain handler
            emit.accept(9);
            break;
          case 9:
            vertx.runOnContext(v2 -> {
              assertEquals(1, drained.get());
              assertEquals(10, events.get());
              assertEquals(5, queue.size());
              testComplete();
            });
            break;
        }
        events.incrementAndGet();
      }, 5, 5);
      queue.drainHandler(v3 -> {
        // Check reentrancy
        assertFalse(draining.get());
        draining.set(true);
        assertEquals(0, drained.getAndIncrement());
        assertFalse(queue.emit());
        draining.set(false);
      });
      queue.pause();
      queue.fill();
      queue.fetch(10);
    });
    await();
  }

  @Test
  public void testEmitInDrainHandler1() {
    context.runOnContext(v1 -> {
      AtomicInteger drained = new AtomicInteger();
      AtomicInteger expectedDrained = new AtomicInteger();
      queue = buffer(elt -> {
        if (elt == 0) {
          // This will set writable to false
          queue.fill();
        }
        assertEquals(expectedDrained.get(), drained.get());
      }, 4, 4)
        .drainHandler(v2 -> {
          switch (drained.getAndIncrement()) {
            case 0:
              // Check that emitting again will not drain again
              expectedDrained.set(1);
              queue.fill();
              assertEquals(1, drained.get());
              testComplete();
              break;
          }
        });
      queue.pause();
      queue.fetch(1);
      queue.emit();
      queue.fetch(4L);
    });
    await();
  }

  @Test
  public void testEmitInDrainHandler2() {
    waitFor(2);
    context.runOnContext(v1 -> {
      AtomicInteger drained = new AtomicInteger();
      AtomicBoolean draining = new AtomicBoolean();
      AtomicInteger emitted = new AtomicInteger();
      queue = buffer(elt -> {
        emitted.incrementAndGet();
        if (elt == 0) {
          assertEquals(0, drained.get());
        } else if (elt == 6) {
          assertEquals(1, drained.get());
        }
      }, 5, 5)
        .drainHandler(v2 -> {
          assertFalse(draining.get());
          draining.set(true);
          switch (drained.getAndIncrement()) {
            case 0:
              // This will trigger a new asynchronous drain
              queue.fill();
              queue.fetch(5);
              break;
            case 1:
              assertEquals(10, emitted.get());
              complete();
              break;
          }
          draining.set(false);
        });
      queue.pause();
      queue.fill();
      queue.fetch(5);
      complete();
    });
    await();
  }

  @Test
  public void testDrainAfter() {
    context.runOnContext(v1 -> {
      AtomicInteger events = new AtomicInteger();
      AtomicBoolean receiving = new AtomicBoolean();
      queue = buffer(elt -> {
        checkContext();
        assertFalse(receiving.getAndSet(true));
        events.incrementAndGet();
        if (elt == 0) {
          queue.emit(5);
        }
        receiving.set(false);
      }, 5, 5);
      assertFalse(queue.emit());
      assertEquals(6, sequence.get());
      assertEquals(6, events.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testPauseInElementHandler() {
    context.runOnContext(v1 -> {
      AtomicInteger events = new AtomicInteger();
      queue = buffer(elt -> {
        events.incrementAndGet();
        if (elt == 0) {
          queue.pause();
          queue.fill();
        }
      }, 5, 5);
      assertFalse(queue.emit());
      assertEquals(1, events.get());
      assertEquals(5 - 1, queue.size());
      testComplete();
    });
    await();
  }

  @Test
  public void testAddAllEmitInHandler() {
    context.runOnContext(v1 -> {
      List<Integer> emitted = new ArrayList<>();
      queue = buffer(elt -> {
        switch (elt) {
          case 0:
            queue.emit();
        }
        emitted.add(elt);
      }, 4, 4);
      assertFalse(queue.emit(3));
      assertEquals(Arrays.asList(0, 1, 2, 3), emitted);
      testComplete();
    });
    await();
  }

  @Test
  public void testAddAllWhenPaused() {
    waitFor(2);
    context.runOnContext(v1 -> {
      AtomicInteger emitted = new AtomicInteger();
      AtomicInteger emptied = new AtomicInteger();
      AtomicInteger drained = new AtomicInteger();
      queue = buffer(elt -> {
        emitted.incrementAndGet();
        assertEquals(0, drained.get());
        assertEquals(0, emptied.get());
        queue.fetch(1);
      }, 4, 4)
        .drainHandler(v2 -> {
          assertEquals(5, emitted.get());
          drained.incrementAndGet();
          complete();
        });
      queue.pause();
      assertFalse(queue.emit(5));
      queue.fetch(1);
      complete();
    });
    await();
  }

  @Test
  public void testAddAllWhenFlowing() {
    context.runOnContext(v1 -> {
      AtomicInteger emitted = new AtomicInteger();
      AtomicInteger drained = new AtomicInteger();
      queue = buffer(elt -> {
        emitted.incrementAndGet();
      }, 4, 4)
        .drainHandler(v2 -> drained.incrementAndGet());
      assertFalse(queue.emit(4));
      context.runOnContext(v -> {
        waitUntilEquals(1, drained::get);
        waitUntilEquals(4, emitted::get);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAddAllWhenDelivering() {
    context.runOnContext(v1 -> {
      List<Integer> emitted = new ArrayList<>();
      queue = buffer(elt -> {
        emitted.add(elt);
        if (elt == 2) {
          queue.write(Arrays.asList(4, 5));
          // Check that we haven't re-entered the handler
          assertEquals(Arrays.asList(0, 1, 2), emitted);
        }
      }, 4, 4);
      queue.emit(4);
      assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), emitted);
      testComplete();
    });
    await();
  }

  @Test
  public void testCheckThatPauseAfterResumeWontDoAnyEmission() {
    context.runOnContext(v1 -> {
      AtomicInteger emitted = new AtomicInteger();
      queue = buffer(elt -> emitted.incrementAndGet(), 4, 4);
      queue.pause();
      queue.fill();
      // Resume will execute an asynchronous drain operation
      queue.resume();
      // Pause just after to ensure that no elements will be delivered to he handler
      queue.pause();
      // Give enough time to have elements delivered
      vertx.setTimer(20, id -> {
        // Check we haven't received anything
        assertEquals(0, emitted.get());
        testComplete();
      });
    });
    await();
  }
/*


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
*/
}
