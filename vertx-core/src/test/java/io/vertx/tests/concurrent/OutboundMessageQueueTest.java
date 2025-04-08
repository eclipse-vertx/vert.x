/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.concurrent;

import io.netty.channel.EventLoop;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class OutboundMessageQueueTest extends VertxTestBase {

  private List<Integer> output = Collections.synchronizedList(new ArrayList<>());
  private OutboundMessageQueue<Integer> queue;
  private EventExecutor eventLoop;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    disableThreadChecks();
    output = Collections.synchronizedList(new ArrayList<>());
    eventLoop = ((ContextInternal)vertx.getOrCreateContext()).executor();
  }

  @Test
  public void testReentrantWriteAccept() {
    queue = new OutboundMessageQueue<>(eventLoop) {
      int reentrant = 0;
      @Override
      public boolean test(Integer msg) {
        assertEquals(0, reentrant++);
        try {
          if (msg == 0) {
            assertTrue(write(1));
          }
          output.add(msg);
          return true;
        } finally {
          reentrant--;
        }
      }
    };
    eventLoop.execute(() -> {
      assertTrue(queue.write(0));
      assertEquals(List.of(0, 1), output);
      testComplete();
    });
    await();
  }

  @Test
  public void testReentrantWriteReject() {
    queue = new OutboundMessageQueue<>(eventLoop) {
      int reentrant = 0;
      @Override
      public boolean test(Integer msg) {
        assertEquals(0, reentrant++);
        try {
          if (msg == 0) {
            assertTrue(write(1));
          }
          output.add(msg);
          return false;
        } finally {
          reentrant--;
        }
      }
    };
    eventLoop.execute(() -> {
      assertTrue(queue.write(0));
      assertEquals(List.of(0), output);
      testComplete();
    });
    await();
  }

  @Test
  public void testReentrantOverflowThenDrain1() {
    queue = new OutboundMessageQueue<>(eventLoop) {
      int reentrant = 0;
      int draining = 0;
      int drained = 0;
      @Override
      public boolean test(Integer msg) {
        assertEquals(0, reentrant++);
        output.add(msg);
        try {
          switch (msg) {
            case 0:
              int count = 1;
              while (write(count++)) {
              }
              assertEquals(16, count);
              assertEquals(0, drained);
              break;
            default:
              break;
          }
          return true;
        } finally {
          reentrant--;
        }
      }
      @Override
      protected void startDraining() {
        draining++;
      }
      @Override
      protected void stopDraining() {
        draining--;
      }
      @Override
      protected void handleDrained() {
        drained++;
        assertEquals(0, reentrant);
        assertEquals(0, draining);
        List<Integer> expected = IntStream.range(0, 16).boxed().collect(Collectors.toList());
        assertEquals(expected, output);
        testComplete();
      }
    };
    eventLoop.execute(() -> {
      queue.write(0);
    });
    await();
  }

  @Test
  public void testReentrantOverflowThenDrain2() {
    queue = new OutboundMessageQueue<>(eventLoop) {
      int reentrant = 0;
      int draining = 0;
      int drained = 0;
      @Override
      public boolean test(Integer msg) {
        assertEquals(0, reentrant++);
        output.add(msg);
        try {
          switch (msg) {
            case 0: {
              int count = 1;
              while (write(count++)) {
              }
              assertEquals(16, count);
              assertEquals(0, drained);
              break;
            }
            case 15: {
              int count = 16;
              while (write(count++)) {
              }
              assertEquals(17, count);
              assertEquals(0, drained);
              break;
            }
            default:
              break;
          }
          return true;
        } finally {
          reentrant--;
        }
      }
      @Override
      protected void startDraining() {
        draining++;
      }
      @Override
      protected void stopDraining() {
        draining--;
      }
      @Override
      protected void handleDrained() {
        drained++;
        assertEquals(0, reentrant);
        assertEquals(0, draining);
        List<Integer> expected = IntStream.range(0, 17).boxed().collect(Collectors.toList());
        assertEquals(expected, output);
        testComplete();
      }
    };
    eventLoop.execute(() -> {
      queue.write(0);
    });
    await();
  }

  @Test
  public void testReentrantTryDrain() {
    AtomicBoolean overflow = new AtomicBoolean();
    queue = new OutboundMessageQueue<>(eventLoop) {
      int draining;
      @Override
      protected void startDraining() {
        assertEquals(0, draining++);
      }
      @Override
      protected void stopDraining() {
        draining--;
      }
      @Override
      public boolean test(Integer msg) {
        if (overflow.get()) {
          queue.tryDrain();
        }
        return false;
      }
    };
    eventLoop.execute(() -> {
      int count = 1;
      while (queue.write(count++)) {
      }
      eventLoop.execute(() -> {
        overflow.set(true);
        queue.tryDrain();
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testWriteAfterDrain() {
    AtomicBoolean paused = new AtomicBoolean();
    AtomicInteger count = new AtomicInteger();
    AtomicInteger test = new AtomicInteger();
    queue = new OutboundMessageQueue<>(eventLoop) {
      @Override
      protected void handleDrained() {
        int msg = count.getAndIncrement();
        write(msg);
        assertEquals(msg, test.get());
        testComplete();
      }
      @Override
      public boolean test(Integer msg) {
        test.set(msg);
        return !paused.get();
      }
    };
    eventLoop.execute(() -> {
      paused.set(true);
      while (queue.write(count.getAndIncrement())) {
      }
      paused.set(false);
      queue.tryDrain();
    });
    await();
  }

  @Test
  public void testReentrantClose() {
    queue = new OutboundMessageQueue<>(eventLoop) {
      @Override
      public boolean test(Integer msg) {
        if (msg == 0) {
          write(1);
          close();
          write(2);
          assertEquals(List.of(2), output);
          return true;
        } else {
          return false;
        }
      }
      @Override
      protected void handleDispose(Integer elt) {
        output.add(elt);
      }
    };
    eventLoop.execute(() -> {
      queue.write(0);
      assertEquals(List.of(2, 1), output);
      testComplete();
    });
    await();
  }

  @Test
  public void testCloseWhileDrainScheduled() {
    AtomicInteger drains = new AtomicInteger();
    queue = new OutboundMessageQueue<>(eventLoop) {
      @Override
      public boolean test(Integer msg) {
        return false;
      }
      @Override
      protected void startDraining() {
        drains.incrementAndGet();
      }
    };
    eventLoop.execute(() -> {
      Thread thread = new Thread(() -> {
        int idx = 0;
        while (queue.write(idx++)) {
          //
        }
      });
      thread.start();
      try {
        thread.join();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      queue.close();
      eventLoop.execute(() -> {
        assertEquals(0, drains.get());
        testComplete();
      });
    });
    await();
  }
}
