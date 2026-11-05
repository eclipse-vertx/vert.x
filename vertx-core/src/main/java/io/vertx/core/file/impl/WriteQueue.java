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
package io.vertx.core.file.impl;

import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.concurrent.WriteOp;
import io.vertx.core.internal.streams.OutboundWriteStream;

/**
 * The file outbound write queue: paces the transit by element count as usual, while the byte budget - the
 * file analog of the channel writability - gates the writes handed to the channel. The budget is accessed
 * exclusively on the context thread: charged when a write is performed, refunded when its I/O completes,
 * the refund resuming the drain.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WriteQueue extends OutboundWriteStream {

  private final ContextInternal context;
  volatile Handler<Void> drainHandler;
  volatile int maxWrites = 128 * 1024;    // TODO - we should tune this for best performance
  private long writesOutstanding;
  private Runnable shutdownCompletion;

  WriteQueue(ContextInternal context) {
    super(context.executor());
    this.context = context;
  }

  @Override
  protected boolean write(WriteOp op) {
    if (writesOutstanding >= maxWrites) {
      // The budget is exhausted: park the op, refunds resume the drain
      return false;
    }
    op.write();
    return true;
  }

  @Override
  protected void handleDrained() {
    Handler<Void> handler = drainHandler;
    if (handler != null) {
      // Associate the context: the queue schedules this hook as a raw event-loop task
      context.dispatch(null, handler);
    }
  }

  @Override
  protected void release(WriteOp op) {
    // Close flush: write the released element directly, bypassing the budget - like the legacy
    // unconditional initiation; its parked result is disposed by the queue afterwards
    op.write();
  }

  void charge(long bytes) {
    writesOutstanding += bytes;
  }

  void refund(long bytes) {
    writesOutstanding -= bytes;
    if (writesOutstanding == 0 && shutdownCompletion != null) {
      Runnable completion = shutdownCompletion;
      shutdownCompletion = null;
      completion.run();
    } else {
      tryDrain();
    }
  }

  /**
   * Close the queue, flushing the queued writes, and run {@code completion} once the outstanding
   * I/O has fully refunded the budget.
   */
  void shutdown(Runnable completion) {
    close();
    if (writesOutstanding == 0) {
      completion.run();
    } else {
      shutdownCompletion = completion;
    }
  }
}
