/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

import java.util.function.Function;

/**
 * An helper class for managing close operations.
 */
public class CloseFuture implements Future<Void>, Closeable {

  private final Promise<Void> promise;
  private Closeable resource;
  private boolean closed;

  public CloseFuture() {
    this.promise = Promise.promise();
  }

  public CloseFuture(Closeable resource) {
    this.promise = Promise.promise();
    this.resource = resource;
  }

  public synchronized void init(Closeable closeable) {
    if (closed) {
      throw new IllegalStateException();
    }
    this.resource = closeable;
  }

  /**
   * Called by client
   */
  public void close(Promise<Void> promise) {
    boolean close;
    Closeable resource;
    synchronized (this) {
      close = !closed;
      resource = this.resource;
      this.closed = true;
    }
    if (resource == null) {
      promise.fail("Close future not initialized");
    } else if (close) {
      resource.close(promise);
      promise.future().onComplete(this.promise);
    } else {
      this.promise.future().onComplete(promise);
    }
  }

  public synchronized boolean isClosed() {
    return closed;
  }

  @Override
  public boolean isComplete() {
    return promise.future().isComplete();
  }

  @Override
  public Future<Void> onComplete(Handler<AsyncResult<Void>> handler) {
    promise.future().onComplete(handler);
    return this;
  }

  @Override
  public Void result() {
    return promise.future().result();
  }

  @Override
  public Throwable cause() {
    return promise.future().cause();
  }

  @Override
  public boolean succeeded() {
    return promise.future().succeeded();
  }

  @Override
  public boolean failed() {
    return promise.future().failed();
  }

  @Override
  public <U> Future<U> compose(Function<Void, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper) {
    return promise.future().compose(successMapper, failureMapper);
  }

  @Override
  public <U> Future<Void> eventually(Function<Void, Future<U>> mapper) {
    return promise.future().eventually(mapper);
  }

  @Override
  public <U> Future<U> transform(Function<AsyncResult<Void>, Future<U>> mapper) {
    return promise.future().transform(mapper);
  }

  @Override
  public <U> Future<U> map(Function<Void, U> mapper) {
    return promise.future().map(mapper);
  }

  @Override
  public <V> Future<V> map(V value) {
    return promise.future().map(value);
  }

  @Override
  public Future<Void> otherwise(Function<Throwable, Void> mapper) {
    return promise.future().otherwise(mapper);
  }

  @Override
  public Future<Void> otherwise(Void value) {
    return promise.future().otherwise(value);
  }
}
