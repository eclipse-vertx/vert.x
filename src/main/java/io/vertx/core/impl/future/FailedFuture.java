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

package io.vertx.core.impl.future;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.NoStackTraceException;

import java.util.function.Function;

/**
 * Failed future implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class FailedFuture<T> extends FutureBase<T> {

  private final Throwable cause;

  /**
   * Create a future that has already failed
   * @param t the throwable
   */
  public FailedFuture(Throwable t) {
    this(null, t);
  }

  /**
   * Create a future that has already failed
   * @param t the throwable
   */
  public FailedFuture(ContextInternal context, Throwable t) {
    super(context);
    this.cause = t != null ? t : new NoStackTraceException();
  }

  /**
   * Create a future that has already failed
   * @param failureMessage the failure message
   */
  public FailedFuture(String failureMessage) {
    this(null, failureMessage);
  }

  /**
   * Create a future that has already failed
   * @param failureMessage the failure message
   */
  public FailedFuture(ContextInternal context, String failureMessage) {
    this(context, new NoStackTraceException(failureMessage));
  }

  @Override
  public boolean isComplete() {
    return true;
  }

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> handler) {
    if (handler instanceof Listener) {
      emitFailure(cause, (Listener<T>) handler);
    } else if (context != null) {
      context.emit(this, handler);
    } else {
      handler.handle(this);
    }
    return this;
  }

  @Override
  public Future<T> onSuccess(Handler<T> handler) {
    return this;
  }

  @Override
  public Future<T> onFailure(Handler<Throwable> handler) {
    if (context != null) {
      context.emit(cause, handler);
    } else {
      handler.handle(cause);
    }
    return this;
  }

  @Override
  public void addListener(Listener<T> listener) {
    emitFailure(cause, listener);
  }

  @Override
  public T result() {
    return null;
  }

  @Override
  public Throwable cause() {
    return cause;
  }

  @Override
  public boolean succeeded() {
    return false;
  }

  @Override
  public boolean failed() {
    return true;
  }

  @Override
  public <U> Future<U> map(Function<T, U> mapper) {
    return (Future<U>) this;
  }

  @Override
  public <V> Future<V> map(V value) {
    return (Future<V>) this;
  }

  @Override
  public Future<T> otherwise(T value) {
    return new SucceededFuture<>(context, value);
  }

  @Override
  public String toString() {
    return "Future{cause=" + cause.getMessage() + "}";
  }
}
