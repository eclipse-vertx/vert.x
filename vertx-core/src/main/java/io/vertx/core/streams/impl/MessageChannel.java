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

import java.util.*;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Predicate;

/**
 * A concurrent back-pressured channel fronting a single consumer back-pressured system that comes with three flavors
 *
 * <ul>
 *   <li>{@link MessageChannel.MpSc multiple producer / single consumer}</li>
 *   <li>{@link MessageChannel.SpSc single producer / single consumer}</li>
 *   <li>{@link MessageChannel.SingleThread single thread producer and consumer}</li>
 * </ul>
 *
 * The channel lets the producer ({@link #add}) elements to the consumer with back-pressure to signal the producer
 * when it should stop emitting. The consumer should consume elements with {@link #drain()}.
 *
 * <h4>Producing elements</h4>
 *
 * <p>We call producer any thread allowed to call {@link #add(Object)} according to the channel flavor.</p>
 *
 * <p>The producer adds elements to the channel with {@link #add}. When the producer adds an element to the channel it attempts
 * to acquire the ownership of the channel. When the producer acquires ownership, it must signal the consumer that the
 * channel needs to be drained.</p>
 *
 * <h3>Consuming element</h3>
 *
 * <p>The consumer uses a {@link Predicate} to handle an element and decide whether to accept it. When a consumer predicate
 * refuses an element, the channel will propose it again later when the consumer signals it can accept again.</p>
 *
 * <p>The consumer drains elements from the channel with {@link #drain}. The consumer can only drain elements when it has
 * ownership of the channel. Ownership of the channel should be signaled by a producer thread that acquired it, or because
 * the {@link #drain()} indicated that the predicate refused an element and the channel should be drained again later.</p>.
 *
 * <h3>Channel ownership</h3>
 *
 * Channel interactions  ({@link #add} and {@link #drain}) returns a set of flags, one of them {@link #DRAIN_REQUIRED_MASK}
 * is ownership. When such interaction returns the {@link #DRAIN_REQUIRED_MASK} bit set, ownership has been acquired and
 * drain should be attempted.
 *
 * <ul>
 *   <li>When {@link #add} acquires ownership, the producer should signal the consumer that the channel must be drained.</li>
 *   <li>{@link #drain} must be called only with ownership, its return code can maintain the ownership: the channel consumer
 *   predicate refused an element and the consumer keeps ownership of the channel.</li>
 * </ul>
 *
 * <h3>Back-pressure</h3>
 *
 * <p>Producers emission flow cannot reliably be controlled by the consumer back-pressure probe since producers
 * emissions are transferred to the consumer thread: the consumer back-pressure controller does not take in account the
 * inflight elements between producers and consumer. This channel is designed to provide reliable signals to control
 * producers emission based on the consumer back-pressure probe and the number of inflight elements between
 * producers and the consumer.</p>
 *
 * The channel maintains an internal queue of elements initially empty and can be filled when
 * <ul>
 *   <li>A producer thread {@link #add)} to the queue above the {@link #highWaterMark}</li>
 *   <li>the {@link #consumer} refuses an element</li>
 * </ul>
 *
 * <p>When the internal queue grows above {@link #highWaterMark}, the queue is considered as {@code unwritable}.
 * {@link #add} returns {@link #UNWRITABLE_MASK} to signal producers should stop emitting.</p>
 *
 * <p>After a drain if the internal queue has shrunk under {@link #lowWaterMark}, the channel is considered as {@code writable}.
 * {@link #add}/{@link #drain} methods return {@link #WRITABLE_MASK} to signal producers should start emitting again. Note
 * that the consumer thread handles this signal and should forward it to the producers.</p>
 *
 * <p>When {@link #WRITABLE_MASK} is signalled, the number of {@link #UNWRITABLE_MASK} signals emitted is encoded
 * in the flags. This number allows the producer flow controller to correctly account the producer writability:
 * {@link #WRITABLE_MASK}/{@link #UNWRITABLE_MASK} signals are observed by different threads, therefore a good
 * practice to compute the channel writability is to increment/decrement an atomic counter for each signal received.</p>
 */
public abstract class MessageChannel<E> {

  /**
   * When the masked bit is set, the channel became unwritable, this triggers only when the channel transitions
   * from the <i>writable</i>> state to the <i>unwritable</i>> state.
   */
  public static final int UNWRITABLE_MASK = 0x01;

  /**
   * When the masked bit is set, the channel became writable, this triggers only when the channel transitions
   * from the <i>unwritable</i>> state to the <i>writable</i> state.
   */
  public static final int WRITABLE_MASK = 0x02;

  /**
   * When the masked bit is set, the caller has acquired the ownership of the channel and must drain it
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

  // Immutable

  private final Predicate<E> consumer;
  protected final long highWaterMark;
  protected final long lowWaterMark;

  // Concurrent part accessed by any producer thread

  final Queue<E> queue;

  // The element refused by the consumer (null <=> overflow)
  // it is shared between producer/consumer and visibility is ensured with the ownership flag
  // when the add method acquires ownership, it signals the consumer to drain the channel and therefore
  // implies a happens-before relationship:
  // 1. producer writes overflow
  // 2. producer signals consumer ownership
  // 3. consumer reads overflow
  private E overflow;

  // Consumer thread only
  // The number of times the channel was observed to be unwritable, this is accessed from consumer
  // todo : false sharing between overflow and writeQueueFull
  private int numberOfUnwritableTimes;

  /**
   * Create a new instance.
   *
   * @param consumer the predicate accepting the elements
   * @param lowWaterMark the low-water mark, must be positive
   * @param highWaterMark the high-water mark, must be greater than the low-water mark
   *
   * @throws NullPointerException if consumer is null
   * @throws IllegalArgumentException if any mark violates the condition
   */
  public MessageChannel(Queue<E> queue, Predicate<E> consumer, long lowWaterMark, long highWaterMark) {
    Arguments.require(lowWaterMark > 0, "The low-water mark must be > 0");
    Arguments.require(lowWaterMark <= highWaterMark, "The high-water mark must greater or equals to the low-water mark");
    this.queue = queue;
    this.consumer = Objects.requireNonNull(consumer, "Consumer must be not null");
    this.lowWaterMark = lowWaterMark;
    this.highWaterMark = highWaterMark;
  }

  /**
   * @return the channel high-water mark
   */
  public long highWaterMark() {
    return highWaterMark;
  }

  /**
   * @return the channel low-water mark
   */
  public long lowWaterMark() {
    return lowWaterMark;
  }

  /**
   * Let the producer add an {@code element} to the channel.
   *
   * A set of flags is returned
   * <ul>
   *   <li>When {@link #UNWRITABLE_MASK} is set, the channel is writable and new elements can be added to the channel,
   *   otherwise no elements <i>should</i> be added to the channel nor submitted, however it is a soft condition</li>
   *   <li>When {@link #DRAIN_REQUIRED_MASK} is set, the producer has acquired the ownership of the channel and should
   *   {@link #drain()} the channel.</li>
   * </ul>
   *
   * @param element the element to add
   * @return a bitset of [{@link #DRAIN_REQUIRED_MASK}, {@link #UNWRITABLE_MASK}] flags
   */
  public int add(E element) {
    if (element == null) {
      throw new NullPointerException();
    }
    long val;
    if (wipCompareAndSet(0L, 1L)) {
      overflow = element;
      val = 1;
    } else {
      queue.add(element);
      val = wipIncrementAndGet();
    }
    if (val != 1) {
      return val == highWaterMark ? UNWRITABLE_MASK : 0; // Check branch-less
    }
    return DRAIN_REQUIRED_MASK | (1 == highWaterMark ? UNWRITABLE_MASK : 0);
  }

  /**
   * Calls {@link #drain(long)} with {@code Long.MAX_VALUE}.
   */
  public int drain() {
    return drain(Long.MAX_VALUE);
  }

  /**
   * Let the consumer thread drain the channel until it becomes not writable or empty, this requires
   * the ownership of the channel acquired by {@link #DRAIN_REQUIRED_MASK} flag.
   *
   * @return a bitset of [{@link #DRAIN_REQUIRED_MASK}, {@link #WRITABLE_MASK}] flags
   */
  public int drain(long maxIter) {
    if (maxIter < 0L) {
      throw new IllegalArgumentException();
    }
    if (maxIter == 0L) {
      return 0; // Really ?
    }
    E elt = overflow;
    if (elt != null) {
      if (!consumer.test(elt)) {
        return drainResult((int)wipGet());
      }
      overflow = null;
      if (consume(1) == 0L) {
        return drainResult(0);
      }
      if (maxIter != Long.MAX_VALUE) {
        maxIter--;
      }
    }
    hook();
    return drainLoop(maxIter);
  }

  /**
   * The main drain loop, entering this loop requires a few conditions:
   * <ul>
   *   <li>{@link #overflow} must be {@code null}, the consumer still accepts elements</li>
   *   <li>{@link #wip} is greater than zero (ownership of the channel)</li>
   *   <li>only the consumer thread can execute it</li>
   * </ul>
   *
   * The loop drains elements from the channel until
   *
   * <ul>
   *   <li>the channel is empty (wip == 0) which releases the channel ownership</li>
   *   <li>the {@link #consumer} rejects an element</li>
   * </ul>
   *
   * When the {@link #consumer} rejects an element, the rejected element is parked
   * in the {@link #overflow} field and the channel ownership is not released. At this point
   * the {@link #drain()} shall be called to try again to drain the channel.
   *
   * @return a bitset of [{@link {@link #WRITABLE_MASK }, {@link #CONSUMER_PAUSED_MASK }}] flags
   */
  // Note : we can optimize this by passing pending as argument of this method to avoid the initial
  private int drainLoop(long maxIter) {
    E elt;
    long pending = wipGet();
//    if (pending == 0) {
//      throw new IllegalStateException();
//    }
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
    return drainResult((int)pending);
  }

  private int drainResult(int pending) {
    boolean writabilityChanged = pending < lowWaterMark && numberOfUnwritableTimes > 0;
    int val = numberOfUnwritableTimes;
    if (writabilityChanged) {
      numberOfUnwritableTimes = 0;
    }
    return drainResult(val, pending, writabilityChanged);
  }

  /**
   * Consume a number of elements from the queue, this method updates the queue {@link #numberOfUnwritableTimes} counter.
   *
   * @param amount the amount to consume
   * @return the number of pending elements after consuming from the queue
   */
  private long consume(int amount) {
    long pending = wipAddAndGet(-amount);
    long size = pending + amount;
    if (size >= highWaterMark && (size - amount) < highWaterMark) {
      numberOfUnwritableTimes++;
    }
    return pending;
  }

  protected void hook() {

  }

  /**
   * Clear the channel and return all the removed elements.
   *
   * @return the removed elements.
   */
  public final List<E> clear() {
    // From event loop thread
    numberOfUnwritableTimes = 0;
    List<E> elts = new ArrayList<>();
    if (overflow != null) {
      elts.add(overflow);
      overflow = null;
      if (wipDecrementAndGet() == 0) {
        return elts;
      }
    }
    for (long pending = wipGet();pending != 0;pending = wipAddAndGet(-pending)) {
      for (int i = 0;i < pending;i++) {
        elts.add(queue.poll());
      }
    }
    return elts;
  }

  // Should be internal
  public static int drainResult(int numberOfUnwritableSignals, int numberOfPendingElements, boolean writable) {
    return
      (writable ? WRITABLE_MASK : 0) |
      (numberOfPendingElements > 0 ? DRAIN_REQUIRED_MASK : 0) |
      (numberOfPendingElements << 3) |
      numberOfUnwritableSignals << 16;
  }

  /**
   * Returns the number of pending elements encoded in {@code value}
   *
   * @param value the value
   * @return then number of unwritable signals
   */
  public static int numberOfPendingElements(int value) {
    return (value & 0xFFF8) >> 3;
  }

  /**
   * Returns the number of times {@link #UNWRITABLE_MASK} signals encoded in {@code value}
   *
   * @param value the value
   * @return then number of unwritable signals
   */
  public static int numberOfUnwritableSignals(int value) {
    return (value & 0xFFFF0000) >> 16;
  }

  /**
   * WIP operations
   */
  protected abstract boolean wipCompareAndSet(long expect, long update);
  protected abstract long wipIncrementAndGet();
  protected abstract long wipDecrementAndGet();
  protected abstract long wipGet();
  protected abstract long wipAddAndGet(long delta);

  /**
   * Factory for {@link MessageChannel}.
   */
  public interface Factory {
    <E> MessageChannel<E> create(Predicate<E> consumer, int lowWaterMark, int highWaterMark);
    <E> MessageChannel<E> create(Predicate<E> consumer);
  }

  public static class SingleThread<E> extends MessageChannel<E> {

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

  public static class MpSc<E> extends MessageChannel<E> {

    private static final AtomicLongFieldUpdater<MessageChannel.MpSc<?>> WIP_UPDATER = (AtomicLongFieldUpdater<MessageChannel.MpSc<?>>) (AtomicLongFieldUpdater)AtomicLongFieldUpdater.newUpdater(MessageChannel.MpSc.class, "wip");

    // Todo : check false sharing
    private volatile long wip;

    public MpSc(Predicate<E> consumer) {
      this(consumer, DEFAULT_LOW_WATER_MARK, DEFAULT_HIGH_WATER_MARK);
    }
    public MpSc(Predicate<E> consumer, int lowWaterMark, int highWaterMark) {
      super(PlatformDependent.newMpscQueue(), consumer, lowWaterMark, highWaterMark);
    }

    /**
     * Let the consumer thread add the {@code element} to the channel.
     *
     * A set of flags is returned
     * <ul>
     *   <li>When {@link #UNWRITABLE_MASK} is set, the channel is writable and new elements can be added to the channel,
     *   otherwise no elements <i>should</i> be added to the channel nor submitted but it is a soft condition</li>
     *   <li>When {@link #DRAIN_REQUIRED_MASK} is set, the channel contains at least one element and must be drained</li>
     * </ul>
     *
     * @param element the element to add
     * @return a bitset of [{@link #DRAIN_REQUIRED_MASK}, {@link #UNWRITABLE_MASK}, {@link #WRITABLE_MASK}] flags
     */
    public int write(E element) {
      int res = add(element);
      if ((res & DRAIN_REQUIRED_MASK) != 0) {
        return drain();
      } else {
        return res;
      }
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

  public static class SpSc<E> extends MessageChannel<E> {

    private static final AtomicLongFieldUpdater<MessageChannel.SpSc<?>> WIP_UPDATER = (AtomicLongFieldUpdater<MessageChannel.SpSc<?>>) (AtomicLongFieldUpdater)AtomicLongFieldUpdater.newUpdater(MessageChannel.SpSc.class, "wip");

    // Todo : check false sharing
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

  /**
   * Factory for a channel assuming distinct single consumer thread / single producer thread
   */
  public static final Factory MPSC = new Factory() {
    @Override
    public <T> MessageChannel<T> create(Predicate<T> consumer, int lowWaterMark, int highWaterMark) {
      return new MpSc<>(consumer, lowWaterMark, highWaterMark);
    }
    @Override
    public <T> MessageChannel<T> create(Predicate<T> consumer) {
      return new MpSc<>(consumer);
    }
  };

  /**
   * Factory for a channel assuming distinct single consumer thread / single producer thread
   */
  public static final Factory SPSC = new Factory() {
    @Override
    public <T> MessageChannel<T> create(Predicate<T> consumer, int lowWaterMark, int highWaterMark) {
      return new SpSc<>(consumer, lowWaterMark, highWaterMark);
    }
    @Override
    public <T> MessageChannel<T> create(Predicate<T> consumer) {
      return new SpSc<>(consumer);
    }
  };

  /**
   * Factory for a channel assuming a same single consumer thread / single producer thread
   */
  public static final Factory SINGLE_THREAD = new Factory() {
    @Override
    public <T> MessageChannel<T> create(Predicate<T> consumer, int lowWaterMark, int highWaterMark) {
      return new SingleThread<>(consumer, lowWaterMark, highWaterMark);
    }
    @Override
    public <T> MessageChannel<T> create(Predicate<T> consumer) {
      return new SingleThread<>(consumer);
    }
  };
}
