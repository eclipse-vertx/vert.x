package io.vertx.tests.concurrent;

import io.vertx.core.streams.impl.OutboundWriteQueue;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.core.streams.impl.OutboundWriteQueue.drainResult;

public class InboundReadQueueTest extends AsyncTestBase {

  final OutboundWriteQueue.Factory factory = OutboundWriteQueue.SPSC;

  @Test
  public void testAdd() {
    OutboundWriteQueue<Integer> queue = factory.create(elt -> false);
    assertEquals(OutboundWriteQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    for (int i = 1;i < 15;i++) {
      assertEquals(0L, queue.add(i));
    }
    assertEquals(OutboundWriteQueue.QUEUE_UNWRITABLE_MASK, queue.add(17));
  }

  @Test
  public void testDrainSingle() {
    OutboundWriteQueue<Integer> queue = factory.create(elt -> true);
    assertEquals(OutboundWriteQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    assertEquals(OutboundWriteQueue.drainResult(0, 0, false), queue.drain(17));
  }

  @Test
  public void testFoo() {
    OutboundWriteQueue<Integer> queue = factory.create(elt -> false);
    assertEquals(OutboundWriteQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    assertEquals(drainResult(0, 1, false), queue.drain());
  }

  @Test
  public void testDrainFully() {
    LinkedList<Integer> consumed = new LinkedList<>();
    OutboundWriteQueue<Integer> queue = factory.create(elt -> {
      consumed.add(elt);
      return true;
    });
    assertEquals(OutboundWriteQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    int idx = 1;
    while ((queue.add(idx++) & OutboundWriteQueue.QUEUE_UNWRITABLE_MASK) == 0) {
      //
    }
    assertEquals(16, idx);
    assertEquals(drainResult(0, 0, true), queue.drain() & 0x3);
    for (int i = 0;i < 16;i++) {
      assertEquals(i, (int)consumed.poll());
    }
    assertTrue(consumed.isEmpty());
  }

  @Test
  public void testDrainRefuseSingleElement() {
    OutboundWriteQueue<Integer> queue = factory.create(elt -> false);
    assertEquals(OutboundWriteQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    assertEquals(drainResult(0, 1, false), queue.drain());
  }

  @Test
  public void testConsumeDrain() {
    AtomicInteger demand = new AtomicInteger(0);
    OutboundWriteQueue<Integer> queue = factory.create(elt -> {
      if (demand.get() > 0) {
        demand.decrementAndGet();
        return true;
      }
      return false;
    });
    assertEquals(OutboundWriteQueue.DRAIN_REQUIRED_MASK, queue.add(0));
    int idx = 1;
    while ((queue.add(idx++) & OutboundWriteQueue.QUEUE_UNWRITABLE_MASK) == 0) {
      //
    }
    assertEquals(16, idx);
    for (int i = 0;i < 8;i++) {
      demand.set(1);
      assertEquals(drainResult(0, (15 - i), false), queue.drain() & 0xFFFF);
    }
    demand.set(1);
    assertEquals(drainResult(0, 7, true), queue.drain() & 0xFFFF);
  }

  @Test
  public void testPartialDrain() {
    AtomicInteger demand = new AtomicInteger(0);
    OutboundWriteQueue<Integer> queue = factory.create(elt -> true);
    int idx = 0;
    while ((queue.add(idx++) & OutboundWriteQueue.QUEUE_UNWRITABLE_MASK) == 0) {
      //
    }
    assertEquals(16, idx);
    assertEquals(drainResult(0, 12, false), queue.drain(4) & 0xFFFF);
    assertEquals(drainResult(0, 7, true), queue.drain(5) & 0xFFFF);
    assertEquals(drainResult(0, 0, false), queue.drain()  & 0xFFFF);
  }

  @Test
  public void testUnwritableCount() {
    AtomicInteger demand = new AtomicInteger();
    OutboundWriteQueue<Integer> queue = factory.create(elt-> {
      if (demand.get() > 0) {
        demand.decrementAndGet();
        return true;
      } else {
        return false;
      }
    });
    int count = 0;
    while (true) {
      if ((queue.add(count++) & OutboundWriteQueue.QUEUE_UNWRITABLE_MASK) != 0) {
        break;
      }
    }
    demand.set(1);
    assertEquals(drainResult(0, 15, false), queue.drain() & 0xFFFF);
    assertFlagsSet(queue.add(count++), OutboundWriteQueue.QUEUE_UNWRITABLE_MASK);
    demand.set(count - 1);
    int flags = queue.drain();
    assertFlagsSet(flags, OutboundWriteQueue.QUEUE_WRITABLE_MASK);
    assertEquals(0, OutboundWriteQueue.numberOfPendingElements(flags));
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
