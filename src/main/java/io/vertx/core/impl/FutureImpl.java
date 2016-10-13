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

class FutureImpl<T> implements Future<T>, Handler<AsyncResult<T>> {
  private boolean failed;
  private boolean succeeded;
  private Handler<AsyncResult<T>> handler;
  private Handler<T> successHandler;
  private Handler<Throwable> failureHandler;
  private T result;
  private Throwable throwable;

  /**
   * Create a future that hasn't completed yet
   */
  FutureImpl() {
  }

  /**
   * Create a future that has already failed
   *
   * @param t the throwable
   */
  FutureImpl(Throwable t) {
    fail(t != null ? t : new NoStackTraceThrowable(null));
  }

  /**
   * Create a future that has already failed
   *
   * @param failureMessage the failure message
   */
  FutureImpl(String failureMessage) {
    this(new NoStackTraceThrowable(failureMessage));
  }

  /**
   * Create a future that has already succeeded
   *
   * @param result the result
   */
  FutureImpl(T result) {
    complete(result);
  }

  /**
   * The result of the operation. This will be null if the operation failed.
   */
  public T result() {
    return result;
  }

  /**
   * An exception describing failure. This will be null if the operation succeeded.
   */
  public Throwable cause() {
    return throwable;
  }

  /**
   * Did it succeeed?
   */
  public boolean succeeded() {
    return succeeded;
  }

  /**
   * Did it fail?
   */
  public boolean failed() {
    return failed;
  }

  /**
   * Has it completed?
   */
  public boolean isComplete() {
    return failed || succeeded;
  }

  /**
   * Set a handler for the result. It will get called when it's complete
   */
  public Future<T> setHandler(Handler<AsyncResult<T>> handler) {
    this.handler = handler;
    checkCallHandler();
    return this;
  }

  @Override
  public Future<T> setSuccessHandler(Handler<T> successHandler) {
    this.successHandler = successHandler;
    checkCallSuccessHandler();
    return this;
  }

  @Override
  public Future<T> setFailureHandler(Handler<Throwable> failureHandler) {
    this.failureHandler = failureHandler;
    checkCallFailureHandler();
    return this;
  }

  /**
   * Set the result. Any handler will be called, if there is one
   */
  public void complete(T result) {
    checkComplete();
    this.result = result;
    succeeded = true;

    checkCallHandlerOnComplete();

    if (successHandler != null) {
      successHandler.handle(result);
    }
  }

  @Override
  public void complete() {
    complete(null);
  }

  public void handle(Future<T> ar) {
    if (ar.succeeded()) {
      complete(ar.result());
    } else {
      fail(ar.cause());
    }
  }

  @Override
  public Handler<AsyncResult<T>> completer() {
    return this;
  }

  @Override
  public void handle(AsyncResult<T> ar) {
    if (ar.succeeded()) {
      complete(ar.result());
    } else {
      fail(ar.cause());
    }
  }

  /**
   * Set the failure. Any handler will be called, if there is one
   */
  public void fail(Throwable throwable) {
    checkComplete();
    this.throwable = throwable != null ? throwable : new NoStackTraceThrowable(null);
    failed = true;

    checkCallHandlerOnComplete();

    if (failureHandler != null) {
      failureHandler.handle(this.throwable);
    }
  }

  @Override
  public void fail(String failureMessage) {
    fail(new NoStackTraceThrowable(failureMessage));
  }

  private void checkCallHandlerOnComplete() {
    if (handler != null) {
      handler.handle(this);
    }
  }

  private void checkCallHandler() {
    if (handler != null && isComplete()) {
      handler.handle(this);
    }
  }

  private void checkCallSuccessHandler() {
    if (successHandler != null && succeeded) {
      successHandler.handle(result);
    }
  }

  private void checkCallFailureHandler() {
    if (failureHandler != null && failed) {
      failureHandler.handle(throwable);
    }
  }

  private void checkComplete() {
    if (succeeded || failed) {
      throw new IllegalStateException("Result is already complete: " + (succeeded ? "succeeded" : "failed"));
    }
  }

}
