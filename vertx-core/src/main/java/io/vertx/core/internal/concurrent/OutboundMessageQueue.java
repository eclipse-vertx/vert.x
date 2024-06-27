package io.vertx.core.internal.concurrent;

import io.netty.channel.EventLoop;
import io.vertx.core.streams.impl.OutboundWriteQueue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static io.vertx.core.streams.impl.OutboundWriteQueue.numberOfUnwritableSignals;

/**
 * Outbound write queue for event-loop and channel like structures.
 */
public class OutboundMessageQueue<M> implements Predicate<M> {

  private final EventLoop eventLoop;
  private final AtomicInteger numberOfUnwritableSignals = new AtomicInteger();
  private final OutboundWriteQueue<M> writeQueue;
  private volatile boolean eventuallyClosed;

  // State accessed exclusively by the event loop thread
  private boolean overflow;
  private boolean closed;
  private int reentrant = 0;

  /**
   * Create a queue.
   *
   * @param eventLoop the queue event-loop
   */
  public OutboundMessageQueue(EventLoop eventLoop, Predicate<M> predicate) {
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
  public boolean test(M msg) {
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
  public final boolean write(M message) {
    boolean inEventLoop = eventLoop.inEventLoop();
    int flags;
    if (inEventLoop) {
      if (closed) {
        disposeMessage(message);
        return true;
      }
      reentrant++;
      try {
        flags = writeQueue.add(message);
        overflow |= (flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0;
        if ((flags & OutboundWriteQueue.QUEUE_WRITABLE_MASK) != 0) {
          handleWriteQueueDrained(numberOfUnwritableSignals(flags));
        }
      } finally {
        reentrant--;
      }
      if (reentrant == 0 && closed) {
        releaseMessages();
      }
    } else {
      if (eventuallyClosed) {
        disposeMessage(message);
        return true;
      }
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
      reentrant++;
      int flags;
      try {
        flags = writeQueue.drain();
        overflow = (flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0;
        if ((flags & OutboundWriteQueue.QUEUE_WRITABLE_MASK) != 0) {
          handleWriteQueueDrained(numberOfUnwritableSignals(flags));
        }
      } finally {
        reentrant--;
      }
      stopDraining();
      if (reentrant == 0 && closed) {
        releaseMessages();
      }
    }
  }

  /**
   * Close the queue.
   */
  public final void close() {
    assert(eventLoop.inEventLoop());
    if (closed) {
      return;
    }
    closed = true;
    eventuallyClosed = true;
    if (reentrant > 0) {
      return;
    }
    releaseMessages();
  }

  private void drainWriteQueue() {
    startDraining();
    reentrant++;
    int flags;
    try {
      flags = writeQueue.drain();
      overflow = (flags & OutboundWriteQueue.DRAIN_REQUIRED_MASK) != 0;
      if ((flags & OutboundWriteQueue.QUEUE_WRITABLE_MASK) != 0) {
        handleWriteQueueDrained(numberOfUnwritableSignals(flags));
      }
    } finally {
      reentrant--;
    }
    stopDraining();
    if (reentrant == 0 && closed) {
      releaseMessages();
    }
  }

  private void handleWriteQueueDrained(int numberOfSignals) {
    int val = numberOfUnwritableSignals.addAndGet(-numberOfSignals);
    if ((val + numberOfSignals) > 0 && val <= 0) {
      writeQueueDrained();
    }
  }

  private void releaseMessages() {
    List<M> messages = writeQueue.clear();
    for (M elt : messages) {
      disposeMessage(elt);
    }
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

  /**
   * Release a message, this is called when the queue has been closed and message resource cleanup.
   *
   * @param msg the message
   */
  protected void disposeMessage(M msg) {
  }
}
