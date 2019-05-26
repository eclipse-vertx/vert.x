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
package io.vertx.core;

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

import static io.vertx.core.Future.factory;

/**
 * Represents the result of an action that may, or may not, have occurred yet.
 * <p>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface Promise<T> extends Handler<AsyncResult<T>> {

  /**
   * Create a future that hasn't completed yet and that is passed to the {@code handler} before it is returned.
   *
   * @param handler the handler
   * @param <T> the result type
   * @return the future.
   */
  static <T> Promise<T> promise(Handler<Promise<T>> handler) {
    Promise<T> promise = promise();
    handler.handle(promise);
    return promise;
  }

  /**
   * Create a succeeded promise with a {@code null} result
   *
   * @param <T>  the result type
   * @return  the promise
   */
  static <T> Promise<T> succeededPromise() {
    return factory.succeededPromise();
  }

  /**
   * Created a succeeded promise with the specified {@code result}.
   *
   * @param result  the result
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Promise<T> succeededPromise(T result) {
    return factory.succeededPromise(result);
  }

  /**
   * Create a failed promise with the specified failure {@code cause}.
   *
   * @param cause  the failure cause as a Throwable
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Promise<T> failedPromise(Throwable cause) {
    return factory.failedPromise(cause);
  }

  /**
   * Create a failed promise with the specified {@code failureMessage}.
   *
   * @param failureMessage  the failure message
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Promise<T> failedPromise(String failureMessage) {
    return factory.failurePromise(failureMessage);
  }

  /**
   * Create a promise that hasn't completed yet
   *
   * @param <T>  the result type
   * @return  the promise
   */
  static <T> Promise<T> promise() {
    return factory.promise();
  }

  /**
   * Succeed or fail this future with the {@link AsyncResult} event.
   *
   * @param asyncResult the async result to handle
   */
  @GenIgnore
  @Override
  void handle(AsyncResult<T> asyncResult);

  /**
   * Set the result. Any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @param result  the result
   */
  void complete(T result);

  /**
   *  Set a null result. Any handler will be called, if there is one, and the future will be marked as completed.
   */
  void complete();

  /**
   * Set the failure. Any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @param cause  the failure cause
   */
  void fail(Throwable cause);

  /**
   * Try to set the failure. When it happens, any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @param failureMessage  the failure message
   */
  void fail(String failureMessage);

  /**
   * Set the failure. Any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @param result  the result
   * @return false when the future is already completed
   */
  boolean tryComplete(T result);

  /**
   * Try to set the result. When it happens, any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @return false when the future is already completed
   */
  boolean tryComplete();

  /**
   * Try to set the failure. When it happens, any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @param cause  the failure cause
   * @return false when the future is already completed
   */
  boolean tryFail(Throwable cause);

  /**
   * Try to set the failure. When it happens, any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @param failureMessage  the failure message
   * @return false when the future is already completed
   */
  boolean tryFail(String failureMessage);

  @CacheReturn
  Future<T> future();

}
