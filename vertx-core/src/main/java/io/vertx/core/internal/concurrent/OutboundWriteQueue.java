package io.vertx.core.internal.concurrent;

import io.vertx.core.internal.EventExecutor;
import io.vertx.core.streams.impl.MessagePassingQueue;

import java.util.ArrayDeque;
import java.util.Deque;

public abstract class OutboundWriteQueue<M extends WriteOp> extends OutboundMessageQueue<M> {

  private static final Deque<?> EMPTY = new ArrayDeque<>(0);

  private final int lowWaterMark;
  private final int highWaterMark;

  // State accessed exclusively by the consumer thread
  @SuppressWarnings("unchecked")
  private Deque<M> collected = (Deque<M>) EMPTY;
  private int promoted;
  private int maxSeq;
  private boolean lastWrite;

  public OutboundWriteQueue(EventExecutor consumer) {
    this(consumer, MessagePassingQueue.DEFAULT_LOW_WATER_MARK, MessagePassingQueue.DEFAULT_HIGH_WATER_MARK);
  }

  public OutboundWriteQueue(EventExecutor consumer, int lowWaterMark, int highWaterMark) {
    super(consumer, lowWaterMark, highWaterMark);

    this.lowWaterMark = lowWaterMark;
    this.highWaterMark = highWaterMark;
  }

  @Override
  public void stamp(M element, int depth, int seq) {
    element.depth = depth;
    element.seq = seq;
    element.writable = depth < highWaterMark;
  }

  protected abstract boolean handleWrite(M msg);

  protected void handleDrain(M msg) {
    msg.onDrain();
  }

  protected void handleDispose(M msg) {
    msg.onDispose();
  }

  protected void handleCancel(M msg, Throwable cause) {
    msg.cancel(cause);
  }

  @Override
  public boolean test(M msg) {
    int depth = msg.depth;
    int seq = msg.seq;
    if (depth < lowWaterMark && !collected.isEmpty() && seq - maxSeq > 0) {
      promoted = collected.size();
    }
    boolean written;
    try {
      written = handleWrite(msg);
    } catch (Exception e) {
      handleCancel(msg, e);
      written = true;
    }
    lastWrite = written;
    if (written && depth >= highWaterMark) {
      if (collected == EMPTY) {
        collected = new ArrayDeque<>(4);
      }
      maxSeq += Math.max(0, seq - maxSeq);
      collected.add(msg);
    }
    return written;
  }

  @Override
  protected void handleClose() {
    promoted = 0;
    M msg;
    while ((msg = collected.poll()) != null) {
      handleDispose(msg);
    }
  }

  @Override
  protected void stopDraining() {
    if (lastWrite) {
      // The drain loop only stops on an empty queue or on a refusal
      promoted = collected.size();
    }
    while (promoted > 0) {
      promoted--;
      handleDrain(collected.poll());
    }
  }

  @Override
  protected void handleRelease(M msg) {
    handleDispose(msg);
  }
}
