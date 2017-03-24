/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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

  FailedFuture(Throwable cause) {
    this.cause = cause;
  }

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
    throw new IllegalStateException();
  }

  @Override
  public void complete() {
    throw new IllegalStateException();
  }

  @Override
  public void fail(Throwable cause) {
    throw new IllegalStateException();
  }

  @Override
  public void fail(String failureMessage) {
    throw new IllegalStateException();
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
    throw new IllegalStateException();
  }
}
