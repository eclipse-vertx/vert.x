package io.vertx.core.net.impl;

import io.netty.channel.EventLoop;
import io.vertx.core.streams.impl.OutboundWriteQueue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static io.vertx.core.streams.impl.OutboundWriteQueue.numberOfUnwritableSignals;

/**
 * Outbound write queue for event-loop and channel like structures.
 */
public class OutboundMessageQueue<T> implements Predicate<T> {

  private final EventLoop eventLoop;
  private final AtomicInteger numberOfUnwritableSignals = new AtomicInteger();
  private final OutboundWriteQueue<T> writeQueue;

  // State accessed exclusively by the event loop thread
  private boolean overflow;

  /**
   * Create a queue.
   *
   * @param eventLoop the queue event-loop
   */
  public OutboundMessageQueue(EventLoop eventLoop, Predicate<T> predicate) {
    this.eventLoop = eventLoop;
    this.writeQueue = new OutboundWriteQueue<>(predicate);
  }

  /**
   * Create a queue.
   *
   * @param eventLoop the queue event-loop
   */
  public OutboundMessageQueue(EventLoop eventLoop) {
    this.eventLoop = eventLoop;
    this.writeQueue = new OutboundWriteQueue<>(this);
  }

  @Override
  public boolean test(T t) {
    throw new UnsupportedOperationException();
  }

  /**
   * @return whether the queue is writable, this can be called from any thread
   */
  public boolean isWritable() {
    // Can be negative temporarily
    return numberOfUnwritableSignals.get() <= 0;
  }

  /**
   * Write a {@code message} to the queue
   *
   * @param message the message to be written
   * @return whether the writer can continue/stop writing to the queue
   */
  public final boolean write(T message) {
    boolean inEventLoop = eventLoop.inEventLoop();
    int flags;
    if (inEventLoop) {
      flags = writeQueue.add(message);
      overflow |= (flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0;
      if ((flags & OutboundWriteQueue.QUEUE_WRITABLE_MASK) != 0) {
        handleWriteQueueDrained(numberOfUnwritableSignals(flags));
      }
    } else {
      flags = writeQueue.submit(message);
      if ((flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0) {
        eventLoop.execute(this::drainWriteQueue);
      }
    }
    if ((flags & OutboundWriteQueue.QUEUE_UNWRITABLE_MASK) != 0) {
      int val = numberOfUnwritableSignals.incrementAndGet();
      return val <= 0;
    } else {
      return numberOfUnwritableSignals.get() <= 0;
    }
  }

  /**
   * Attempt to drain the queue Drain the queue.
   */
  public void drain() {
    assert(eventLoop.inEventLoop());
    if (overflow) {
      startDraining();
      int flags = writeQueue.drain();
      overflow = (flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0;
      if ((flags & OutboundWriteQueue.QUEUE_WRITABLE_MASK) != 0) {
        handleWriteQueueDrained(numberOfUnwritableSignals(flags));
      }
      stopDraining();
    }
  }

  private void drainWriteQueue() {
    startDraining();
    int flags = writeQueue.drain();
    overflow = (flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0;
    if ((flags & OutboundWriteQueue.QUEUE_WRITABLE_MASK) != 0) {
      handleWriteQueueDrained(numberOfUnwritableSignals(flags));
    }
    stopDraining();
  }

  private void handleWriteQueueDrained(int numberOfSignals) {
    int val = numberOfUnwritableSignals.addAndGet(-numberOfSignals);
    if ((val + numberOfSignals) > 0 && val <= 0) {
      writeQueueDrained();
    }
  }

  /**
   * Clear the queue.
   *
   * @return the pending writes
   */
  public final List<T> clear() {
    assert(eventLoop.inEventLoop());
    return writeQueue.clear();
  }

  /**
   * Called when the queue becomes writable again.
   */
  protected void writeQueueDrained() {
  }

  protected void startDraining() {
  }

  protected void stopDraining() {
  }
}
