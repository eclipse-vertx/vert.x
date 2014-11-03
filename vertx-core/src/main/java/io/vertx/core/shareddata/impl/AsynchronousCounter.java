/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.shareddata.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.Counter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsynchronousCounter implements Counter {

  private final VertxInternal vertx;
  private final AtomicLong counter = new AtomicLong();

  public AsynchronousCounter(VertxInternal vertx) {
    this.vertx = vertx;
  }

  @Override
  public void get(Handler<AsyncResult<Long>> resultHandler) {
    handleResult(resultHandler, () -> counter.get());
  }

  @Override
  public void incrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
    handleResult(resultHandler, () -> counter.incrementAndGet());
  }

  @Override
  public void getAndIncrement(Handler<AsyncResult<Long>> resultHandler) {
    handleResult(resultHandler, () -> counter.getAndIncrement());
  }

  @Override
  public void decrementAndGet(Handler<AsyncResult<Long>> resultHandler) {
    handleResult(resultHandler, () -> counter.decrementAndGet());
  }

  @Override
  public void addAndGet(long value, Handler<AsyncResult<Long>> resultHandler) {
    handleResult(resultHandler, () -> counter.addAndGet(value));
  }

  @Override
  public void getAndAdd(long value, Handler<AsyncResult<Long>> resultHandler) {
    handleResult(resultHandler, () -> counter.getAndAdd(value));
  }

  @Override
  public void compareAndSet(long expected, long value, Handler<AsyncResult<Boolean>> resultHandler) {
    handleResult(resultHandler, () -> counter.compareAndSet(expected, value));
  }

  private <T> void handleResult(Handler<AsyncResult<T>> resultHandler, final Supplier<T> function) {
    Context context = vertx.getOrCreateContext();
    context.runOnContext(v -> {
      final Future<T> result = Future.completedFuture(function.get());
      if (null != resultHandler) {
        resultHandler.handle(result);
      }
    });
  }
}
