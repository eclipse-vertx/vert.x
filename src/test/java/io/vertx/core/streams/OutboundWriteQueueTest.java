/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.streams;

import io.vertx.core.streams.impl.OutboundWriteQueue;
import io.vertx.test.core.AsyncTestBase;
import junit.framework.AssertionFailedError;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.vertx.core.streams.impl.OutboundWriteQueue.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class OutboundWriteQueueTest extends AsyncTestBase {

  private List<Integer> output = Collections.synchronizedList(new ArrayList<>());
  private OutboundWriteQueue<Integer> queue;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    disableThreadChecks();
    output = Collections.synchronizedList(new ArrayList<>());
  }

  @Override
  protected void tearDown() throws Exception {
    queue = null;
  }

  @Test
  public void testAddFromOtherThread() {
    queue = new OutboundWriteQueue<>(elt -> {
      output.add(elt);
      return true;
    });
    assertEquals(DRAIN_REQUIRED_MASK, queue.submit(0));
    for (int i = 1;i < 10;i++) {
      assertEquals(0, queue.submit(i));
    }
    queue.drain();
    assertEquals(range(0, 10), output);
  }

  @Test
  public void testAddFromEventLoopThread() {
    queue = new OutboundWriteQueue<>(elt -> {
      output.add(elt);
      return true;
    });
    for (int i = 0;i < 10;i++) {
      assertEquals(0, queue.add(i));
    }
    assertEquals(10, output.size());
  }

  @Test
  public void testReentrantAdd() {
    queue = new OutboundWriteQueue<>(elt -> {
      output.add(elt);
      if (elt < 9) {
        queue.add(elt + 1);
      }
      return true;
    });
    queue.add(0);
    assertEquals(range(0, 10), output);
  }

  @Test
  public void testConcurrentAdd() {
    queue = new OutboundWriteQueue<>(elt -> {
      output.add(elt);
      if (elt < 9) {
        Thread thread = new Thread(() -> {
          queue.submit(elt + 1);
        });
        thread.start();
        try {
          thread.join();
        } catch (InterruptedException e) {
        }
      }
      return true;
    });
    queue.add(0);
    assertEquals(range(0, 10), output);
  }

  @Test
  public void testOverflow() {
    queue = new OutboundWriteQueue<>(elt -> false);
    assertEquals(DRAIN_REQUIRED_MASK, queue.add(0));
    for (int i = 1;i < 15;i++) {
      assertEquals(0, queue.add(i));
    }
    assertEquals(QUEUE_UNWRITABLE_MASK, queue.add(15));
  }

  @Test
  public void testOverflowReentrant() {
    queue = new OutboundWriteQueue<>(elt -> {
      if (elt == 0) {
        for (int i = 1;i < 15;i++) {
          assertEquals(0, queue.add(i));
        }
        assertEquals(QUEUE_UNWRITABLE_MASK, queue.add(15));
      }
      return false;
    });
    assertEquals(DRAIN_REQUIRED_MASK | QUEUE_UNWRITABLE_MASK, queue.add(0));
  }

  @Test
  public void testOverflowReentrant2() {
    queue = new OutboundWriteQueue<>(elt -> {
      if (elt == 0) {
        for (int i = 1;i < 15;i++) {
          assertEquals(0, queue.add(i));
        }
        assertEquals(QUEUE_UNWRITABLE_MASK, queue.add(15));
        return true;
      } else {
        return false;
      }
    });
    int flags = queue.add(0);
    assertFlagsSet(flags, DRAIN_REQUIRED_MASK);
    assertEquals(QUEUE_UNWRITABLE_MASK, queue.add(16));
  }

  @Test
  public void testOverflowReentrant3() {
    queue = new OutboundWriteQueue<>(elt -> {
      if (elt == 0) {
        for (int i = 1;i < 3;i++) {
          assertEquals(0, queue.add(i));
        }
        return true;
      } else {
        return false;
      }
    });
    assertEquals(DRAIN_REQUIRED_MASK, queue.add(0));
  }

  @Test
  public void testDrainQueue() {
    AtomicBoolean paused = new AtomicBoolean(true);
    queue = new OutboundWriteQueue<>(elt -> {
      if (paused.get()) {
        return false;
      } else {
        output.add(elt);
        return true;
      }
    });
    assertEquals(DRAIN_REQUIRED_MASK, queue.add(0));
    for (int i = 1;i < 5;i++) {
      assertEquals(0, queue.add(i));
    }
    assertEquals(0, output.size());
    queue.drain();
    assertEquals(0, output.size());
    paused.set(false);
    assertEquals(0, queue.drain());
    assertEquals(range(0, 5), output);
  }

  @Test
  public void testReentrantWritable1() {
    queue = new OutboundWriteQueue<>(elt -> {
      switch (elt) {
        case 0:
          Thread thread = new Thread(() -> {
            for (int i = 1;i < 15;i++) {
              assertEquals(0, queue.submit(i));
            }
            assertEquals(QUEUE_UNWRITABLE_MASK, queue.submit(15));
            assertEquals(0, queue.submit(16));
          });
          thread.start();
          try {
            thread.join();
          } catch (InterruptedException e) {
            fail(e);
          }
          return true;
        default:
          return false;
      }
    });
    assertEquals(DRAIN_REQUIRED_MASK, queue.add(0));
  }

  @Test
  public void testReentrantWritable2() {
    queue = new OutboundWriteQueue<>(elt -> {
      switch (elt) {
        case 0:
          Thread thread = new Thread(() -> {
            for (int i = 1;i < 15;i++) {
              assertEquals(0, queue.submit(i));
            }
            assertEquals(QUEUE_UNWRITABLE_MASK, queue.submit(15));
          });
          thread.start();
          try {
            thread.join();
          } catch (InterruptedException e) {
            fail(e);
          }
          return true;
        default:
          return false;
      }
    });
    int flags = queue.add(0);
    assertFlagsSet(flags, DRAIN_REQUIRED_MASK);
    assertFlagsClear(flags, QUEUE_WRITABLE_MASK, QUEUE_UNWRITABLE_MASK);
  }

  @Test
  public void testReentrantWritable3() {
    queue = new OutboundWriteQueue<>(elt -> {
      switch (elt) {
        case 0:
          Thread thread = new Thread(() -> {
            for (int i = 1; i < 15; i++) {
              assertEquals(0, queue.submit(i));
            }
            assertEquals(QUEUE_UNWRITABLE_MASK, queue.submit(15));
          });
          thread.start();
          try {
            thread.join();
          } catch (InterruptedException e) {
            fail(e);
          }
          return true;
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
        case 6:
        case 7:
        case 8:
          return true;
        default:
          return false;
      }
    });
    assertEquals(DRAIN_REQUIRED_MASK, queue.submit(0));
    int flags = queue.drain();
    assertFlagsSet(flags, QUEUE_WRITABLE_MASK, DRAIN_REQUIRED_MASK);
  }

  @Test
  public void testWritabilityListener() {
    AtomicInteger demand = new AtomicInteger(0);
    queue = new OutboundWriteQueue<>(elt -> {
      if (demand.get() > 0) {
        demand.decrementAndGet();
        return true;
      } else {
        return false;
      }
    });
    int count = 0;
    while ((queue.add(count++) & QUEUE_UNWRITABLE_MASK) == 0) {
    }
    assertEquals(16, count);
    demand.set(8);
    queue.drain();
    assertEquals(0, demand.get());
    demand.set(1);
    assertFlagsSet(queue.drain(), QUEUE_WRITABLE_MASK, DRAIN_REQUIRED_MASK);
    assertEquals(0, demand.get());
  }

  @Test
  public void testClear() {
    queue = new OutboundWriteQueue<>(elt -> false);
    for (int i = 0;i < 5;i++) {
      queue.add(i);
    }
    List<Integer> buffered = queue.clear();
    assertEquals(range(0, 5), buffered);
  }

  @Ignore
  @Test
  public void testRace3() {
    AtomicBoolean paused = new AtomicBoolean(true);
    queue = new OutboundWriteQueue<>(elt -> {
      switch (elt) {
        case 0:
          while ((queue.add(++elt) & QUEUE_UNWRITABLE_MASK) != 0) {
          }
          return true;
        default:
          return !paused.get();
      }
    });
    queue.add(0);
//    eventLoop.execute(() -> {
//      assertTrue(paused.getAndSet(false));
//      queue.drain();
//    });
    await();
  }

  @Test
  public void testReentrancy() {
    queue = new OutboundWriteQueue<>(elt -> {
      switch (elt) {
        case 0:
          for (int i = 1;i < 15;i++) {
            assertEquals(0, queue.add(i));
          }
          int flags = queue.add(16);
          assertFlagsSet(flags, QUEUE_UNWRITABLE_MASK);
          assertFlagsClear(flags, QUEUE_WRITABLE_MASK, DRAIN_REQUIRED_MASK);
          break;
      }
      return true;
    });
    int flags = queue.add(0);
    assertFlagsSet(flags, QUEUE_WRITABLE_MASK);
    assertFlagsClear(flags, QUEUE_UNWRITABLE_MASK, DRAIN_REQUIRED_MASK);
  }

  @Test
  public void testWeird() {
    AtomicInteger behavior = new AtomicInteger(0);
    queue = new OutboundWriteQueue<>(elt -> {
      switch (behavior.get()) {
        case 0:
          return false;
        case 1:
          for (int i = 1;i < 15;i++) {
            assertEquals(0, queue.submit(i));
          }
          behavior.set(2);
          return true;
        case 2:
          return true;
        default:
          throw new AssertionFailedError();
      }
    }) {
      @Override
      protected void hook() {
        assertEquals(0, queue.submit(15));
        assertEquals(QUEUE_UNWRITABLE_MASK, queue.submit(16));
      }
    };
    assertEquals(DRAIN_REQUIRED_MASK, queue.add(0));
    behavior.set(1);
    int flags = queue.drain();
    assertFlagsSet(flags, QUEUE_WRITABLE_MASK);
    assertFlagsClear(flags, DRAIN_REQUIRED_MASK);
  }

  @Test
  public void testOrdering() {
    AtomicInteger wqf = new AtomicInteger();
    queue = new OutboundWriteQueue<>(elt -> {
      switch (elt) {
        case 0:
          while (true) {
            if ((queue.submit(1) & QUEUE_UNWRITABLE_MASK) != 0) {
              wqf.incrementAndGet();
              queue.add(2);
              break;
            }
          }
          return true;
        case 1:
          return true;
        case 2:
          queue.add(3);
          return true;
        case 3:
          while (true) {
            if ((queue.submit(4) & QUEUE_UNWRITABLE_MASK) != 0) {
              wqf.incrementAndGet();
              break;
            }
          }
          return true;
        case 4:
          return true;
        default:
          throw new IllegalStateException();
      }
    }) {
      @Override
      protected void hook2() {
        queue.add(1);
      }
    };
    int flags = queue.add(0);
    assertTrue((flags & QUEUE_WRITABLE_MASK) != 0);
    int count = numberOfUnwritableSignals(flags);
    assertEquals(0, wqf.addAndGet(-count));
  }

  @Ignore
  @Test
  public void testOrdering2() {

    AtomicBoolean wqf = new AtomicBoolean();

    AtomicInteger state = new AtomicInteger(0);
    queue = new OutboundWriteQueue<>(elt -> {
      switch (state.get()) {
        case 0:
          return false;
        case 1:
          state.set(2);
          queue.submit(-1);
          return true;
        case 2:
          if (elt == -1) {
            while (true) {
              if ((queue.submit(3) & QUEUE_UNWRITABLE_MASK) != 0) {
                break;
              }
            }
          }
          return true;
        default:
          throw new IllegalStateException();

      }
    }) {
      @Override
      protected void hook2() {
        // Drain
        state.set(1);
        assertTrue((queue.drain() & QUEUE_WRITABLE_MASK) != 0);
        wqf.set(false);
      }
    };

    while (true) {
      if ((queue.submit(0) & QUEUE_UNWRITABLE_MASK) != 0) {
        wqf.set(true);
        break;
      }
    }

    System.out.println(wqf.get());

  }

  @Test
  public void testUnwritableCount() {
    AtomicInteger demand = new AtomicInteger();
    queue = new OutboundWriteQueue<>(elt-> {
      if (demand.get() > 0) {
        demand.decrementAndGet();
        return true;
      } else {
        return false;
      }
    });
    int count = 0;
    while (true) {
      if ((queue.submit(count++) & QUEUE_UNWRITABLE_MASK) != 0) {
        break;
      }
    }
    demand.set(1);
    assertFlagsSet(queue.drain(), DRAIN_REQUIRED_MASK);
    assertFlagsSet(queue.submit(count++), QUEUE_UNWRITABLE_MASK);
    demand.set(count - 1);
    int flags = queue.drain();
    assertFlagsSet(flags, QUEUE_WRITABLE_MASK);
    assertEquals(2, numberOfUnwritableSignals(flags));
  }

  @Test
  public void testConditions() {
    queue = new OutboundWriteQueue<>(elt -> true, 1, 1);
    assertEquals(0, queue.add(0));
    assertFlagsSet(queue.submit(0), QUEUE_UNWRITABLE_MASK, DRAIN_REQUIRED_MASK);
    assertFlagsSet(queue.drain(), QUEUE_WRITABLE_MASK);

    queue = new OutboundWriteQueue<>(elt -> true, 1, 2);
    assertEquals(0, queue.add(0));
    assertFlagsSet(queue.submit(0), DRAIN_REQUIRED_MASK);
    assertFlagsSet(queue.submit(1), QUEUE_UNWRITABLE_MASK);
    assertFlagsSet(queue.drain(), QUEUE_WRITABLE_MASK);
  }

  private void assertFlagsSet(int flags, int... masks) {
    for (int mask : masks) {
      assertTrue("Expecting flag " + Integer.toBinaryString(mask) + " to be set", (flags & mask) != 0);
    }
  }

  private void assertFlagsClear(int flags, int... masks) {
    for (int mask : masks) {
      assertTrue("Expecting flag " + Integer.toBinaryString(mask) + " to be clear", (flags & mask) == 0);
    }
  }

  private static List<Integer> range(int start, int end) {
    return IntStream.range(start, end).boxed().collect(Collectors.toList());
  }
}
