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
package io.vertx.core.streams.impl;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class InboundReadQueue<E> {

  private final Consumer<E> consumer;
  private boolean paused;
  private final Queue<E> queue;
  private final AtomicInteger inflight = new AtomicInteger();
  private final long highWaterMark = 8;
  private final long lowWaterMark = 4;

  public InboundReadQueue(Consumer<E> consumer) {
    this.consumer = consumer;
    this.queue = new ArrayDeque<>();
  }

  // Producer thread
  public boolean add(E elt) {
    int s = inflight.getAndIncrement();
    if (s > 0) {
      queue.add(elt);
      boolean full = queue.size() < highWaterMark;
      paused |= full;
      return full;
    }
    consumer.accept(elt);
    return true;
  }

  // Producer thread
  public boolean drain() {
    while (true) {
      int s = inflight.get();
      if (s > queue.size()) {
        break;
      }
      E elt = queue.poll();
      if (elt == null) {
        break;
      }
      consumer.accept(elt);
    }
    boolean resume = paused && queue.size() < lowWaterMark;
    paused &= !resume;
    return resume;
  }

  /**
   * Ack the element.
   *
   * @return the next element to ack or drain ?
   */
  // Any thread
  public boolean ack(E elt) {
    // Should check we cannot do more than that!
    int val = inflight.decrementAndGet();
    assert val >= 0;
    return val > 0;
  }
}
