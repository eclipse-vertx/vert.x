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
import io.vertx.core.VertxOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.concurrent.InboundMessageChannel;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

public abstract class InboundMessageChannelTest extends VertxTestBase {

  private volatile Thread producerThread;
  private volatile Thread consumerThread;

  Context context;
  TestChannel queue;
  final AtomicInteger sequence = new AtomicInteger();

  InboundMessageChannelTest() {
  }

  class TestChannel extends InboundMessageChannel<Integer> {

    final IntConsumer consumer;
    private Handler<Void> drainHandler;
    private volatile boolean writable;
    private int size;

    public TestChannel(IntConsumer consumer) {
      super(((ContextInternal) context).eventLoop(), ((ContextInternal) context).executor());
      this.consumer = consumer;
      this.writable = true;
    }

    public TestChannel(IntConsumer consumer, int lwm, int hwm) {
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

    public boolean isWritable() {
      return writable;
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

    TestChannel drainHandler(Handler<Void> handler) {
      this.drainHandler = handler;
      return this;
    }
  }

  protected abstract Context createContext(VertxInternal vertx);

  @Override
  public void setUp() throws Exception {
    super.setUp();
    context = createContext((VertxInternal) vertx);
    sequence.set(0);
    context.runOnContext(v -> {
      consumerThread = Thread.currentThread();
    });
    ((ContextInternal)context).nettyEventLoop().execute(() -> {
      producerThread = Thread.currentThread();
    });
    waitUntil(() -> consumerThread != null && producerThread != null);
  }

  @Override
  protected VertxOptions getOptions() {
    return super.getOptions().setWorkerPoolSize(1);
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  protected final void assertConsumer() {
    assertSame(consumerThread, Thread.currentThread());
  }

  protected final void assertProducer() {
    assertSame(producerThread, Thread.currentThread());
  }

  protected final void producerTask(Runnable task) {
    ((ContextInternal)context).nettyEventLoop().execute(task);
  }

  protected final void consumerTask(Runnable task) {
    context.runOnContext(v -> task.run());
  }

  protected final TestChannel buffer(IntConsumer consumer) {
    return new TestChannel(consumer);
  }

  protected final TestChannel buffer(IntConsumer consumer, int lwm, int hwm) {
    return new TestChannel(consumer, lwm, hwm);
  }

  @Test
  public void testFlowing() {
    AtomicInteger events = new AtomicInteger();
    queue = buffer(elt -> {
      assertConsumer();
      assertEquals(0, elt);
      assertEquals(0, events.getAndIncrement());
      testComplete();
    });
    producerTask(() -> {
      assertTrue(queue.emit());
    });
    await();
  }

  @Test
  public void testTake() {
    AtomicInteger events = new AtomicInteger();
    queue = buffer(elt -> {
      assertConsumer();
      assertEquals(0, elt);
      assertEquals(0, events.getAndIncrement());
      testComplete();
    });
    consumerTask(() -> {
      queue.pause();
      queue.fetch(1);
      producerTask(() -> queue.emit());
    });
    await();
  }

  @Test
  public void testFlowingAdd() {
    AtomicInteger events = new AtomicInteger();
    queue = buffer(elt -> {
      assertConsumer();
      events.getAndIncrement();
    });
    producerTask(() -> {
      assertTrue(queue.emit());
      assertWaitUntil(() -> events.get() == 1);
      assertTrue(queue.emit());
      assertWaitUntil(() -> events.get() == 2);
      testComplete();
    });
    await();
  }

  @Test
  public void testFlowingRefill() {
    AtomicInteger events = new AtomicInteger();
    queue = buffer(elt -> {
      assertConsumer();
      events.getAndIncrement();
    }, 5, 5).drainHandler(v -> {
      assertProducer();
      assertEquals(8, events.get());
      testComplete();
    });
    queue.pause();
    producerTask(() -> {
      for (int i = 0; i < 8; i++) {
        assertEquals("Expected " + i + " to be bilto", i < 4, queue.emit());
      }
      queue.resume();
    });
    await();
  }

  @Test
  public void testPauseWhenFull() {
    AtomicInteger events = new AtomicInteger();
    AtomicInteger reads = new AtomicInteger();
    queue = buffer(elt -> {
      assertConsumer();
      assertEquals(0, reads.get());
      assertEquals(0, events.getAndIncrement());
      testComplete();
    }, 5, 5).drainHandler(v2 -> {
      assertProducer();
      assertEquals(0, reads.getAndIncrement());
    });
    producerTask(() -> {
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
    AtomicInteger reads = new AtomicInteger();
    AtomicInteger events = new AtomicInteger();
    queue = buffer(elt -> {
      assertConsumer();
      events.getAndIncrement();
    }, 5, 5).drainHandler(v2 -> {
      assertProducer();
      assertEquals(0, reads.getAndIncrement());
      assertEquals(5, events.get());
      testComplete();
    });
    producerTask(() -> {
      queue.pause();
      queue.fill();
      queue.resume();
    });
    await();
  }

  @Test
  public void testPausedDrain() {
    waitFor(2);
    AtomicInteger drained = new AtomicInteger();
    AtomicInteger emitted = new AtomicInteger();
    queue = buffer(elt -> {
      assertConsumer();
      assertEquals(0, drained.get());
      emitted.getAndIncrement();
    }, 5, 5);
    queue.drainHandler(v2 -> {
      assertProducer();
      assertEquals(0, drained.getAndIncrement());
      assertEquals(5, emitted.get());
      complete();
    });
    producerTask(() -> {
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
    AtomicInteger events = new AtomicInteger();
    AtomicInteger reads = new AtomicInteger();
    queue = buffer(elt -> {
      assertConsumer();
      events.getAndIncrement();
    }, 3, 3)
      .drainHandler(v2 -> {
        assertProducer();
        assertEquals(0, reads.getAndIncrement());
      });
    producerTask(() -> {
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
    AtomicInteger emitted = new AtomicInteger();
    queue = buffer(elt -> {
      emitted.incrementAndGet();
    }, 2, 2);
    producerTask(() -> {
      queue.pause();
      queue.fetch(1);
      assertTrue(queue.emit());
      assertWaitUntil(() -> emitted.get() == 1);
      assertTrue(queue.emit());
      assertFalse(queue.emit());
      testComplete();
    });
    await();
  }

  @Test
  public void testHighWaterMark() {
    queue = buffer(elt -> {
    }, 5, 5);
    producerTask(() -> {
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
  public void testAddAllWhenPaused() {
    waitFor(2);
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
    producerTask(() -> {
      queue.pause();
      assertFalse(queue.emit(5));
      queue.fetch(1);
      complete();
    });
    await();
  }

  @Test
  public void testAddAllWhenFlowing() {
    AtomicInteger emitted = new AtomicInteger();
    AtomicInteger drained = new AtomicInteger();
    queue = buffer(elt -> {
      emitted.incrementAndGet();
    }, 4, 4)
      .drainHandler(v2 -> drained.incrementAndGet());
    producerTask(() -> {
      queue.emit(4);
    });
    waitUntilEquals(1, drained::get);
    waitUntilEquals(4, emitted::get);
  }

  @Test
  public void testCheckThatPauseAfterResumeWontDoAnyEmission() {
    AtomicInteger emitted = new AtomicInteger();
    queue = buffer(elt -> emitted.incrementAndGet(), 4, 4);
    producerTask(() -> {
      queue.pause();
      queue.fill();
      consumerTask(() -> {
        // Resume will execute an asynchronous drain operation
        queue.resume();
        // Pause just after to ensure that no elements will be delivered to the handler
        queue.pause();
        // Give enough time to have elements delivered
        vertx.setTimer(20, id -> {
          // Check we haven't received anything
          assertEquals(0, emitted.get());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testBufferSignalingFullImmediately() {
    List<Integer> emitted = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger drained = new AtomicInteger();
    queue = buffer(emitted::add, 1, 1);
    producerTask(() -> {
      queue.drainHandler(v -> {
        switch (drained.getAndIncrement()) {
          case 0:
            producerTask(() -> {
              assertFalse(queue.emit());
              queue.resume();
            });
            break;
          case 1:
            assertEquals(Arrays.asList(0, 1), emitted);
            testComplete();
            break;
        }
      });
      queue.emit();
      assertWaitUntil(() -> emitted.size() == 1);
      assertEquals(Collections.singletonList(0), emitted);
      queue.pause();
    });
    await();
  }

  @Test
  public void testClose() {
    List<Integer> emitted = Collections.synchronizedList(new ArrayList<>());
    List<Integer> disposed = Collections.synchronizedList(new ArrayList<>());
    queue = new TestChannel(emitted::add, 1, 1) {
      @Override
      protected void handleDispose(Integer msg) {
        disposed.add(msg);
      }
    };

    producerTask(() -> {
      queue.pause();
      queue.emit(5);
      queue.closeProducer();
      consumerTask(() -> {
        queue.closeConsumer();
        assertEquals(Collections.emptyList(), emitted);
        assertEquals(Arrays.asList(0, 1, 2, 3, 4), disposed);
        producerTask(() -> {
          queue.write(5);
          testComplete();
        });
      });
    });
    await();
  }
}
