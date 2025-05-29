package io.vertx.core.internal.concurrent;

import io.vertx.core.internal.EventExecutor;
import io.vertx.core.streams.impl.MessagePassingQueue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static io.vertx.core.streams.impl.MessagePassingQueue.numberOfUnwritableSignals;

/**
 * Outbound message queue for event-loop and write stream like structures.
 */
public class OutboundMessageQueue<M> implements Predicate<M> {

  private final EventExecutor consumer;
  private final AtomicInteger numberOfUnwritableSignals = new AtomicInteger();
  private final MessagePassingQueue.MpSc<M> mqp;
  private volatile boolean eventuallyClosed;

  // State accessed exclusively by the event loop thread
  private boolean overflow; // Indicates queue ownership
  private int draining = 0; // Indicates drain in progress
  private boolean closed;

  /**
   * Create a queue.
   *
   * @param consumer the queue event-loop
   */
  public OutboundMessageQueue(EventExecutor consumer) {
    this.consumer = consumer;
    this.mqp = new MessagePassingQueue.MpSc<>(this);
  }

  /**
   * Create a queue.
   *
   * @param consumer the queue event-loop
   * @param lowWaterMark the low-water mark, must be positive
   * @param highWaterMark the high-water mark, must be greater than the low-water mark
   */
  public OutboundMessageQueue(EventExecutor consumer, int lowWaterMark, int highWaterMark) {
    this.consumer = consumer;
    this.mqp = new MessagePassingQueue.MpSc<>(this, lowWaterMark, highWaterMark);
  }

  @Override
  public boolean test(M msg) {
    throw new UnsupportedOperationException();
  }

  /**
   * @return whether the queue is writable, this can be called from any thread
   */
  public final boolean isWritable() {
    // Can be negative temporarily
    return numberOfUnwritableSignals.get() <= 0;
  }

  /**
   * Write a {@code message} to the queue.
   *
   * @param message the message to be written
   * @return whether the writer can continue/stop writing to the queue
   */
  public final boolean write(M message) {
    boolean inEventLoop = consumer.inThread();
    int flags;
    if (inEventLoop) {
      if (closed) {
        handleDispose(message);
        return true;
      }
      flags = mqp.add(message);
      if (draining == 0 && (flags & MessagePassingQueue.DRAIN_REQUIRED_MASK) != 0) {
        flags = drainMessageQueue();
      }
    } else {
      if (eventuallyClosed) {
        handleDispose(message);
        return true;
      }
      flags = mqp.add(message);
      if ((flags & MessagePassingQueue.DRAIN_REQUIRED_MASK) != 0) {
        consumer.execute(this::drain);
      }
    }
    int val;
    if ((flags & MessagePassingQueue.UNWRITABLE_MASK) != 0) {
      val = numberOfUnwritableSignals.incrementAndGet();
    } else {
      val = numberOfUnwritableSignals.get();
    }
    return val <= 0;
  }

  /**
   * Synchronous message queue drain.
   */
  private int drainMessageQueue() {
    draining++;
    try {
      int flags = mqp.drain();
      overflow |= (flags & MessagePassingQueue.DRAIN_REQUIRED_MASK) != 0;
      if ((flags & MessagePassingQueue.WRITABLE_MASK) != 0) {
        handleDrained(numberOfUnwritableSignals(flags));
      }
      return flags;
    } finally {
      draining--;
      if (draining == 0 && closed) {
        releaseMessages();
      }
    }
  }

  private void drain() {
    if (closed) {
      return;
    }
    assert(draining == 0);
    startDraining();
    drainMessageQueue();
    stopDraining();
  }

  /**
   * Attempts to drain the queue.
   */
  public final boolean tryDrain() {
    assert(consumer.inThread());
    if (overflow) {
      overflow = false;
      drain();
      return true;
    } else {
      return false;
    }
  }

  /**
   * Close the queue.
   */
  public final void close() {
    assert(consumer.inThread());
    if (closed) {
      return;
    }
    closed = true;
    eventuallyClosed = true;
    if (draining > 0) {
      return;
    }
    releaseMessages();
  }

  private void handleDrained(int numberOfSignals) {
    int val = numberOfUnwritableSignals.addAndGet(-numberOfSignals);
    if ((val + numberOfSignals) > 0 && val <= 0) {
      consumer.execute(this::handleDrained);
    }
  }

  private void releaseMessages() {
    List<M> messages = mqp.clear();
    for (M elt : messages) {
      handleDispose(elt);
    }
  }

  /**
   * Called when the queue becomes writable again.
   */
  protected void handleDrained() {
  }

  protected void startDraining() {
  }

  protected void stopDraining() {
  }

  /**
   * Release a message, this is called when the queue has been closed and message resource cleanup.
   *
   * @param msg the message
   */
  protected void handleDispose(M msg) {
  }
}
