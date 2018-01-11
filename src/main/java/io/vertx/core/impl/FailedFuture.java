/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FailedFuture<T> implements Future<T> {

  private final Throwable cause;

  /**
   * Create a future that has already failed
   * @param t the throwable
   */
  FailedFuture(Throwable t) {
    cause = t != null ? t : new NoStackTraceThrowable(null);
  }

  /**
   * Create a future that has already failed
   * @param failureMessage the failure message
   */
  FailedFuture(String failureMessage) {
    this(new NoStackTraceThrowable(failureMessage));
  }

  @Override
  public boolean isComplete() {
    return true;
  }

  @Override
  public Future<T> setHandler(Handler<AsyncResult<T>> handler) {
    handler.handle(this);
    return this;
  }

  @Override
  public void complete(T result) {
    throw new IllegalStateException("Result is already complete: failed");
  }

  @Override
  public void complete() {
    throw new IllegalStateException("Result is already complete: failed");
  }

  @Override
  public void fail(Throwable cause) {
    throw new IllegalStateException("Result is already complete: failed");
  }

  @Override
  public void fail(String failureMessage) {
    throw new IllegalStateException("Result is already complete: failed");
  }

  @Override
  public boolean tryComplete(T result) {
    return false;
  }

  @Override
  public boolean tryComplete() {
    return false;
  }

  @Override
  public boolean tryFail(Throwable cause) {
    return false;
  }

  @Override
  public boolean tryFail(String failureMessage) {
    return false;
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
  public void handle(AsyncResult<T> asyncResult) {
    throw new IllegalStateException("Result is already complete: failed");
  }

  @Override
  public String toString() {
    return "Future{cause=" + cause.getMessage() + "}";
  }
}
