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
package io.vertx.core.net.impl;

import io.netty.channel.EventLoop;
import io.vertx.core.ThreadingModel;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.streams.impl.InboundReadQueue;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Inbound message queue for event-loop and read stream like structures.
 */
public class InboundMessageQueue<M> implements Predicate<M>, Runnable {

  private final ContextInternal context;
  private final EventLoop eventLoop;
  private final InboundReadQueue<M> readQueue;

  // Accessed by context thread
  private boolean needsDrain;
  private boolean draining;

  // Any thread
  private final AtomicLong demand = new AtomicLong(Long.MAX_VALUE);

  public InboundMessageQueue(EventLoop eventLoop, ContextInternal context) {
    InboundReadQueue.Factory readQueueFactory;
    if (context.threadingModel() == ThreadingModel.EVENT_LOOP && context.nettyEventLoop() == eventLoop) {
      readQueueFactory = InboundReadQueue.SINGLE_THREADED;
    } else {
      readQueueFactory = InboundReadQueue.SPSC;
    }
    this.readQueue = readQueueFactory.create(this);
    this.context = context;
    this.eventLoop = eventLoop;
  }

  public InboundMessageQueue(EventLoop eventLoop, ContextInternal context, int lowWaterMark, int highWaterMark) {
    InboundReadQueue.Factory readQueueFactory;
    if (context.threadingModel() == ThreadingModel.EVENT_LOOP && context.nettyEventLoop() == eventLoop) {
      readQueueFactory = InboundReadQueue.SINGLE_THREADED;
    } else {
      readQueueFactory = InboundReadQueue.SPSC;
    }
    this.readQueue = readQueueFactory.create(this, lowWaterMark, highWaterMark);
    this.context = context;
    this.eventLoop = eventLoop;
  }

  @Override
  public boolean test(M msg) {
    while (true) {
      long d = demand.get();
      if (d == 0L) {
        return false;
      } else if (d == Long.MAX_VALUE || demand.compareAndSet(d, d - 1)) {
        break;
      }
    }
    handleMessage(msg);
    return true;
  }

  /**
   * Handle resume, executed on the event-loop thread.
   */
  protected void handleResume() {
  }

  /**
   * Handler pause, executed on the event-loop thread
   */
  protected void handlePause() {
  }

  /**
   * Handle a message, executed on the context thread
   *
   * @param msg the message
   */
  protected void handleMessage(M msg) {
  }

  /**
   * Add a message to the queue
   *
   * @param msg the message
   * @return {@code true} when a {@link #drain()} should be called.
   */
  public final boolean add(M msg) {
    assert eventLoop.inEventLoop();
    int res = readQueue.add(msg);
    if ((res & InboundReadQueue.QUEUE_UNWRITABLE_MASK) != 0) {
      handlePause();
    }
    return (res & InboundReadQueue.DRAIN_REQUIRED_MASK) != 0;
  }

  /**
   * {@link #add(Object)} + {@link #drain()}.
   *
   * @param messages the messages
   */
  public final void write(Iterable<M> messages) {
    boolean drain = false;
    for (M msg : messages) {
      drain |= add(msg);
    }
    if (drain) {
      drain();
    }
  }

  /**
   * {@link #add(Object)} + {@link #drain()}.
   *
   * @param msg the message
   */
  public final void write(M msg) {
    if (add(msg)) {
      drain();
    }
  }

  /**
   * Schedule a drain operation on the context thread.
   */
  public void drain() {
    assert eventLoop.inEventLoop();
    if (context.inThread()) {
      drainInternal();
    } else {
      context.execute(this::drainInternal);
    }
  }

  /**
   * Task executed from context thread.
   */
  @Override
  public void run() {
    assert context.inThread();
    if (!draining && needsDrain) {
      drainInternal();
    }
  }

  private void drainInternal() {
    draining = true;
    try {
      int res = readQueue.drain();
      needsDrain = (res & InboundReadQueue.DRAIN_REQUIRED_MASK) != 0;
      if ((res & InboundReadQueue.QUEUE_WRITABLE_MASK) != 0) {
        eventLoop.execute(this::handleResume);
      }
    } finally {
      draining = false;
    }
  }

  /**
   * Stop demand.
   */
  public void pause() {
    demand.set(0L);
  }

  /**
   * Set an absolute demand.
   *
   * @param value the new demand
   */
  public void demand(long value) {
    if (value < 0L) {
      throw new IllegalArgumentException();
    }
    demand.set(value);
  }

  /**
   * Add {@code amount} to the current demand.
   *
   * @param amount the number of message to consume
   */
  public void fetch(long amount) {
    if (amount < 0L) {
      throw new IllegalArgumentException();
    }
    if (amount == 0L) {
      return;
    }
    while (true) {
      long prev = demand.get();
      long next = prev + amount;
      if (next < 0L) {
        next = Long.MAX_VALUE;
      }
      if (prev == next || demand.compareAndSet(prev, next)) {
        break;
      }
    }
    context
      .executor()
      .execute(this);
  }
}
