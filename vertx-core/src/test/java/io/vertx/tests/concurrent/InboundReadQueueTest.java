package io.vertx.tests.concurrent;

import io.vertx.core.streams.impl.InboundReadQueue;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.core.streams.impl.InboundReadQueue.drainResult;

public class InboundReadQueueTest extends AsyncTestBase {

  final InboundReadQueue.Factory factory = InboundReadQueue.SPSC;

  @Test
  public void testAdd() {
    InboundReadQueue<Integer> queue = factory.create(elt -> false);
    assertEquals(InboundReadQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    for (int i = 1;i < 15;i++) {
      assertEquals(0L, queue.add(i));
    }
    assertEquals(InboundReadQueue.QUEUE_UNWRITABLE_MASK, queue.add(17));
  }

  @Test
  public void testDrainSingle() {
    InboundReadQueue<Integer> queue = factory.create(elt -> true);
    assertEquals(InboundReadQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    assertEquals(InboundReadQueue.drainResult(0, false), queue.drain(17));
  }

  @Test
  public void testFoo() {
    InboundReadQueue<Integer> queue = factory.create(elt -> false);
    assertEquals(InboundReadQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    assertEquals(drainResult(1, false), queue.drain());
  }

  @Test
  public void testDrainFully() {
    LinkedList<Integer> consumed = new LinkedList<>();
    InboundReadQueue<Integer> queue = factory.create(elt -> {
      consumed.add(elt);
      return true;
    });
    assertEquals(InboundReadQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    int idx = 1;
    while ((queue.add(idx++) & InboundReadQueue.QUEUE_UNWRITABLE_MASK) == 0) {
      //
    }
    assertEquals(16, idx);
    assertEquals(drainResult(0, true), queue.drain());
    for (int i = 0;i < 16;i++) {
      assertEquals(i, (int)consumed.poll());
    }
    assertTrue(consumed.isEmpty());
  }

  @Test
  public void testDrainRefuseSingleElement() {
    InboundReadQueue<Integer> queue = factory.create(elt -> false);
    assertEquals(InboundReadQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    assertEquals(drainResult(1, false), queue.drain());
  }

  @Test
  public void testConsumeDrain() {
    AtomicInteger demand = new AtomicInteger(0);
    InboundReadQueue<Integer> queue = factory.create(elt -> {
      if (demand.get() > 0) {
        demand.decrementAndGet();
        return true;
      }
      return false;
    });
    assertEquals(InboundReadQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    int idx = 1;
    while ((queue.add(idx++) & InboundReadQueue.QUEUE_UNWRITABLE_MASK) == 0) {
      //
    }
    assertEquals(16, idx);
    for (int i = 0;i < 8;i++) {
      demand.set(1);
      assertEquals(drainResult((15 - i), false), queue.drain());
    }
    demand.set(1);
    assertEquals(drainResult(7, true), queue.drain());
  }

  @Test
  public void testPartialDrain() {
    AtomicInteger demand = new AtomicInteger(0);
    InboundReadQueue<Integer> queue = factory.create(elt -> true);
    int idx = 0;
    while ((queue.add(idx++) & InboundReadQueue.QUEUE_UNWRITABLE_MASK) == 0) {
      //
    }
    assertEquals(16, idx);
    assertEquals(drainResult(12, false), queue.drain(4));
    assertEquals(drainResult(7, true), queue.drain(5));
    assertEquals(drainResult(0, false), queue.drain());
  }

  @Test
  public void testUnwritableCount() {
    AtomicInteger demand = new AtomicInteger();
    InboundReadQueue<Integer> queue = factory.create(elt-> {
      if (demand.get() > 0) {
        demand.decrementAndGet();
        return true;
      } else {
        return false;
      }
    });
    int count = 0;
    while (true) {
      if ((queue.add(count++) & InboundReadQueue.QUEUE_UNWRITABLE_MASK) != 0) {
        break;
      }
    }
    demand.set(1);
    assertEquals(drainResult(15, false), queue.drain());
    assertFlagsSet(queue.add(count++), InboundReadQueue.QUEUE_UNWRITABLE_MASK);
    demand.set(count - 1);
    int flags = queue.drain();
    assertFlagsSet(flags, InboundReadQueue.QUEUE_WRITABLE_MASK);
    assertEquals(0, InboundReadQueue.numberOfPendingElements(flags));
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
}
