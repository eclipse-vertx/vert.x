package io.vertx.core.internal.concurrent;

import io.vertx.core.internal.EventExecutor;

import java.util.ArrayDeque;
import java.util.Deque;

public abstract class OutboundWriteQueue<M> extends OutboundMessageQueue<M> {

  public OutboundWriteQueue(EventExecutor consumer) {
    super(consumer);
  }

  public OutboundWriteQueue(EventExecutor consumer, int lowWaterMark, int highWaterMark) {
    super(consumer, lowWaterMark, highWaterMark);
  }

  @Override
  public boolean test(M msg) {
    boolean written = writeImpl(msg);
    if (written && aboveOrEqualsHighWaterMark()) {
      if (toDrain == null) {
        toDrain = new ArrayDeque<>(4);
      }
      toDrain.add(msg);
    }
    return written;
  }

  private Deque<M> toDrain;

  protected abstract boolean writeImpl(M msg);

  @Override
  protected void stopDraining() {
    Deque<M> c = toDrain;
    if (c != null) {
      toDrain = null;
      fireDrains(c);
    }
  }

  protected abstract void fireDrains(Deque<M> toDrain);

}
