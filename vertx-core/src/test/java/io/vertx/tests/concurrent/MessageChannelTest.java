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
package io.vertx.tests.concurrent;

import io.vertx.core.streams.impl.MessageChannel;
import io.vertx.test.core.AsyncTestBase;
import junit.framework.AssertionFailedError;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.vertx.core.streams.impl.MessageChannel.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MessageChannelTest extends AsyncTestBase {

  private List<Integer> output = Collections.synchronizedList(new ArrayList<>());
  private MessageChannel.MpSc<Integer> queue;
  private Runnable unwritableHook;

  private int producerAdd(Integer element) {
    int res = queue.add(element);
    if ((res & UNWRITABLE_MASK) != 0) {
      if (unwritableHook != null) {
        unwritableHook.run();
      }
    }
    return res;
  }

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
  public void testWriteFromOtherThread() {
    queue = new MessageChannel.MpSc<>(elt -> {
      output.add(elt);
      return true;
    });
    assertEquals(DRAIN_REQUIRED_MASK, producerAdd(0));
    for (int i = 1;i < 10;i++) {
      assertEquals(0, producerAdd(i));
    }
    queue.drain();
    assertEquals(range(0, 10), output);
  }

  @Test
  public void testWriteFromEventLoopThread() {
    queue = new MessageChannel.MpSc<>(elt -> {
      output.add(elt);
      return true;
    });
    for (int i = 0;i < 10;i++) {
      assertEquals(0, queue.write(i));
    }
    assertEquals(10, output.size());
  }

  @Test
  public void testReentrantWrite() {
    queue = new MessageChannel.MpSc<>(elt -> {
      output.add(elt);
      if (elt < 9) {
        queue.write(elt + 1);
      }
      return true;
    });
    queue.write(0);
    assertEquals(range(0, 10), output);
  }

  @Test
  public void testConcurrentWrite() {
    queue = new MessageChannel.MpSc<>(elt -> {
      output.add(elt);
      if (elt < 9) {
        Thread thread = new Thread(() -> {
          producerAdd(elt + 1);
        });
        thread.start();
        try {
          thread.join();
        } catch (InterruptedException e) {
        }
      }
      return true;
    });
    queue.write(0);
    assertEquals(range(0, 10), output);
  }

  @Test
  public void testOverflow() {
    queue = new MessageChannel.MpSc<>(elt -> false);
    assertFlagsSet(DRAIN_REQUIRED_MASK, queue.write(0));
    for (int i = 1;i < 15;i++) {
      assertEquals(0, queue.write(i));
    }
    assertEquals(UNWRITABLE_MASK, queue.write(15));
  }

  @Test
  public void testOverflowReentrant() {
    queue = new MessageChannel.MpSc<>(elt -> {
      if (elt == 0) {
        for (int i = 1;i < 15;i++) {
          assertEquals(0, queue.write(i));
        }
        assertEquals(UNWRITABLE_MASK, queue.write(15));
      }
      return false;
    });
    assertFlagsSet(DRAIN_REQUIRED_MASK, queue.write(0));
  }

  @Test
  public void testOverflowReentrant2() {
    queue = new MessageChannel.MpSc<>(elt -> {
      if (elt == 0) {
        for (int i = 1;i < 15;i++) {
          assertEquals(0, queue.write(i));
        }
        assertEquals(UNWRITABLE_MASK, queue.write(15));
        return true;
      } else {
        return false;
      }
    });
    int flags = queue.write(0);
    assertFlagsSet(flags, DRAIN_REQUIRED_MASK);
    assertEquals(UNWRITABLE_MASK, queue.write(16));
  }

  @Test
  public void testOverflowReentrant3() {
    queue = new MessageChannel.MpSc<>(elt -> {
      if (elt == 0) {
        for (int i = 1;i < 3;i++) {
          assertEquals(0, queue.write(i));
        }
        return true;
      } else {
        return false;
      }
    });
    assertFlagsSet(DRAIN_REQUIRED_MASK, queue.write(0));
  }

  @Test
  public void testDrainQueue() {
    AtomicBoolean paused = new AtomicBoolean(true);
    queue = new MessageChannel.MpSc<>(elt -> {
      if (paused.get()) {
        return false;
      } else {
        output.add(elt);
        return true;
      }
    });
    assertFlagsSet(DRAIN_REQUIRED_MASK, queue.write(0));
    for (int i = 1;i < 5;i++) {
      assertEquals(0, queue.write(i));
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
    queue = new MessageChannel.MpSc<>(elt -> {
      switch (elt) {
        case 0:
          Thread thread = new Thread(() -> {
            for (int i = 1;i < 15;i++) {
              assertEquals(0, producerAdd(i));
            }
            assertEquals(UNWRITABLE_MASK, producerAdd(15));
            assertEquals(0, producerAdd(16));
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
    assertFlagsSet(DRAIN_REQUIRED_MASK, queue.write(0));
  }

  @Test
  public void testReentrantWritable2() {
    queue = new MessageChannel.MpSc<>(elt -> {
      switch (elt) {
        case 0:
          Thread thread = new Thread(() -> {
            for (int i = 1;i < 15;i++) {
              assertEquals(0, producerAdd(i));
            }
            assertEquals(UNWRITABLE_MASK, producerAdd(15));
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
    int flags = queue.write(0);
    assertFlagsSet(flags, DRAIN_REQUIRED_MASK);
    assertFlagsClear(flags, WRITABLE_MASK, UNWRITABLE_MASK);
  }

  @Test
  public void testReentrantWritable3() {
    queue = new MessageChannel.MpSc<>(elt -> {
      switch (elt) {
        case 0:
          Thread thread = new Thread(() -> {
            for (int i = 1; i < 15; i++) {
              assertEquals(0, producerAdd(i));
            }
            assertEquals(UNWRITABLE_MASK, producerAdd(15));
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
    assertEquals(DRAIN_REQUIRED_MASK, producerAdd(0));
    int flags = queue.drain();
    assertFlagsSet(flags, WRITABLE_MASK, DRAIN_REQUIRED_MASK);
  }

  @Test
  public void testWritabilityListener() {
    AtomicInteger demand = new AtomicInteger(0);
    queue = new MessageChannel.MpSc<>(elt -> {
      if (demand.get() > 0) {
        demand.decrementAndGet();
        return true;
      } else {
        return false;
      }
    });
    int count = 0;
    while ((queue.write(count++) & UNWRITABLE_MASK) == 0) {
    }
    assertEquals(16, count);
    demand.set(8);
    queue.drain();
    assertEquals(0, demand.get());
    demand.set(1);
    assertFlagsSet(queue.drain(), WRITABLE_MASK, DRAIN_REQUIRED_MASK);
    assertEquals(0, demand.get());
  }

  @Test
  public void testClear() {
    queue = new MessageChannel.MpSc<>(elt -> false);
    for (int i = 0;i < 5;i++) {
      queue.write(i);
    }
    List<Integer> buffered = queue.clear();
    assertEquals(range(0, 5), buffered);
  }

  @Test
  public void testReentrancy() {
    queue = new MessageChannel.MpSc<>(elt -> {
      switch (elt) {
        case 0:
          for (int i = 1;i < 15;i++) {
            assertEquals(0, queue.write(i));
          }
          int flags = queue.write(16);
          assertFlagsSet(flags, UNWRITABLE_MASK);
          assertFlagsClear(flags, WRITABLE_MASK, DRAIN_REQUIRED_MASK);
          break;
      }
      return true;
    });
    int flags = queue.write(0);
    assertFlagsSet(flags, WRITABLE_MASK);
    assertFlagsClear(flags, UNWRITABLE_MASK, DRAIN_REQUIRED_MASK);
  }

  @Test
  public void testWeird() {
    AtomicInteger behavior = new AtomicInteger(0);
    queue = new MessageChannel.MpSc<>(elt -> {
      switch (behavior.get()) {
        case 0:
          return false;
        case 1:
          for (int i = 1;i < 15;i++) {
            assertEquals(0, producerAdd(i));
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
        assertEquals(0, producerAdd(15));
        assertEquals(UNWRITABLE_MASK, producerAdd(16));
      }
    };
    assertFlagsSet(DRAIN_REQUIRED_MASK, queue.write(0));
    behavior.set(1);
    int flags = queue.drain();
    assertFlagsSet(flags, WRITABLE_MASK);
    assertFlagsClear(flags, DRAIN_REQUIRED_MASK);
  }

  @Test
  public void testOrdering() {
    int[] hookRuns = new int[1];
    unwritableHook = () -> {
      hookRuns[0]++;
      queue.write(1);
    };
    AtomicInteger wqf = new AtomicInteger();
    queue = new MessageChannel.MpSc<>(elt -> {
      switch (elt) {
        case 0:
          while (true) {
            if ((producerAdd(1) & UNWRITABLE_MASK) != 0) {
              wqf.incrementAndGet();
              queue.write(2);
              break;
            }
          }
          return true;
        case 1:
          return true;
        case 2:
          queue.write(3);
          return true;
        case 3:
          while (true) {
            if ((producerAdd(4) & UNWRITABLE_MASK) != 0) {
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
    });
    int flags = queue.write(0);
    assertTrue((flags & WRITABLE_MASK) != 0);
    int count = numberOfUnwritableSignals(flags);
    assertEquals(0, wqf.addAndGet(-count));
    assertEquals(2, hookRuns[0]);
  }

  @Test
  public void testUnwritableCount() {
    AtomicInteger demand = new AtomicInteger();
    queue = new MessageChannel.MpSc<>(elt-> {
      if (demand.get() > 0) {
        demand.decrementAndGet();
        return true;
      } else {
        return false;
      }
    });
    int count = 0;
    while (true) {
      if ((producerAdd(count++) & UNWRITABLE_MASK) != 0) {
        break;
      }
    }
    demand.set(1);
    assertFlagsSet(queue.drain(), DRAIN_REQUIRED_MASK);
    assertFlagsSet(producerAdd(count++), UNWRITABLE_MASK);
    demand.set(count - 1);
    int flags = queue.drain();
    assertFlagsSet(flags, WRITABLE_MASK);
    assertEquals(2, numberOfUnwritableSignals(flags));
  }

  @Test
  public void testConditions() {
    queue = new MessageChannel.MpSc<>(elt -> true, 1, 1);
//    assertEquals(0, queue.write(0));
    queue.write(0);
    assertFlagsSet(producerAdd(0), UNWRITABLE_MASK, DRAIN_REQUIRED_MASK);
    assertFlagsSet(queue.drain(), WRITABLE_MASK);

    queue = new MessageChannel.MpSc<>(elt -> true, 1, 2);
    assertEquals(0, queue.write(0));
    assertFlagsSet(queue.add(0), DRAIN_REQUIRED_MASK);
    assertFlagsSet(queue.add(1), UNWRITABLE_MASK);
    assertFlagsSet(queue.drain(), WRITABLE_MASK);
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

  @Test
  public void testWriteShouldNotReturnUnwritableWithOverflowSubmissions() {
    queue = new MessageChannel.MpSc<>(elt -> {
      if (elt == 0) {
        Thread th = new Thread(() -> {
          int idx = 1;
          while ((queue.add(idx++) & UNWRITABLE_MASK) == 0) {

          }
        });
        th.start();
        try {
          th.join();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      return false;
    });
    assertEquals(0, (queue.write(0) & UNWRITABLE_MASK));
  }
}
