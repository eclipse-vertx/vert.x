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
import io.vertx.core.internal.VertxInternal;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

public class InboundMessageChannelSingleThreadTest extends InboundMessageChannelTest {

  @Override
  protected Context createContext(VertxInternal vertx) {
    return vertx.createEventLoopContext();
  }

  @Test
  public void testEmitInElementHandler() {
    AtomicInteger events = new AtomicInteger();
    AtomicBoolean receiving = new AtomicBoolean();
    queue = buffer(elt -> {
      assertConsumer();
      assertFalse(receiving.getAndSet(true));
      events.incrementAndGet();
      if (elt == 0) {
        queue.emit(5);
      }
      receiving.set(false);
    }, 5, 5);
    producerTask(() -> {
      queue.pause();
      queue.fetch(1);
      assertFalse(queue.emit());
      assertEquals(5, queue.size());
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
    AtomicInteger events = new AtomicInteger();
    AtomicInteger drained = new AtomicInteger();
    AtomicBoolean draining = new AtomicBoolean();
    queue = buffer(elt -> {
      assertConsumer();
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
    producerTask(() -> {
      queue.pause();
      queue.fill();
      queue.fetch(10);
    });
    await();
  }

  @Test
  public void testEmitWhenHandlingLastItem() {
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
    producerTask(() -> {
      queue.pause();
      queue.fill();
      queue.fetch(sequence.get());
      assertEquals(0, writable.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testEmitInDrainHandler1() {
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
    producerTask(() -> {
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
    producerTask(() -> {
      queue.pause();
      queue.fill();
      queue.fetch(5);
      complete();
    });
    await();
  }

  @Test
  public void testDrainAfter() {
    AtomicInteger events = new AtomicInteger();
    AtomicBoolean receiving = new AtomicBoolean();
    queue = buffer(elt -> {
      assertConsumer();
      assertFalse(receiving.getAndSet(true));
      events.incrementAndGet();
      if (elt == 0) {
        queue.emit(5);
      }
      receiving.set(false);
    }, 5, 5);
    producerTask(() -> {
      assertTrue(queue.emit());
      assertEquals(6, sequence.get());
      assertEquals(6, events.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testPauseInElementHandler() {
    AtomicInteger events = new AtomicInteger();
    queue = buffer(elt -> {
      events.incrementAndGet();
      if (elt == 0) {
        queue.pause();
        queue.emit(5);
      }
    }, 5, 5);
    producerTask(() -> {
      assertFalse(queue.emit());
      assertEquals(1, events.get());
      assertEquals(5, queue.size());
      testComplete();
    });
    await();
  }

  @Test
  public void testAddAllEmitInHandler() {
    List<Integer> emitted = new ArrayList<>();
    queue = buffer(elt -> {
      switch (elt) {
        case 0:
          queue.emit();
      }
      emitted.add(elt);
    }, 4, 4);
    producerTask(() -> {
      assertTrue(queue.emit(3));
      assertEquals(Arrays.asList(0, 1, 2, 3), emitted);
      testComplete();
    });
    await();
  }

  @Test
  public void testAddAllWhenDelivering() {
    List<Integer> emitted = new ArrayList<>();
    queue = buffer(elt -> {
      emitted.add(elt);
      if (elt == 2) {
        queue.write(Arrays.asList(4, 5));
        // Check that we haven't re-entered the handler
        assertEquals(Arrays.asList(0, 1, 2), emitted);
      }
    }, 4, 4);
    producerTask(() -> {
      queue.emit(4);
      assertWaitUntil(() -> Arrays.asList(0, 1, 2, 3, 4, 5).equals(emitted));
      testComplete();
    });
    await();
  }

  @Test
  public void testPauseInHandlerSignalsFullImmediately() {
    queue = buffer(elt -> {
      queue.pause();
      queue.emit();
    }, 1, 1);
    producerTask(() -> {
      assertFalse(queue.emit());
      testComplete();
    });
    await();
  }

  @Test
  public void testWriteWhenClosing() {
    AtomicInteger emitted = new AtomicInteger();
    List<Integer> dropped = Collections.synchronizedList(new ArrayList<>());
    queue = new TestChannel(elt -> emitted.incrementAndGet(), 4, 4) {
      @Override
      protected void handleDispose(Integer msg) {
        dropped.add(msg);
        if (msg == 0) {
          queue.write(1);
        }
      }
    };
    producerTask(() -> {
      queue.pause();
      queue.write(0);
      queue.close();
      assertEquals(0, emitted.get());
      assertEquals(Arrays.asList(0, 1), dropped);
      testComplete();
    });
  }

  @Test
  public void testCloseWhenDraining() {
    List<Integer> emitted = Collections.synchronizedList(new ArrayList<>());;
    List<Integer> dropped = Collections.synchronizedList(new ArrayList<>());
    queue = new TestChannel(elt -> {
      emitted.add(elt);
      if (elt == 0) {
        queue.close();
        assertEquals(Collections.emptyList(), dropped);
      }
    }, 4, 4) {
      @Override
      protected void handleDispose(Integer msg) {
        dropped.add(msg);
      }
    };
    producerTask(() -> {
      queue.fill();
      assertEquals(List.of(1, 2, 3), dropped);
      assertEquals(List.of(0), emitted);
      testComplete();
    });
    await();
  }
}
