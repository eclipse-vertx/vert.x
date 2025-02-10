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
package io.vertx.core.internal.concurrent;

import io.vertx.core.impl.EventLoopExecutor;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.streams.impl.MessageChannel;

import java.util.List;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Predicate;

/**
 * Inbound message channel for event-loop and read stream like structures.
 */
public class InboundMessageChannel<M> implements Predicate<M>, Runnable {

  private static final AtomicLongFieldUpdater<InboundMessageChannel<?>> DEMAND_UPDATER = (AtomicLongFieldUpdater<InboundMessageChannel<?>>) (AtomicLongFieldUpdater)AtomicLongFieldUpdater.newUpdater(InboundMessageChannel.class, "demand");

  private final EventExecutor consumer;
  private final EventExecutor producer;
  private final MessageChannel<M> messageChannel;

  // Accessed by produced thread
  private boolean producerClosed;

  // Accessed by consumer thread
  private boolean draining;
  private boolean needsDrain;
  private boolean consumerClosed;

  // Any thread
  private volatile long demand = Long.MAX_VALUE;

  public InboundMessageChannel(EventExecutor producer, EventExecutor consumer) {
    MessageChannel.Factory messageChannelFactory;
    if (consumer instanceof EventLoopExecutor && producer instanceof EventLoopExecutor && ((EventLoopExecutor)consumer).eventLoop() == ((EventLoopExecutor)producer).eventLoop()) {
      messageChannelFactory = MessageChannel.SINGLE_THREAD;
    } else {
      messageChannelFactory = MessageChannel.SPSC;
    }
    this.messageChannel = messageChannelFactory.create(this);
    this.consumer = consumer;
    this.producer = producer;
  }

  public InboundMessageChannel(EventExecutor producer, EventExecutor consumer, MessageChannel.Factory messageChannelFactory) {
    this.messageChannel = messageChannelFactory.create(this);
    this.consumer = consumer;
    this.producer = producer;
  }

  public InboundMessageChannel(EventExecutor producer, EventExecutor consumer, int lowWaterMark, int highWaterMark) {
    MessageChannel.Factory messageChannelFactory;
    if (consumer instanceof EventLoopExecutor && producer instanceof EventLoopExecutor && ((EventLoopExecutor)consumer).eventLoop() == ((EventLoopExecutor)producer).eventLoop()) {
      messageChannelFactory = MessageChannel.SINGLE_THREAD;
    } else {
      messageChannelFactory = MessageChannel.SPSC;
    }
    this.messageChannel = messageChannelFactory.create(this, lowWaterMark, highWaterMark);
    this.consumer = consumer;
    this.producer = producer;
  }

  @Override
  public final boolean test(M msg) {
    if (consumerClosed) {
      return false;
    } else {
      while (true) {
        long d = DEMAND_UPDATER.get(this);
        if (d == 0L) {
          return false;
        } else if (d == Long.MAX_VALUE || DEMAND_UPDATER.compareAndSet(this, d, d - 1)) {
          break;
        }
      }
      handleMessage(msg);
      return true;
    }
  }

  /**
   * Add a message to the channel
   *
   * @param msg the message
   * @return {@code true} when a {@link #drain()} should be called.
   */
  public final boolean add(M msg) {
    assert producer.inThread();
    if (producerClosed) {
      handleDispose(msg);
      return false;
    }
    int res = messageChannel.add(msg);
    if ((res & MessageChannel.UNWRITABLE_MASK) != 0) {
      handlePause();
    }
    return (res & MessageChannel.DRAIN_REQUIRED_MASK) != 0;
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
   * Schedule a drain operation on the consumer thread, this method assumes a consumer thread.
   */
  public final void drain() {
    assert producer.inThread();
    if (producerClosed) {
      return;
    }
    if (consumer.inThread()) {
      drainInternal();
    } else {
      consumer.execute(this::drainInternal);
    }
  }

  /**
   * Task executed from context thread, this should not be called directly.
   */
  @Override
  public void run() {
    assert consumer.inThread();
    if (!draining && needsDrain) {
      drainInternal();
    }
  }

  private void drainInternal() {
    if (consumerClosed) {
      return;
    }
    draining = true;
    try {
      int res = messageChannel.drain();
      if (consumerClosed) {
        releaseMessages();
      } else {
        needsDrain = (res & MessageChannel.DRAIN_REQUIRED_MASK) != 0;
        if ((res & MessageChannel.WRITABLE_MASK) != 0) {
          if (producer.inThread()) {
            handleResume();
          } else {
            producer.execute(this::handleResume);
          }
        }
      }
    } finally {
      draining = false;
    }
  }

  /**
   * Clear the demand.
   */
  public final void pause() {
    DEMAND_UPDATER.set(this, 0L);
  }

  /**
   * Add {@code amount} to the current demand.
   *
   * @param amount the number of message to consume
   */
  public final void fetch(long amount) {
    if (amount < 0L) {
      throw new IllegalArgumentException("Invalid amount: " + amount);
    }
    if (amount > 0L) {
      while (true) {
        long prev = DEMAND_UPDATER.get(this);
        long next = prev + amount;
        if (next < 0L) {
          next = Long.MAX_VALUE;
        }
        if (prev == next || DEMAND_UPDATER.compareAndSet(this, prev, next)) {
          break;
        }
      }
      consumer.execute(this);
    }
  }

  /**
   * Close the channel.
   */
  public final void close() {
    if (!producer.inThread()) {
      producer.execute(this::close);
      return;
    }
    closeProducer();
    if (consumer.inThread()) {
      closeConsumer();
    } else {
      consumer.execute(this::closeConsumer);
    }
  }

  /**
   * Close the producer side, this must be called from the producer thread
   */
  public final void closeProducer() {
    assert producer.inThread();
    if (producerClosed) {
      return;
    }
    producerClosed = true;
  }

  /**
   * Close the consumer side, this must be called from the consumer thread
   */
  public final void closeConsumer() {
    assert consumer.inThread();
    if (consumerClosed) {
      return;
    }
    consumerClosed = true;
    if (!draining) {
      releaseMessages();
    }
  }

  private void releaseMessages() {
    List<M> messages = messageChannel.clear();
    for (M elt : messages) {
      handleDispose(elt);
    }
  }

  /**
   * Handle resume, executed on a producer thread.
   */
  protected void handleResume() {
  }

  /**
   * Handler pause, executed on a producer thread.
   */
  protected void handlePause() {
  }

  /**
   * Handle a message, executed on a consumer thread.
   *
   * @param msg the message
   */
  protected void handleMessage(M msg) {
  }

  /**
   * Dispose a message, this is called when the channel has been closed and message resource cleanup. No specific
   * thread assumption can be made on this callback.
   *
   * @param msg the message to dispose
   */
  // Todo : try remove this
  protected void handleDispose(M msg) {
  }
}
