package io.vertx.core.internal.streams;

import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.concurrent.OutboundWriteQueue;
import io.vertx.core.internal.concurrent.WriteOp;

public abstract class OutboundWriteStream extends OutboundWriteQueue<WriteOp> {

  public OutboundWriteStream(EventExecutor consumer) {
    super(consumer);
  }

  public OutboundWriteStream(EventExecutor consumer, int lowWaterMark, int highWaterMark) {
    super(consumer, lowWaterMark, highWaterMark);
  }

  public void add(WritePromise write) {
    add(write.op);
  }
}
