package org.vertx.java.core.impl;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultFutureResult<T> implements Future<T> {
  private boolean failed;
  private boolean succeeded;
  private Handler<AsyncResult<T>> handler;
  private T result;
  private Throwable throwable;

  /**
   * Create a FutureResult that hasn't completed yet
   */
  public DefaultFutureResult() {
  }

  /**
   * Create a VoidResult that has already completed
   * @param t The Throwable or null if succeeded
   */
  public DefaultFutureResult(Throwable t) {
    if (t == null) {
      setResult(null);
    } else {
      setFailure(t);
    }
  }

  /**
   * Create a FutureResult that has already succeeded
   * @param result The result
   */
  public DefaultFutureResult(T result) {
    setResult(result);
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
  public boolean complete() {
    return failed || succeeded;
  }

  /**
   * Set a handler for the result. It will get called when it's complete
   */
  public DefaultFutureResult<T> setHandler(Handler<AsyncResult<T>> handler) {
    this.handler = handler;
    checkCallHandler();
    return this;
  }

  /**
   * Set the result. Any handler will be called, if there is one
   */
  public DefaultFutureResult<T> setResult(T result) {
    this.result = result;
    succeeded = true;
    checkCallHandler();
    return this;
  }

  /**
   * Set the failure. Any handler will be called, if there is one
   */
  public DefaultFutureResult<T> setFailure(Throwable throwable) {
    this.throwable = throwable;
    failed = true;
    checkCallHandler();
    return this;
  }

  private void checkCallHandler() {
    if (handler != null && complete()) {
      handler.handle(this);
    }
  }
}
