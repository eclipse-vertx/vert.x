/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.concurrent;

import io.vertx.core.internal.EventExecutor;
import io.vertx.core.streams.impl.MessagePassingQueue;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * An outbound message queue delivering per-write back-pressure.
 *
 * <p>Each added {@link WriteOp} is stamped at add time with the queue depth and add order its own atomic
 * increment observed, giving the producer an exact writability verdict for that write. The consumer writes
 * every element on arrival; an element written above the high-water mark owes a drain signal, collected and
 * fired at the end of a drain once the queue was witnessed below the low-water mark after the whole collected
 * batch, or once a drain emptied the queue. A drain ended by a downstream refusal holds the collected batch:
 * writers are not woken onto a stuck downstream. On close, queued elements and the held batch are disposed.
 *
 * <p>Every op signals exactly once: drained after it was written, or disposed.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
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

  /**
   * Attempt the write operation downstream.
   *
   * <p>Returning {@code false} means the write was not performed and the element is retested on a later
   * drain. A throw is caught by the queue: the op is cancelled with the failure and counted as written.
   *
   * @param msg the op to write
   * @return whether the write was performed
   */
  protected abstract boolean write(M msg);

  /**
   * Signal the release of {@code msg}, called when the queue releases an op unwritten, before the op is
   * disposed.
   *
   * @param msg the released op
   */
  protected void release(M msg) {
  }

  /**
   * Signal the beginning of a drain.
   */
  protected void beforeDrain() {
  }

  /**
   * Signal the end of a drain, before the collected drain signals fire.
   */
  protected void afterDrain() {
  }

  @Override
  protected final void startDraining() {
    beforeDrain();
  }

  @Override
  public final boolean test(M msg) {
    int depth = msg.depth;
    int seq = msg.seq;
    if (depth < lowWaterMark && !collected.isEmpty() && seq - maxSeq > 0) {
      promoted = collected.size();
    }
    boolean written;
    try {
      written = write(msg);
    } catch (Exception e) {
      msg.cancel(e);
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
  protected final void handleClose() {
    promoted = 0;
    M msg;
    while ((msg = collected.poll()) != null) {
      msg.onDispose();
    }
  }

  @Override
  protected final void stopDraining() {
    afterDrain();
    if (lastWrite) {
      // The drain loop only stops on an empty queue or on a refusal
      promoted = collected.size();
    }
    while (promoted > 0) {
      promoted--;
      collected
        .poll()
        .onDrain();
    }
  }

  @Override
  protected final void handleRelease(M msg) {
    try {
      release(msg);
    } finally {
      msg.onDispose();
    }
  }
}
