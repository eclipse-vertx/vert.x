package io.vertx.core.internal.concurrent;

import io.netty.channel.EventLoop;
import io.vertx.core.streams.impl.MessageChannel;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static io.vertx.core.streams.impl.MessageChannel.numberOfUnwritableSignals;

/**
 * Outbound message channel for event-loop and channel like structures.
 */
public class OutboundMessageChannel<M> implements Predicate<M> {

  private final EventLoop eventLoop;
  private final AtomicInteger numberOfUnwritableSignals = new AtomicInteger();
  private final MessageChannel.MpSc<M> messageChannel;
  private volatile boolean eventuallyClosed;

  // State accessed exclusively by the event loop thread
  private boolean overflow; // Indicates channel ownership
  private int draining = 0; // Indicates drain in progress
  private boolean closed;

  /**
   * Create a channel.
   *
   * @param eventLoop the channel event-loop
   */
  public OutboundMessageChannel(EventLoop eventLoop) {
    this.eventLoop = eventLoop;
    this.messageChannel = new MessageChannel.MpSc<>(this);
  }

  /**
   * Create a channel.
   *
   * @param eventLoop the channel event-loop
   * @param lowWaterMark the low-water mark, must be positive
   * @param highWaterMark the high-water mark, must be greater than the low-water mark
   */
  public OutboundMessageChannel(EventLoop eventLoop, int lowWaterMark, int highWaterMark) {
    this.eventLoop = eventLoop;
    this.messageChannel = new MessageChannel.MpSc<>(this, lowWaterMark, highWaterMark);
  }

  @Override
  public boolean test(M msg) {
    throw new UnsupportedOperationException();
  }

  /**
   * @return whether the channel is writable, this can be called from any thread
   */
  public boolean isWritable() {
    // Can be negative temporarily
    return numberOfUnwritableSignals.get() <= 0;
  }

  /**
   * Write a {@code message} to the channel.
   *
   * @param message the message to be written
   * @return whether the writer can continue/stop writing to the channel
   */
  public final boolean write(M message) {
    boolean inEventLoop = eventLoop.inEventLoop();
    int flags;
    if (inEventLoop) {
      if (closed) {
        handleDispose(message);
        return true;
      }
      flags = messageChannel.add(message);
      if (draining == 0 && (flags & MessageChannel.DRAIN_REQUIRED_MASK) != 0) {
        flags = drainMessageQueue();
      }
    } else {
      if (eventuallyClosed) {
        handleDispose(message);
        return true;
      }
      flags = messageChannel.add(message);
      if ((flags & MessageChannel.DRAIN_REQUIRED_MASK) != 0) {
        eventLoop.execute(this::drain);
      }
    }
    int val;
    if ((flags & MessageChannel.UNWRITABLE_MASK) != 0) {
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
      int flags = messageChannel.drain();
      overflow |= (flags & MessageChannel.DRAIN_REQUIRED_MASK) != 0;
      if ((flags & MessageChannel.WRITABLE_MASK) != 0) {
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
    assert(eventLoop.inEventLoop());
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
    assert(eventLoop.inEventLoop());
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
      eventLoop.execute(this::handleDrained);
    }
  }

  private void releaseMessages() {
    List<M> messages = messageChannel.clear();
    for (M elt : messages) {
      handleDispose(elt);
    }
  }

  /**
   * Called when the channel becomes writable again.
   */
  protected void handleDrained() {
  }

  protected void startDraining() {
  }

  protected void stopDraining() {
  }

  /**
   * Release a message, this is called when the channel has been closed and message resource cleanup.
   *
   * @param msg the message
   */
  protected void handleDispose(M msg) {
  }
}
