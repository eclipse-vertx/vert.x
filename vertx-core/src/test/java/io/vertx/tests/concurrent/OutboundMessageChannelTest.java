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
import io.vertx.core.internal.concurrent.OutboundMessageChannel;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class OutboundMessageChannelTest extends VertxTestBase {

  private List<Integer> output = Collections.synchronizedList(new ArrayList<>());
  private OutboundMessageChannel<Integer> queue;
  private EventLoop eventLoop;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    disableThreadChecks();
    output = Collections.synchronizedList(new ArrayList<>());
    eventLoop = ((ContextInternal)vertx.getOrCreateContext()).nettyEventLoop();
  }

  @Test
  public void testReentrantWriteAccept() {
    queue = new OutboundMessageChannel<>(eventLoop) {
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
    queue = new OutboundMessageChannel<>(eventLoop) {
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
  public void testReentrantOverflowThenDrain() {
    AtomicInteger drains = new AtomicInteger();
    queue = new OutboundMessageChannel<>(eventLoop) {
      int reentrant = 0;
      @Override
      public boolean test(Integer msg) {
        assertEquals(0, reentrant++);
        try {
          output.add(msg);
          switch (msg) {
            case 0:
              int count = 1;
              while (write(count++)) {
              }
              assertEquals(16, count);
              assertEquals(0, drains.get());
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
      protected void afterDrain() {
        drains.incrementAndGet();
      }
    };
    eventLoop.execute(() -> {
      queue.write(0);
      assertEquals(1, drains.get());
      testComplete();
    });
    await();
  }

  @Test
  public void testReentrantClose() {
    queue = new OutboundMessageChannel<>(eventLoop) {
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
      protected void disposeMessage(Integer elt) {
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
    queue = new OutboundMessageChannel<>(eventLoop) {
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
    await();  }
}
