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
package io.vertx.core.net.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.future.FutureImpl;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.streams.WriteResult;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A write promise bridging {@link WriteResult} and {@link WriteOp}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class WritePromise extends FutureImpl<Void> implements WriteOp, PromiseInternal<Void>, WriteResult {

  private static final int DRAIN_HANDLER_MASK = 0x1;
  private static final int DISPOSE_HANDLER_MASK = 0x2;
  private static final int SIGNALED_MASK = 0x4;
  private static final int CLOSED_MASK = 0x8;
  private static final int FIRED_MASK = 0x10;

  public WritePromise(ContextInternal context) {
    super(context);
  }

  private int depth;
  private int seq;
  private Handler<Void> drainHandler;
  private Handler<Void> disposeHandler;
  private final AtomicInteger status = new AtomicInteger();
  private boolean writable;

  @Override
  public void init(int depth, int seq, boolean writable) {
    this.depth = depth;
    this.seq = seq;
    this.writable = writable;
  }

  @Override
  public void onDrain() {
    signal(true);
  }

  @Override
  public void onDispose() {
    signal(false);
  }

  private void signal(boolean writable) {
    int handlerBit = writable ? DRAIN_HANDLER_MASK : DISPOSE_HANDLER_MASK;
    while (true) {
      int st = status.get();
      if ((st & SIGNALED_MASK) != 0) {
        return;
      }
      int next = st | SIGNALED_MASK | (writable ? 0 : CLOSED_MASK);
      boolean fire = (st & handlerBit) != 0;
      if (fire) {
        next |= FIRED_MASK;
      }
      if (status.compareAndSet(st, next)) {
        if (fire) {
          Handler<Void> handler = writable ? drainHandler : disposeHandler;
          drainHandler = null;
          disposeHandler = null;
          context.runOnContext(handler);
        }
        return;
      }
    }
  }

  @Override
  public int depth() {
    return depth;
  }

  @Override
  public int seq() {
    return seq;
  }

  @Override
  public boolean isWritable() {
    if ((status.get() & CLOSED_MASK) != 0) {
      return false;
    } else {
      if (depth == 0) {
        throw new IllegalStateException("Write was not enqueued through a depth-aware queue");
      }
      return writable;
    }
  }

  @Override
  public WriteResult drainHandler(Handler<Void> handler) {
    return handler(handler, DRAIN_HANDLER_MASK, false);
  }

  @Override
  public WriteResult disposeHandler(Handler<Void> handler) {
    return handler(handler, DISPOSE_HANDLER_MASK, true);
  }

  private WriteResult handler(Handler<Void> handler, int handlerBit, boolean closed) {
    if (handler == null) {
      throw new UnsupportedOperationException();
    }
    if (depth == 0 && (status.get() & CLOSED_MASK) == 0) {
      throw new IllegalStateException("Write was not enqueued through a depth-aware queue");
    }
    while (true) {
      int st = status.get();
      if ((st & handlerBit) != 0) {
        throw new IllegalStateException();
      }
      if ((st & SIGNALED_MASK) != 0) {
        if (((st & CLOSED_MASK) != 0) != closed) {
          // The signal does not match this handler: never called
          return this;
        } else {
          if ((st & FIRED_MASK) != 0) {
            throw new IllegalStateException();
          }
          if (status.compareAndSet(st, st | handlerBit | FIRED_MASK)) {
            context.runOnContext(handler);
            return this;
          }
        }
      } else {
        if (handlerBit == DRAIN_HANDLER_MASK) {
          drainHandler = handler;
        } else {
          disposeHandler = handler;
        }
        if (status.compareAndSet(st, st | handlerBit)) {
          return this;
        }
      }
    }
  }

  @Override
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
