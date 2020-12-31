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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * An helper class for managing close operations.
 * <p>
 * A thread-safe / lock-free implementation of a {@link CloseFuture} relying on compare-and-swap, busy-waiting
 * and piggybacking.
 */
public class CloseFuture implements Future<Void>, Closeable {

  /**
   * All possible states of the {@code CloseFuture}, the initial state is <i>NEW</i> then the possible state transitions are:
   * <p>
   * <ul>
   *   <li><i>NEW</i> -> <i>INITIALIZING</i> -> <i>INITIALIZED</i> -> <i>CLOSED</i>
   * </ul>
   *
   * <p>
   * The description of all possible states:
   * <table>
   *   <col width="25%"/>
   *   <col width="75%"/>
   *   <thead>
   *     <tr><th>State</th><th>Description</th></tr>
   *   <thead>
   *   <tbody>
   *      <tr><td>{@link CloseFuture#NEW}</td><td>The initial state</td></tr>
   *      <tr><td>{@link CloseFuture#INITIALIZED}</td><td>The state in case the underlying resource has been set</td></tr>
   *      <tr><td>{@link CloseFuture#INITIALIZING}</td><td>The state in case the {@code CloseFuture} is currently being initialized</td></tr>
   *      <tr><td>{@link CloseFuture#CLOSED}</td><td>The state in case the {@code CloseFuture} has been closed</td></tr>
   *   </tbody>
   * </table>
   */
  private static final int NEW          = 0;
  private static final int INITIALIZED  = 1;
  private static final int INITIALIZING = 2;
  private static final int CLOSED       = 3;

  private final Promise<Void> promise;
  private Closeable resource;
  /**
   * The current state of the {@code CloseFuture}.
   */
  private final AtomicInteger state;

  public CloseFuture() {
    this.promise = Promise.promise();
    this.state = new AtomicInteger(NEW);
  }

  public CloseFuture(Closeable resource) {
    this.promise = Promise.promise();
    this.resource = resource;
    this.state = new AtomicInteger(resource == null ? NEW : INITIALIZED);
  }

  public void init(Closeable closeable) {
    for (;;) {
      int s = this.state.get();
      if (s == CLOSED) {
        throw new IllegalStateException();
      } else if (closeable == null) {
        if (s == NEW) {
          return;
        } else if (s == INITIALIZED && this.state.compareAndSet(s, INITIALIZING)) {
          this.resource = null;
          this.state.set(NEW);
          return;
        }
      } else if (s <= INITIALIZED && this.state.compareAndSet(s, INITIALIZING)) {
        this.resource = closeable;
        this.state.set(INITIALIZED);
        return;
      }
    }
  }

  /**
   * Called by client
   */
  public void close(Promise<Void> promise) {
    for (;;) {
      int s = this.state.get();
      if (s == NEW) {
        promise.fail("Close future not initialized");
        return;
      } else if (s == INITIALIZED && this.state.compareAndSet(s, CLOSED)) {
        this.resource.close(promise);
        promise.future().onComplete(this.promise);
        return;
      } else if (s == CLOSED) {
        this.promise.future().onComplete(promise);
        return;
      }
    }
  }

  public boolean isClosed() {
    return state.get() == CLOSED;
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
