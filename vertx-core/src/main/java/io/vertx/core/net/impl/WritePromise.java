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
 * A write promise bridging {@link WriteResult} and {@link Write}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class WritePromise extends FutureImpl<Void> implements Write, PromiseInternal<Void>, WriteResult {

  private static final int ST_DRAIN_INIT = 0;
  private static final int ST_DRAIN_HANDLER_SET = 1;
  private static final int ST_DRAIN_DRAINED = 2;
  private static final int ST_DRAIN_FIRED = 3;

  public WritePromise(ContextInternal context) {
    super(context);
  }

  private boolean writable;
  private Handler<Void> drainHandler;
  private final AtomicInteger status = new AtomicInteger();

  @Override
  public void perform() {
    write();
  }

  @Override
  public void enqueued(boolean full) {
    this.writable = !full;
  }

  @Override
  public void drain() {
    while (true) {
      int st = status.get();
      switch (st) {
        case ST_DRAIN_INIT:
          if (status.compareAndSet(ST_DRAIN_INIT, ST_DRAIN_DRAINED)) {
            return;
          }
          break;
        case ST_DRAIN_HANDLER_SET:
          if (status.compareAndSet(ST_DRAIN_HANDLER_SET, ST_DRAIN_FIRED)) {
            Handler<Void> handler = drainHandler;
            drainHandler = null;
            context.runOnContext(handler);
            return;
          }
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  public abstract void write();

  @Override
  public boolean isWritable() {
    return writable;
  }

  @Override
  public WriteResult drainHandler(Handler<Void> handler) {
    if (handler == null) {
      throw new UnsupportedOperationException();
    }
    while (true) {
      int st = status.get();
      switch (st) {
        case ST_DRAIN_INIT:
          drainHandler = handler;
          if (status.compareAndSet(ST_DRAIN_INIT, ST_DRAIN_HANDLER_SET)) {
            return this;
          }
          break;
        case ST_DRAIN_DRAINED:
          if (status.compareAndSet(ST_DRAIN_DRAINED, ST_DRAIN_FIRED)) {
            context.runOnContext(handler);
            return this;
          }
          break;
        default:
          throw new IllegalStateException();
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
