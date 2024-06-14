/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import io.netty.util.concurrent.FutureListener;
import io.vertx.core.Timer;
import io.vertx.core.impl.future.FutureImpl;
import io.vertx.core.internal.ContextInternal;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * A timer task as a vertx future.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TimerImpl extends FutureImpl<Void> implements FutureListener<Void>, Timer {

  private final io.netty.util.concurrent.ScheduledFuture<Void> delegate;

  public TimerImpl(ContextInternal ctx, io.netty.util.concurrent.ScheduledFuture<Void> delegate) {
    super(ctx);
    this.delegate = delegate;
  }

  @Override
  public long getDelay(TimeUnit unit) {
    return delegate.getDelay(unit);
  }

  @Override
  public int compareTo(Delayed o) {
    return delegate.compareTo(o);
  }

  @Override
  public boolean cancel() {
    return delegate.cancel(false);
  }

  @Override
  public void operationComplete(io.netty.util.concurrent.Future<Void> future) {
    if (future.isSuccess()) {
      tryComplete(null);
    } else {
      tryFail(future.cause());
    }
  }
}
