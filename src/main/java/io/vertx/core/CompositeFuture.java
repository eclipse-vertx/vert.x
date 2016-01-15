/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.impl.CompositeFutureImpl;

/**
 * The composite future wraps a list of {@link Future futures}, it is useful when several futures
 * needs to be coordinated.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface CompositeFuture extends Future<CompositeFuture> {

  /**
   * Return a composite future, succeeded when all futures are succeeded, failed when any future is failed.
   *
   * @param f1 future
   * @param f2 future
   * @return the composite future
   */
  static <T1, T2> CompositeFuture all(Future<T1> f1, Future<T2> f2) {
    return CompositeFutureImpl.all(f1, f2);
  }

  /**
   * Like {@link #all(Future, Future)} but with 3 futures.
   */
  static <T1, T2, T3> CompositeFuture all(Future<T1> f1, Future<T2> f2, Future<T3> f3) {
    return CompositeFutureImpl.all(f1, f2, f3);
  }

  /**
   * Like {@link #all(Future, Future)} but with 4 futures.
   */
  static <T1, T2, T3, T4> CompositeFuture all(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4) {
    return CompositeFutureImpl.all(f1, f2, f3, f4);
  }

  /**
   * Like {@link #all(Future, Future)} but with 5 futures.
   */
  static <T1, T2, T3, T4, T5> CompositeFuture all(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5) {
    return CompositeFutureImpl.all(f1, f2, f3, f4, f5);
  }

  /**
   * Like {@link #all(Future, Future)} but with 6 futures.
   */
  static <T1, T2, T3, T4, T5, T6> CompositeFuture all(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5, Future<T6> f6) {
    return CompositeFutureImpl.all(f1, f2, f3, f4, f5, f6);
  }

  /**
   * Return a composite future, succeeded when any futures is succeeded, failed when all futures are failed.
   *
   * @param f1 future
   * @param f2 future
   * @return the composite future
   */
  static <T1, T2> CompositeFuture any(Future<T1> f1, Future<T2> f2) {
    return CompositeFutureImpl.any(f1, f2);
  }

  /**
   * Like {@link #any(Future, Future)} but with 3 futures.
   */
  static <T1, T2, T3> CompositeFuture any(Future<T1> f1, Future<T2> f2, Future<T3> f3) {
    return CompositeFutureImpl.any(f1, f2, f3);
  }

  /**
   * Like {@link #any(Future, Future)} but with 4 futures.
   */
  static <T1, T2, T3, T4> CompositeFuture any(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4) {
    return CompositeFutureImpl.any(f1, f2, f3, f4);
  }

  /**
   * Like {@link #any(Future, Future)} but with 5 futures.
   */
  static <T1, T2, T3, T4, T5> CompositeFuture any(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5) {
    return CompositeFutureImpl.any(f1, f2, f3, f4, f5);
  }

  /**
   * Like {@link #any(Future, Future)} but with 6 futures.
   */
  static <T1, T2, T3, T4, T5, T6> CompositeFuture any(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5, Future<T6> f6) {
    return CompositeFutureImpl.any(f1, f2, f3, f4, f5, f6);
  }

  @Override
  CompositeFuture setHandler(Handler<AsyncResult<CompositeFuture>> handler);

  /**
   * Returns a cause of a wrapped future
   *
   * @param index the wrapped future index
   */
  Throwable cause(int index);

  /**
   * Returns true if a wrapped future is succeeded
   *
   * @param index the wrapped future index
   */
  boolean succeeded(int index);

  /**
   * Returns true if a wrapped future is failed
   *
   * @param index the wrapped future index
   */
  boolean failed(int index);

  /**
   * Returns true if a wrapped future is completed
   *
   * @param index the wrapped future index
   */
  boolean isComplete(int index);

  /**
   * Returns the result of a wrapped future
   *
   * @param index the wrapped future index
   */
  <T> T result(int index);

  /**
   * @return the number of wrapped future
   */
  int size();

}
