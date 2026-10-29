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
package io.vertx.core.internal.streams;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.future.FutureImpl;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.concurrent.WriteOp;

/**
 * A write promise bridging {@link WriteResult} and {@link WriteOp}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class WritePromise extends FutureImpl<Void> implements PromiseInternal<Void>, WriteResult {

  private Handler<Void> drainHandler;
  private Handler<Void> disposeHandler;
  final WriteOp op;

  public WritePromise(ContextInternal context) {
    super(context);

    this.op = new WriteOp() {

      protected void handleDrain() {
        Handler<Void> handler = drainHandler;
        drainHandler = null;
        disposeHandler = null;
        context.runOnContext(handler);
      }

      protected void handleDispose() {
        Handler<Void> handler = disposeHandler;
        drainHandler = null;
        disposeHandler = null;
        context.runOnContext(handler);
      }

      @Override
      public void write() {
        WritePromise.this.write();
      }

      @Override
      public void cancel(Throwable cause) {
        WritePromise.this.cancel(cause);
      }
    };
  }

  @Override
  public boolean isWritable() {
    return op.isWritable();
  }

  @Override
  public WriteResult drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    if (op.drainHandlerSet()) {
      drainHandler = null;
    }
    return this;
  }

  @Override
  public WriteResult disposeHandler(Handler<Void> handler) {
    disposeHandler = handler;
    if (op.disposeHandlerSet()) {
      disposeHandler = null;
    }
    return this;
  }

  /**
   * Perform the write operation.
   */
  public abstract void write();

  /**
   * Cancel the write.
   *
   * @param cause the cancellation cause
   */
  public void cancel(Throwable cause) {
    tryFail(cause);
  }

  @Override
  public Future<Void> future() {
    return this;
  }

  @Override
  public void operationComplete(io.netty.util.concurrent.Future<Void> future) {
    if (future.isSuccess()) {
      complete(future.getNow());
    } else {
      fail(future.cause());
    }
  }
}
