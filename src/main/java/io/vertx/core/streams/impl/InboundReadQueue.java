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
 * todo : write the rest of the doc
 *
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
    protected long wipGet() {
      return WIP_UPDATER.get(this);
    }

    @Override
    protected long wipAddAndGet(long delta) {
      return WIP_UPDATER.addAndGet(this, delta);
    }
  }

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
   * Let the consumer thread drain the queue until it becomes not writable or empty, this does not require
   * the ownership, but it is recommenced to possess the ownership of the queue.
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
        return 0;
      }
      overflow = null;
      if (consume(1) == 0L) {
        return 0;
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
    boolean writabilityChanged = pending < lowWaterMark && writeQueueFull > 0;
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
}
