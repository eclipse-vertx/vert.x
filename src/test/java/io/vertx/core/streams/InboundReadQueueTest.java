package io.vertx.core.streams;

import io.vertx.core.streams.impl.InboundReadQueue;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class InboundReadQueueTest {

  @Test
  public void testFoo() {

    ArrayDeque<Integer> consumer = new ArrayDeque<>();

    InboundReadQueue<Integer> queue = new InboundReadQueue<>(elt -> {
      consumer.add(elt);
    });
    assertTrue(queue.add(0));
    assertEquals(1, consumer.size());
    assertEquals(0, (int)consumer.poll());
    assertFalse(queue.ack(0));
  }

  @Test
  public void testBar() {

    ArrayDeque<Integer> consumer = new ArrayDeque<>();

    InboundReadQueue<Integer>[] queue = new InboundReadQueue[1];
    queue[0] = new InboundReadQueue<>(elt -> {
      queue[0].ack(elt);
    });
    queue[0].add(0);
  }

  @Test
  public void testJuu() {

    ArrayDeque<Integer> consumer = new ArrayDeque<>();

    InboundReadQueue<Integer> queue = new InboundReadQueue<>(elt -> {
      consumer.add(elt);
    });
    assertTrue(queue.add(0));
    assertTrue(queue.add(1));
    assertEquals(1, consumer.size());
//    assertEquals(0, (int)consumer.poll());
    assertTrue(queue.ack(0));
  }

  @Test
  public void testDrain1() {

    ArrayDeque<Integer> consumer = new ArrayDeque<>();

    InboundReadQueue<Integer> queue = new InboundReadQueue<>(elt -> {
      consumer.add(elt);
    });
    assertTrue(queue.add(0));
    assertTrue(queue.add(1));
    assertTrue(queue.add(2));
    assertTrue(queue.ack(0));
    queue.drain();
    assertEquals(2, consumer.size());
  }

  @Test
  public void testDrain2() {

    ArrayDeque<Integer> consumer = new ArrayDeque<>();

    AtomicBoolean autoAck = new AtomicBoolean();

    InboundReadQueue<Integer>[] queue = new InboundReadQueue[1];
    queue[0] = new InboundReadQueue<>(elt -> {
      consumer.add(elt);
      if (autoAck.get()) {
        queue[0].ack(elt);
      }
    });
    assertTrue(queue[0].add(0));
    assertTrue(queue[0].add(1));
    assertTrue(queue[0].add(2));
    assertTrue(queue[0].ack(0));
    autoAck.set(true);
    queue[0].drain();
    assertEquals(3, consumer.size());
  }

  volatile Consumer<Integer> consumer;

  @Test
  public void testPause() {

    consumer = elt -> {};
    InboundReadQueue<Integer> queue = new InboundReadQueue<>(elt -> consumer.accept(elt));
    int seq = 0;
    while (queue.add(seq++)) {
      //
    }
    queue.ack(0);
    assertEquals(8 + 1, seq);
    consumer = queue::ack;
    assertTrue(queue.drain());

  }
}
