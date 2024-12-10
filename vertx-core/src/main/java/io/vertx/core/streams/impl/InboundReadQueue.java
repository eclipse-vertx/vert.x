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
package io.vertx.core.streams.impl;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.impl.Arguments;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Predicate;

/**
 * A concurrent single producer back-pressured queue fronting a single consumer back-pressured system.
 *
 * This queue lets a single producer emit elements ({@link #add}) to the consumer with back-pressure
 * to signal the producer when it should stop emitting. The consumer should consume elements with {@link #drain()}.
 *
 * <h3>Handling elements</h3>
 *
 * The consumer side uses a {@link Predicate} to handle elements and decide whether it can accept them. When a
 * consumer returns {@code false} it refuses the element and the queue will propose this element later when the
 * consumer signals it can accept elements again.
 *
 * <h3>Adding elements</h3>
 *
 * Only the producer thread can add elements. When the consumer threads adds an element to the queue it tries
 * to get the ownership of the queue. The {@link #add} method returns a signal indicating that the queue should be
 * drained to make progress: {@link #DRAIN_REQUIRED_MASK} signals the queue contains element that shall be drained.
 *
 * <h3>Draining elements</h3>
 *
 * The {@link #drain} method tries to remove elements from the queue until the consumer refuses them or the queue becomes empty.
 * When a method returns a {@link #DRAIN_REQUIRED_MASK} signal, the {@link #drain()} should be called by the consumer thread
 *
 * <ul>
 *   <li>The consumer method {@link #drain}) emits this signal when the {@link #consumer} refuses an element,
 *   therefore {@link #drain()} should be called again when the consumer can accept element</li>
 *   <li>The producer method ({@link #add}) emits this signal when it acquired the ownership of the queue to {@link #drain} the
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
 *   <li>the producer thread {@link #add)} to the queue above the {@link #highWaterMark}</li>
 *   <li>the {@link #consumer} refuses an element during a {@link #drain()}</li>
 * </ul>
 *
 * <p>When the internal queue grows above the {@link #highWaterMark}, the queue is considered as {@code unwritable}.
 * The {@link #add} method return {@link #QUEUE_UNWRITABLE_MASK} to signal the producer it should stop emitting.</p>
 *
 * <p>After a drain if the internal queue has shrunk under {@link #lowWaterMark}, the queue is considered as {@code writable}.
 * The {@link #drain} method return {@link #QUEUE_WRITABLE_MASK} to signal the producer should start emitting. Note
 * that the consumer thread handles this signal and should forward it to the producer.</p>
 */
public abstract class InboundReadQueue<E> {

  /**
   * Factory for a queue assuming distinct single consumer thread / single producer thread
   */
  public static final Factory SPSC = new Factory() {
    @Override
    public <T> InboundReadQueue<T> create(Predicate<T> consumer, int lowWaterMark, int highWaterMark) {
      return new SpSc<>(consumer, lowWaterMark, highWaterMark);
    }
    @Override
    public <T> InboundReadQueue<T> create(Predicate<T> consumer) {
      return new SpSc<>(consumer);
    }
  };

  /**
   * Factory for a queue assuming a same single consumer thread / single producer thread
   */
  public static final Factory SINGLE_THREADED = new Factory() {
    @Override
    public <T> InboundReadQueue<T> create(Predicate<T> consumer, int lowWaterMark, int highWaterMark) {
      return new SingleThread<>(consumer, lowWaterMark, highWaterMark);
    }
    @Override
    public <T> InboundReadQueue<T> create(Predicate<T> consumer) {
      return new SingleThread<>(consumer);
    }
  };

  /**
   * Returns the number of times {@link #QUEUE_UNWRITABLE_MASK} signals encoded in {@code value}
   *
   * @param value the value
   * @return then number of unwritable signals
   */
  public static int numberOfPendingElements(int value) {
    return (value & ~0X3) >> 2;
  }

  public static int drainResult(int num, boolean writable) {
    return (writable ? QUEUE_WRITABLE_MASK : 0) | (num > 0 ? DRAIN_REQUIRED_MASK : 0) | (num << 2);
  }

  /**
   * The default high-water mark value: {@code 16}
   */
  public static final int DEFAULT_HIGH_WATER_MARK = 16;

  /**
   * The default low-water mark value: {@code 8}
   */
  public static final int DEFAULT_LOW_WATER_MARK = 8;

  /**
   * When the masked bit is set, the caller has acquired the ownership of the queue and must drain it
   * to attempt to release the ownership.
   */
  public static final int DRAIN_REQUIRED_MASK = 0x01;

  /**
   * When the masked bit is set, the queue became unwritable, this triggers only when the queue transitions
   * from the <i>writable</i>> state to the <i>unwritable</i>> state.
   */
  public static final int QUEUE_UNWRITABLE_MASK = 0x02;

  /**
   * When the masked bit is set, the queue became writable, this triggers only when the queue transitions
   * from the <i>unwritable</i>> state to the <i>writable</i> state.
   */
  public static final int QUEUE_WRITABLE_MASK = 0x02;

  // NOW
  // el -> handle content -> dispatch and maybe pause
  // THEN
  // el -> dispatch and maybe pause -> handle content with test

  private final long highWaterMark;
  private final long lowWaterMark;
  private final Queue<E> queue;
  private final Predicate<E> consumer;

  // Consumer/Producer thread -> rely on happens-before of task execution
  private E overflow;

  // Consumer thread
  private int writeQueueFull;

  private InboundReadQueue(Queue<E> queue, Predicate<E> consumer, int lowWaterMark, int highWaterMark) {
    Arguments.require(lowWaterMark >= 0, "The low-water mark must be >= 0");
    Arguments.require(lowWaterMark <= highWaterMark, "The high-water mark must greater or equals to the low-water mark");
    this.queue = queue;
    this.lowWaterMark = lowWaterMark;
    this.highWaterMark = highWaterMark;
    this.consumer = Objects.requireNonNull(consumer);
  }

  /**
   * wip operations
   */
  protected abstract boolean wipCompareAndSet(long expect, long update);
  protected abstract long wipIncrementAndGet();
  protected abstract long wipDecrementAndGet();
  protected abstract long wipGet();
  protected abstract long wipAddAndGet(long delta);

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
   * Let the producer thread add the {@code element} to the queue.
   *
   * A set of flags is returned
   * <ul>
   *   <li>When {@link #QUEUE_UNWRITABLE_MASK} is set, the queue is writable and new elements can be added to the queue,
   *   otherwise no elements <i>should</i> be added to the queue nor submitted but it is a soft condition</li>
   *   <li>When {@link #DRAIN_REQUIRED_MASK} is set, the producer has acquired the ownership of the queue and should
   *   {@link #drain()} the queue.</li>
   * </ul>
   *
   * @param element the element to add
   * @return a bitset of [{@link #DRAIN_REQUIRED_MASK}, {@link #QUEUE_UNWRITABLE_MASK}] flags
   */
  public int add(E element) {
    if (element == null) {
      throw new NullPointerException();
    }
    if (wipCompareAndSet(0L, 1L)) {
      overflow = element; // Do we need barrier ? should we always use the queue instead ???
      return DRAIN_REQUIRED_MASK;
    } else {
      queue.offer(element);
      long val = wipIncrementAndGet();
      if (val != 1) {
        return val == highWaterMark ? QUEUE_UNWRITABLE_MASK : 0; // Check branch-less
      }
      return DRAIN_REQUIRED_MASK;
    }
  }

  public int drain() {
    return drain(Long.MAX_VALUE);
  }

  /**
   * Let the consumer thread drain the queue until it becomes empty or the consumer decided to stop, this does not require
   * the ownership, but it is recommenced to own the ownership of the queue.
   *
   * A set of flags is returned
   * <ul>
   *   <li>When {@link #QUEUE_WRITABLE_MASK} is set, the queue is writable again and new elements can be added to the queue
   *   by the producer thread, this requires an external cooperation between the producer and consumer thread.</li>
   *   <li>When {@link #DRAIN_REQUIRED_MASK} is set, the producer still has the ownership of the queue and should
   *   {@link #drain()} the queue again.</li>
   * </ul>
   *
   * @return a bitset of [{@link #QUEUE_WRITABLE_MASK}, {@link #DRAIN_REQUIRED_MASK}] flags
   */
  public int drain(long maxIter) {
    if (maxIter < 0L) {
      throw new IllegalArgumentException();
    }
    if (maxIter == 0L) {
      return 0;
    }
    E elt = overflow;
    if (elt != null) {
      if (!consumer.test(elt)) {
        return drainResult((int)wipGet(), false); // TEST THIS
      }
      overflow = null;
      if (consume(1) == 0L) {
        return drainResult(0, false); // WRITABLE => false
      }
      if (maxIter != Long.MAX_VALUE) {
        maxIter--;
      }
    }
    long pending = wipGet();
    do {
      int consumed;
      for (consumed = 0;consumed < pending && maxIter > 0L;consumed++) {
        elt = queue.poll();
        if (maxIter != Long.MAX_VALUE) {
          maxIter--;
        }
        if (!consumer.test(elt)) {
          overflow = elt;
          break;
        }
      }
      pending = consume(consumed);
    } while (pending != 0 && overflow == null && maxIter > 0L);
    boolean writabilityChanged = pending < lowWaterMark && writeQueueFull > 0; // SEEMS INCORRECT
    if (writabilityChanged) {
      writeQueueFull = 0;
    }
    return drainResult((int)pending, writabilityChanged);
  }

  private long consume(int amount) {
    long pending = wipAddAndGet(-amount);
    long size = pending + amount;
    if (size >= highWaterMark && (size - amount) < highWaterMark) {
      writeQueueFull++;
    }
    return pending;
  }

  /**
   * Factory for {@link InboundReadQueue}.
   */
  public interface Factory {
    <E> InboundReadQueue<E> create(Predicate<E> consumer, int lowWaterMark, int highWaterMark);
    <E> InboundReadQueue<E> create(Predicate<E> consumer);
  }

  private static class SingleThread<E> extends InboundReadQueue<E> {

    private long wip;

    public SingleThread(Predicate<E> consumer) {
      this(consumer, DEFAULT_LOW_WATER_MARK, DEFAULT_HIGH_WATER_MARK);
    }

    public SingleThread(Predicate<E> consumer, int lowWaterMark, int highWaterMark) {
      super(new ArrayDeque<>(1), consumer, lowWaterMark, highWaterMark);
    }

    @Override
    protected boolean wipCompareAndSet(long expect, long update) {
      if (wip == expect) {
        wip = update;
        return true;
      }
      return false;
    }

    @Override
    protected long wipIncrementAndGet() {
      return ++wip;
    }

    @Override
    protected long wipDecrementAndGet() {
      return --wip;
    }

    @Override
    protected long wipGet() {
      return wip;
    }

    @Override
    protected long wipAddAndGet(long delta) {
      return wip += delta;
    }
  }

  private static class SpSc<E> extends InboundReadQueue<E> {

    private static final AtomicLongFieldUpdater<SpSc<?>> WIP_UPDATER = (AtomicLongFieldUpdater<SpSc<?>>) (AtomicLongFieldUpdater)AtomicLongFieldUpdater.newUpdater(SpSc.class, "wip");

    private volatile long wip;

    public SpSc(Predicate<E> consumer) {
      this(consumer, DEFAULT_LOW_WATER_MARK, DEFAULT_HIGH_WATER_MARK);
    }
    public SpSc(Predicate<E> consumer, int lowWaterMark, int highWaterMark) {
      super(PlatformDependent.newSpscQueue(), consumer, lowWaterMark, highWaterMark);
    }

    @Override
    protected boolean wipCompareAndSet(long expect, long update) {
      return WIP_UPDATER.compareAndSet(this, expect, update);
    }

    @Override
    protected long wipIncrementAndGet() {
      return WIP_UPDATER.incrementAndGet(this);
    }

    @Override
    protected long wipDecrementAndGet() {
      return WIP_UPDATER.decrementAndGet(this);
    }

    @Override
    protected long wipGet() {
      return WIP_UPDATER.get(this);
    }

    @Override
    protected long wipAddAndGet(long delta) {
      return WIP_UPDATER.addAndGet(this, delta);
    }
  }
}
