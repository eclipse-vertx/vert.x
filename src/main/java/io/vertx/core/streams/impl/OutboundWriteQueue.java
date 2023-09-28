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

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.impl.Arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Predicate;

/**
 * A concurrent multi producers back-pressured queue fronting a single consumer back-pressured system.
 *
 * The queue lets multiple producers emit elements ({@link #add}/{@link #submit}) to the consumer with back-pressure
 * to signal producers when they should stop emitting.
 *
 * <h3>Handling elements</h3>
 *
 * The consumer side uses a {@link Predicate} to handle elements and decide whether it can accept them. When a
 * consumer returns {@code false} it refuses the element and the queue will propose this element later when the
 * consumer signals it can accept elements again.
 *
 * <h3>Emitting elements</h3>
 *
 * Elements are emitted with {@link #add} and {@link #submit} methods.
 *
 * <h4>Adding elements</h4>
 *
 * Only the consumer thread can add elements. When the consumer threads adds an element to the queue it tries
 * to get the ownership of the queue and directly invoke the {@link #consumer}. When ownership
 * is not acquired, the element is added to the queue, until it is handled by the consumer thread later.
 *
 * <h4>Submitting elements</h4>
 *
 * When a producer thread submits an element to the queue, the element is added to the queue to let the consumer
 * thread handle it.
 *
 * <h3>Queue ownership</h3>
 *
 * Some operation require to <i>own</i> the queue to execute them. We already mentioned that {@link #add} and {@link #submit}
 * attempts to get ownership of the queue, sometimes these methods will not release ownership when they exit, because the queue
 * expects another thread or another condition to be met to make progress. Emitting methods can return a signal indicating that
 * the queue should be drained to make progress: {@link #DRAIN_REQUIRED_MASK} signals the queue contains element that
 * shall be drained.
 *
 * <h3>Draining elements</h3>
 *
 * The {@link #drain} method tries to remove elements from the queue until the consumer refuses them or the queue becomes empty.
 * When a method returns a {@link #DRAIN_REQUIRED_MASK} signal, the {@link #drain()} should be called by the consumer thread
 *
 * <ul>
 *   <li>A consumer method ({@link #add}, {@link #drain}) emits this signal when the {@link #consumer} refuses an element,
 *   therefore {@link #drain()} should be called when the consumer can accept element again</li>
 *   <li>A producer method ({@link #submit}) emits this signal when it acquired the ownership of the queue to {@link #drain} the
 *   queue.</li>
 * </ul>
 *
 * <h3>Back-pressure</h3>
 *
 * <p>Producers emission flow cannot reliably be controlled by the consumer back-pressure probe since producers
 * emissions are transferred to the consumer thread: the consumer back-pressure controller does not take in account the
 * inflight elements between producers and consumer. This queue is designed to provide reliable signals to control
 * producers emission based on the consumer back-pressure probe and the number of inflight elements between
 * producers and the consumer.</p>
 *
 * The queue maintains an internal queue of elements initially empty and can be filled when
 * <ul>
 *   <li>producer threads {@link #submit)} to the queue above the {@link #highWaterMark}</li>
 *   <li>the {@link #consumer} refuses an element</li>
 * </ul>
 *
 * <p>When the internal queue grows above the {@link #highWaterMark}, the queue is considered as {@code unwritable}.
 * {@link #add}/{@link #submit} methods return {@link #QUEUE_UNWRITABLE_MASK} to signal producers should stop emitting.</p>
 *
 * <p>After a drain if the internal queue has shrunk under {@link #lowWaterMark}, the queue is considered as {@code writable}.
 * {@link #add}/{@link #drain} methods return {@link #QUEUE_WRITABLE_MASK} to signal producers should start emitting. Note
 * that the consumer thread handles this signal and should forward it to the producers.</p>
 *
 * <p>When {@link #QUEUE_WRITABLE_MASK} is signalled, the number of {@link #QUEUE_UNWRITABLE_MASK} signals emitted is encoded
 * in the flags. This number allows the producer flow controller to correctly account the producer writability:
 * {@link #QUEUE_WRITABLE_MASK}/{@link #QUEUE_UNWRITABLE_MASK} signals are observed by different threads, therefore a good
 * practice to compute the queue writability is to increment/decrement an atomic counter for each signal received.</p>
 */
public class OutboundWriteQueue<E> {

  /**
   * Returns the number of times {@link #QUEUE_UNWRITABLE_MASK} signals encoded in {@code value}
   *
   * @param value the value
   * @return then number of unwritable signals
   */
  public static int numberOfUnwritableSignals(int value) {
    return (value & ~0XF) >> 4;
  }

  /**
   * When the masked bit is set, the queue became unwritable, this triggers only when the queue transitions
   * from the <i>writable</i>> state to the <i>unwritable</i>> state.
   */
  public static final int QUEUE_UNWRITABLE_MASK = 0x01;

  /**
   * When the masked bit is set, the queue became writable, this triggers only when the queue transitions
   * from the <i>unwritable</i>> state to the <i>writable</i> state.
   */
  public static final int QUEUE_WRITABLE_MASK = 0x02;

  /**
   * When the masked bit is set, the caller has acquired the ownership of the queue and must drain it
   * to attempt to release the ownership.
   */
  public static final int DRAIN_REQUIRED_MASK = 0x04;

  /**
   * The default high-water mark value: {@code 16}
   */
  public static final int DEFAULT_HIGH_WATER_MARK = 16;

  /**
   * The default low-water mark value: {@code 8}
   */
  public static final int DEFAULT_LOW_WATER_MARK = 8;

  private static final AtomicLongFieldUpdater<OutboundWriteQueue<?>> WIP_UPDATER = (AtomicLongFieldUpdater<OutboundWriteQueue<?>>) (AtomicLongFieldUpdater)AtomicLongFieldUpdater.newUpdater(OutboundWriteQueue.class, "wip");

  // Immutable

  private final Predicate<E> consumer;
  private final long highWaterMark;
  private final long lowWaterMark;

  // Concurrent part accessed by any producer thread

  private final Queue<E> queue = PlatformDependent.newMpscQueue();
  private volatile long wip = 0L;

  // Consumer thread only

  // The element refused by the consumer (null <=> overflow)
  private E overflow;
  // The number of times the queue was observed to be unwritable
  private long writeQueueFull;

  /**
   * Create a new instance.
   *
   * @param consumer the predicate accepting the elements
   * @throws NullPointerException if consumer is null
   */
  public OutboundWriteQueue(Predicate<E> consumer) {
    this(consumer, DEFAULT_LOW_WATER_MARK, DEFAULT_HIGH_WATER_MARK);
  }

  /**
   * Create a new instance.
   *
   * @param consumer the predicate accepting the elements
   * @param lowWaterMark the low-water mark, must be zero or positive
   * @param highWaterMark the high-water mark, must be greater than the low-water mark
   *
   * @throws NullPointerException if consumer is null
   * @throws IllegalArgumentException if any mark violates the condition
   */
  public OutboundWriteQueue(Predicate<E> consumer, long lowWaterMark, long highWaterMark) {
    Arguments.require(lowWaterMark >= 0, "The low-water mark must be >= 0");
    Arguments.require(lowWaterMark <= highWaterMark, "The high-water mark must greater or equals to the low-water mark");
    this.consumer = Objects.requireNonNull(consumer, "Consumer must be not null");
    this.lowWaterMark = lowWaterMark;
    this.highWaterMark = highWaterMark;
  }

  /**
   * @return the queue high-water mark
   */
  public long highWaterMark() {
    return highWaterMark;
  }

  /**
   * @return the queue low-water mark
   */
  public long lowWaterMark() {
    return lowWaterMark;
  }

  /**
   * Let the consumer thread add the {@code element} to the queue.
   *
   * A set of flags is returned
   * <ul>
   *   <li>When {@link #QUEUE_UNWRITABLE_MASK} is set, the queue is writable and new elements can be added to the queue,
   *   otherwise no elements <i>should</i> be added to the queue nor submitted but it is a soft condition</li>
   *   <li>When {@link #DRAIN_REQUIRED_MASK} is set, the queue overflow</li>
   * </ul>
   *
   * @param element the element to add
   * @return a bitset of [{@link #DRAIN_REQUIRED_MASK}, {@link #QUEUE_UNWRITABLE_MASK}, {@link #QUEUE_WRITABLE_MASK}] flags
   */
  public int add(E element) {
    if (WIP_UPDATER.compareAndSet(this, 0, 1)) {
      if (!consumer.test(element)) {
        overflow = element;
        return DRAIN_REQUIRED_MASK | (WIP_UPDATER.get(this) == highWaterMark ? QUEUE_UNWRITABLE_MASK : 0);
      }
      if (consume(1) == 0) {
        return 0;
      }
      return drainLoop();
    } else {
      queue.add(element);
      long v = WIP_UPDATER.incrementAndGet(this);
      if (v == 1) {
        return drainLoop();
      } else {
        return v == highWaterMark ? QUEUE_UNWRITABLE_MASK : 0;
      }
    }
  }

  /**
   * Let a producer thread submit an {@code element} to the queue, no delivery is attempted. Submitting an element
   * might acquire the ownership of the queue, when it happens a {@link #drain} operation should be called
   * to drain the queue and release the ownership.
   *
   * @param element the element to submit
   * @return a bitset of [{@link {@link #QUEUE_UNWRITABLE_MASK }, {@link #DRAIN_REQUIRED_MASK }}] flags
   */
  public int submit(E element) {
    queue.add(element);
    // winning this => overflow == null
    long pending = WIP_UPDATER.incrementAndGet(this);
    if (pending == highWaterMark) {
      hook2();
    }
    return (pending == highWaterMark ? QUEUE_UNWRITABLE_MASK : 0) + ((pending == 1) ? DRAIN_REQUIRED_MASK : 0);
  }

  protected void hook2() {

  }

  /**
   * Let the consumer thread drain the queue until it becomes not writable or empty, this requires
   * the ownership of the queue acquired by {@link #DRAIN_REQUIRED_MASK} flag.
   *
   * @return a bitset of [{@link #DRAIN_REQUIRED_MASK}, {@link #QUEUE_WRITABLE_MASK}] flags
   */
  public int drain() {
    E elt = overflow;
    if (elt != null) {
      if (!consumer.test(overflow)) {
        return DRAIN_REQUIRED_MASK;
      }
      overflow = null;
      if (consume(1) == 0) {
        return 0;
      }
    }
    hook();
    return drainLoop();
  }

  /**
   * The main drain loop, entering this loop requires a few conditions:
   * <ul>
   *   <li>{@link #overflow} must be {@code null}, the consumer still accepts elements</li>
   *   <li>{@link #wip} is greater than zero (ownership of the queue)</li>
   *   <li>only the consumer thread can execute it</li>
   * </ul>
   *
   * The loop drains elements from the queue until
   *
   * <ul>
   *   <li>the queue is empty (wip == 0) which releases the queue ownership</li>
   *   <li>the {@link #consumer} rejects an element</li>
   * </ul>
   *
   * When the {@link #consumer} rejects an element, the rejected element is parked
   * in the {@link #overflow} field and the queue ownership is not released. At this point
   * the {@link #drain()} shall be called to try again to drain the queue.
   *
   * @return a bitset of [{@link {@link #QUEUE_WRITABLE_MASK }, {@link #CONSUMER_PAUSED_MASK }}] flags
   */
  // Note : we can optimize this by passing pending as argument of this method to avoid the initial
  private int drainLoop() {
    long pending = WIP_UPDATER.get(this);
    if (pending == 0) {
      throw new IllegalStateException();
    }
    do {
      int consumed;
      for (consumed = 0; consumed < pending; consumed++) {
        E elt = queue.poll();
        if (!consumer.test(elt)) {
          overflow = elt;
          break;
        }
      }
      // the trick is to decrement the wip at once on each iteration in order to count the number of times producers
      // have observed a QUEUE_UNWRITABLE_MASK signal
      pending = consume(consumed);
    } while (pending != 0 && overflow == null);
    boolean writabilityChanged = pending < lowWaterMark && writeQueueFull > 0;
    long val = writeQueueFull << 4;
    if (writabilityChanged) {
      writeQueueFull = 0;
    }
    int flags = 0;
    flags |= overflow != null ? DRAIN_REQUIRED_MASK : 0;
    flags |= writabilityChanged ? QUEUE_WRITABLE_MASK : 0;
    flags |= val;
    return flags;
  }

  /**
   * Consume a number of elements from the queue, this method updates the queue {@link #writeQueueFull} counter.
   *
   * @param amount the amount to consume
   * @return the number of pending elements after consuming from the queue
   */
  private long consume(int amount) {
    long pending = WIP_UPDATER.addAndGet(this, -amount);
    long size = pending + amount;
    if (size >= highWaterMark && (size - amount) < highWaterMark) {
      writeQueueFull++;
    }
    return pending;
  }

  protected void hook() {

  }

  /**
   * Clear the queue and return all the removed elements.
   *
   * @return the removed elements.
   */
  public final List<E> clear() {
    writeQueueFull = 0;
    List<E> elts = new ArrayList<>();
    if (overflow != null) {
      elts.add(overflow);
      overflow = null;
      if (WIP_UPDATER.decrementAndGet(this) == 0) {
        return elts;
      }
    }
    for (long pending = WIP_UPDATER.get(this);pending != 0;pending = WIP_UPDATER.addAndGet(this, -pending)) {
      for (int i = 0;i < pending;i++) {
        elts.add(queue.poll());
      }
    }
    return elts;
  }
}
