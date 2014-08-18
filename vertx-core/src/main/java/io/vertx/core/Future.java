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

import io.vertx.core.spi.FutureFactory;

public interface Future<T> extends AsyncResult<T> {

  static <T> Future<T> future() {
    return factory.future();
  }

  static <T> Future<T> completedFuture() {
    return factory.completedFuture();
  }

  static <T> Future<T> completedFuture(T result) {
    return factory.completedFuture(result);
  }

  static <T> Future<T> completedFuture(Throwable t) {
    return factory.completedFuture(t);
  }

  /**
   * Has it completed?
   */
  boolean complete();

  /**
   * Set a handler for the result. It will get called when it's complete
   */
  Future<T> setHandler(Handler<AsyncResult<T>> handler);

  /**
   * Set the result. Any handler will be called, if there is one
   *
   * @throws IllegalStateException when the future is already completed
   */
  Future<T> setResult(T result) throws IllegalStateException;

  /**
   * Set the failure. Any handler will be called, if there is one
   */
  Future<T> setFailure(Throwable throwable);

  static FutureFactory factory = ServiceHelper.loadFactory(FutureFactory.class);

}
