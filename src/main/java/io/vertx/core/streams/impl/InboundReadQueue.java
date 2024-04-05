package io.vertx.core.streams.impl;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.impl.Arguments;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public class InboundReadQueue<E> {

  /**
   * Returns the number of times {@link #QUEUE_UNWRITABLE_MASK} signals encoded in {@code value}
   *
   * @param value the value
   * @return then number of unwritable signals
   */
  public static int numberOfPendingElements(int value) {
    return (value & ~0X1) >> 1;
  }

  public static int drainResult(int num, boolean writable) {
    return (writable ? QUEUE_WRITABLE_MASK : 0) | (num << 1);
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
  public static final int QUEUE_WRITABLE_MASK = 0x01;

  // NOW
  // el -> handle content -> dispatch and maybe pause
  // THEN
  // el -> dispatch and maybe pause -> handle content with test

  private final long highWaterMark;
  private final long lowWaterMark;
  private final AtomicLong wip = new AtomicLong(0L);
  private final Queue<E> queue = PlatformDependent.newSpscQueue(); // BUT COULD BE REGULAR QUEUE IF SAME CONTEXT THREAD ???
  private final Predicate<E> consumer;

  // Consumer/Producer thread -> rely on happens-before of task execution
  private E overflow;

  // Consumer thread
  private int writeQueueFull;

  public InboundReadQueue(Predicate<E> consumer) {
    this(consumer, DEFAULT_LOW_WATER_MARK, DEFAULT_HIGH_WATER_MARK);
  }

  public InboundReadQueue(Predicate<E> consumer, int lowWaterMark, int highWaterMark) {
    Arguments.require(lowWaterMark >= 0, "The low-water mark must be >= 0");
    Arguments.require(lowWaterMark <= highWaterMark, "The high-water mark must greater or equals to the low-water mark");
    this.lowWaterMark = lowWaterMark;
    this.highWaterMark = highWaterMark;
    this.consumer = Objects.requireNonNull(consumer);
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

  // Producer thread
  public int add(E element) {
    if (element == null) {
      throw new NullPointerException();
    }
    if (wip.compareAndSet(0L, 1L)) {
      overflow = element; // Do we need barrier ? should we always use the queue instead ???
      return DRAIN_REQUIRED_MASK;
    } else {
      queue.offer(element);
      long val = wip.incrementAndGet();
      if (val != 1) {
        return val == highWaterMark ? QUEUE_UNWRITABLE_MASK : 0; // Check branch-less
      }
      return DRAIN_REQUIRED_MASK;
    }
  }

  // Consumer thread

  /**
   * Drain the queue
   *
   */
  public int drain() {
    return drain(Long.MAX_VALUE);
  }

  /**
   * Drain the queue
   *
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
        return QUEUE_WRITABLE_MASK;
      }
      if (maxIter != Long.MAX_VALUE) {
        maxIter--;
      }
    }
    long pending = wip.get();
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
    long pending = wip.addAndGet(-amount);
    long size = pending + amount;
    if (size >= highWaterMark && (size - amount) < highWaterMark) {
      writeQueueFull++;
    }
    return pending;
  }
}
