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

package io.vertx.core;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.spi.FutureFactory;

/**
 * Represents the result of an action that may, or may not, have occurred yet.
 * <p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Future<T> extends AsyncResult<T> {

  /**
   * Create a future that hasn't completed yet
   *
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Future<T> future() {
    return factory.future();
  }

  /**
   * Create a succeeded future with a null result
   *
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Future<T> succeededFuture() {
    return factory.completedFuture();
  }

  /**
   * Created a succeeded future with the specified result.
   *
   * @param result  the result
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Future<T> succeededFuture(T result) {
    return factory.completedFuture(result);
  }

  /**
   * Create a failed future with the specified failure cause.
   *
   * @param t  the failure cause as a Throwable
   * @param <T>  the result type
   * @return  the future
   */
  @GenIgnore
  static <T> Future<T> failedFuture(Throwable t) {
    return factory.completedFuture(t);
  }

  /**
   * Create a failed future with the specified failure message.
   *
   * @param failureMessage  the failure message
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Future<T> failedFuture(String failureMessage) {
    return factory.completedFuture(failureMessage, true);
  }

  /**
   * Has the future completed?
   * <p>
   * It's completed if it's either succeeded or failed.
   *
   * @return true if completed, false if not
   */
  boolean isComplete();

  /**
   * Set a handler for the result.
   * <p>
   * If the future has already been completed it will be called immediately. Otherwise it will be called when the
   * future is completed.
   *
   * @param handler  the Handler that will be called with the result
   *
   */
  void setHandler(Handler<AsyncResult<T>> handler);

  /**
   * Set the result. Any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @param result  the result
   * @throws IllegalStateException when the future is already completed
   */
  void complete(T result);

  /**
   * Set a null result. Any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @throws IllegalStateException when the future is already completed
   */
  void complete();

  /**
   * Set the failure. Any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @param throwable  the failure cause
   */
  @GenIgnore
  void fail(Throwable throwable);

  /**
   * Set the failure. Any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @param failureMessage  the failure message
   */
  void fail(String failureMessage);

  static FutureFactory factory = ServiceHelper.loadFactory(FutureFactory.class);

}
