package io.vertx.core.net.impl;

import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageDispatchTest extends VertxTestBase {

  @Test
  public void testSimple() {
    Object msg = new Object();
    MessageQueue mq = new MessageQueue();
    mq.read(msg);
    assertFalse(mq.handling);
    assertNull(mq.processing);
  }

  @Test
  public void testDelayedAck() {
    Object msg = new Object();
    List<Object> pending = new ArrayList<>();
    MessageQueue mq = new MessageQueue() {
      @Override
      public void handleMessage(Object msg) {
        pending.add(msg);
      }
    };
    mq.read(msg);
    assertEquals(Collections.singletonList(msg), pending);
    assertFalse(mq.handling);
    assertSame(msg, mq.processing);
    mq.ack(msg);
    assertFalse(mq.handling);
    assertNull(mq.processing);
  }

  @Test
  public void testEnqueueMessages() {
    Object msg1 = new Object();
    Object msg2 = new Object();
    Object msg3 = new Object();
    Object msg4 = new Object();
    List<Object> pending = new ArrayList<>();
    MessageQueue mq = new MessageQueue() {
      @Override
      public void handleMessage(Object msg) {
        pending.add(msg);
        if (msg == msg2) {
          ack(msg2);
        }
      }
    };
    mq.read(msg1);
    assertEquals(Collections.singletonList(msg1), pending);
    mq.read(msg2);
    assertEquals(Collections.singletonList(msg1), pending);
    mq.read(msg3);
    assertEquals(Collections.singletonList(msg1), pending);
    mq.read(msg4);
    assertEquals(Collections.singletonList(msg1), pending);
    assertFalse(mq.handling);
    assertSame(msg1, mq.processing);
    mq.ack(msg1);
    assertFalse(mq.handling);
    assertSame(msg3, mq.processing);
    assertEquals(Arrays.asList(msg1, msg2, msg3), pending);
    mq.ack(msg3);
    assertFalse(mq.handling);
    assertSame(msg4, mq.processing);
    assertEquals(Arrays.asList(msg1, msg2, msg3, msg4), pending);
    mq.ack(msg4);
    assertFalse(mq.handling);
    assertNull(mq.processing);
  }

  @Test
  public void testReentrantRead() {
    MessageQueue mq = new MessageQueue() {
      @Override
      public void handleMessage(Object msg) {
        switch ((int)msg) {
          case 1:
            read(2);
            ack(msg);
            break;
        }
      }
    };
    mq.read(1);
    assertEquals(2, mq.processing);
  }

  @Test
  public void testReentrantOverflow() {
    AtomicInteger seq = new AtomicInteger();
    MessageQueue mq = new MessageQueue() {
      @Override
      public void handleMessage(Object msg) {
        switch ((int)msg) {
          case 1:
            while (read(seq.incrementAndGet())) {
            }
            break;
        }
      }
    };
    assertFalse(mq.read(seq.incrementAndGet()));
    assertEquals(17, seq.get());
    assertEquals(1, mq.processing);
  }

  @Test
  public void testDrainRead() {
    Deque<Object> pending = new ArrayDeque<>();
    AtomicInteger seq = new AtomicInteger();
    AtomicInteger drains = new AtomicInteger();
    MessageQueue mq = new MessageQueue() {
      @Override
      public void handleMessage(Object msg) {
        pending.add(msg);
      }
      @Override
      public void handleDrain() {
        if (drains.incrementAndGet() == 1) {
          while (read(seq.incrementAndGet())) {

          }
        }
      }
    };
    while (mq.read(seq.incrementAndGet())) {
    }
    int toDrain1 = seq.get();
    for (int i = 1; i <= toDrain1; i++) {
      mq.ack(i);
    }
    assertEquals(1, drains.get());
    assertEquals(toDrain1 + 1, mq.processing);
    int toDrain2 = seq.get();
    for (int i = toDrain1 + 1;i <= toDrain2;i++) {
      assertEquals(1, drains.get());
      mq.ack(i);
    }
    assertEquals(2, drains.get());
    assertNull(mq.processing);
  }

  @Test
  public void testPause() {
    Deque<Object> pending = new ArrayDeque<>();
    AtomicInteger drains = new AtomicInteger();
    MessageQueue mq = new MessageQueue() {
      @Override
      public void handleMessage(Object msg) {
        pending.add(msg);
      }
      @Override
      public void handleDrain() {
        drains.incrementAndGet();
      }
    };
    int cnt = 0;
    while (mq.read(cnt++)) {
      assertFalse(mq.paused);
    }
    assertTrue(mq.paused);
    assertEquals(cnt, 16 + 1);
    assertFalse(mq.read(cnt++));
    for (Object o = pending.poll();o != null;o = pending.poll()) {
      assertEquals(0, drains.get());
      assertTrue(mq.paused);
      mq.ack(o);
    }
    assertFalse(mq.paused);
    assertEquals(1, drains.get());
  }
}
