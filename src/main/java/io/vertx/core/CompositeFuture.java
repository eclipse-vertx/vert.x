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

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.impl.CompositeFutureImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * The composite future wraps a list of {@link Future futures}, it is useful when several futures
 * needs to be coordinated.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 */
@VertxGen
public interface CompositeFuture extends Future<CompositeFuture> {

  /**
   * Return a composite future, succeeded when all futures are succeeded, failed when any future is failed.
   * <p/>
   * The returned future fails as soon as one of {@code f1} or {@code f2} fails.
   *
   * @param f1 future
   * @param f2 future
   * @return the composite future
   */
  static <T1, T2> CompositeFuture all(Future<T1> f1, Future<T2> f2) {
    return CompositeFutureImpl.all(false, f1, f2);
  }

  /**
   * Like {@link #all(Future, Future)} but with 3 futures.
   */
  static <T1, T2, T3> CompositeFuture all(Future<T1> f1, Future<T2> f2, Future<T3> f3) {
    return CompositeFutureImpl.all(false, f1, f2, f3);
  }

  /**
   * Like {@link #all(Future, Future)} but with 4 futures.
   */
  static <T1, T2, T3, T4> CompositeFuture all(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4) {
    return CompositeFutureImpl.all(false, f1, f2, f3, f4);
  }

  /**
   * Like {@link #all(Future, Future)} but with 5 futures.
   */
  static <T1, T2, T3, T4, T5> CompositeFuture all(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5) {
    return CompositeFutureImpl.all(false, f1, f2, f3, f4, f5);
  }

  /**
   * Like {@link #all(Future, Future)} but with 6 futures.
   */
  static <T1, T2, T3, T4, T5, T6> CompositeFuture all(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5, Future<T6> f6) {
    return CompositeFutureImpl.all(false, f1, f2, f3, f4, f5, f6);
  }

  /**
   * Like {@link #all(Future, Future)} but with a list of futures.<p>
   *
   * When the list is empty, the returned future will be already completed.
   */
  static CompositeFuture all(List<Future> futures) {
    return CompositeFutureImpl.all(false, futures.toArray(new Future[futures.size()]));
  }

  /**
   * Return a composite future, succeeded when all futures are succeeded, failed when any future is failed.
   * <p/>
   * The returned future fails if either {@code f1} or {@code f2} fails. The {@code collectResults} flag determines when
   * the composite future completes.
   * <p/>
   * If {@code collectResults = false} the composite future fails and completes as soon as the first future fails. This
   * is equivalent to calling {@link #all(Future, Future)} without the parameter.
   * <p/>
   * On the other hand, if {@code collectResults = true} the composite future completes only after both {@code f1} and {@code f2} have
   * completed. This allows you to inspect each individual outcome. In case of failure the {@code cause()} returned in the
   * composite future result handler will hold the last failure that occurred on its constituent futures.
   *
   * @param collectResults whether or not to collect results
   * @param f1 future
   * @param f2 future
   * @return the composite future
   */
  static <T1, T2> CompositeFuture all(boolean collectResults, Future<T1> f1, Future<T2> f2) {
    return CompositeFutureImpl.all(collectResults, f1, f2);
  }

  /**
   * Like {@link #all(boolean, Future, Future)} but with 3 futures.
   */
  static <T1, T2, T3> CompositeFuture all(boolean collectResults, Future<T1> f1, Future<T2> f2, Future<T3> f3) {
    return CompositeFutureImpl.all(collectResults, f1, f2, f3);
  }

  /**
   * Like {@link #all(boolean, Future, Future)} but with 4 futures.
   */
  static <T1, T2, T3, T4> CompositeFuture all(boolean collectResults, Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4) {
    return CompositeFutureImpl.all(collectResults, f1, f2, f3, f4);
  }

  /**
   * Like {@link #all(boolean, Future, Future)} but with 5 futures.
   */
  static <T1, T2, T3, T4, T5> CompositeFuture all(boolean collectResults, Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5) {
    return CompositeFutureImpl.all(collectResults, f1, f2, f3, f4, f5);
  }

  /**
   * Like {@link #all(boolean, Future, Future)} but with 6 futures.
   */
  static <T1, T2, T3, T4, T5, T6> CompositeFuture all(boolean collectResults, Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5, Future<T6> f6) {
    return CompositeFutureImpl.all(collectResults, f1, f2, f3, f4, f5, f6);
  }

  /**
   * Like {@link #all(boolean, Future, Future)} but with a list of futures.<p>
   *
   * When the list is empty, the returned future will be already completed.
   */
  static CompositeFuture all(boolean collectResults, List<Future> futures) {
    return CompositeFutureImpl.all(collectResults, futures.toArray(new Future[futures.size()]));
  }

  /**
   * Return a composite future, succeeded when any future is succeeded, failed when all futures are failed.
   * <p/>
   * The returned future succeeds as soon as one of {@code f1} or {@code f2} succeeds.
   *
   * @param f1 future
   * @param f2 future
   * @return the composite future
   */
  static <T1, T2> CompositeFuture any(Future<T1> f1, Future<T2> f2) {
    return CompositeFutureImpl.any(false, f1, f2);
  }

  /**
   * Like {@link #any(Future, Future)} but with 3 futures.
   */
  static <T1, T2, T3> CompositeFuture any(Future<T1> f1, Future<T2> f2, Future<T3> f3) {
    return CompositeFutureImpl.any(false, f1, f2, f3);
  }

  /**
   * Like {@link #any(Future, Future)} but with 4 futures.
   */
  static <T1, T2, T3, T4> CompositeFuture any(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4) {
    return CompositeFutureImpl.any(false, f1, f2, f3, f4);
  }

  /**
   * Like {@link #any(Future, Future)} but with 5 futures.
   */
  static <T1, T2, T3, T4, T5> CompositeFuture any(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5) {
    return CompositeFutureImpl.any(false, f1, f2, f3, f4, f5);
  }

  /**
   * Like {@link #any(Future, Future)} but with 6 futures.
   */
  static <T1, T2, T3, T4, T5, T6> CompositeFuture any(Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5, Future<T6> f6) {
    return CompositeFutureImpl.any(false, f1, f2, f3, f4, f5, f6);
  }

  /**
   * Like {@link #any(Future, Future)} but with a list of futures.<p>
   *
   * When the list is empty, the returned future will be already completed.
   */
  static CompositeFuture any(List<Future> futures) {
    return CompositeFutureImpl.any(false, futures.toArray(new Future[futures.size()]));
  }

  /**
   * Return a composite future, succeeded when any futures are succeeded, failed when all futures are failed.
   * <p/>
   * The returned future succeeds when either {@code f1} or {@code f2} succeeds. The {@code collectResults} flag determines when
   * the composite future itself completes.
   * <p/>
   * If {@code collectResults = false} the composite future completes as soon as the first future succeeds. This
   * is equivalent to calling {@link #any(Future, Future)} without the parameter.
   * <p/>
   * On the other hand, if {@code collectResults = true} the composite future completes only after both {@code f1} and {@code f2} have
   * completed. This allows you to inspect each individual outcome. In the case where all futures fail the {@code cause()} returned in the
   * composite future result handler will hold the last failure that occurred.
   *
   * @param collectResults whether or not to collect results
   * @param f1 future
   * @param f2 future
   * @return the composite future
   */
  static <T1, T2> CompositeFuture any(boolean collectResults, Future<T1> f1, Future<T2> f2) {
    return CompositeFutureImpl.any(collectResults, f1, f2);
  }

  /**
   * Like {@link #any(boolean, Future, Future)} but with 3 futures.
   */
  static <T1, T2, T3> CompositeFuture any(boolean collectResults, Future<T1> f1, Future<T2> f2, Future<T3> f3) {
    return CompositeFutureImpl.any(collectResults, f1, f2, f3);
  }

  /**
   * Like {@link #any(boolean, Future, Future)} but with 4 futures.
   */
  static <T1, T2, T3, T4> CompositeFuture any(boolean collectResults, Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4) {
    return CompositeFutureImpl.any(collectResults, f1, f2, f3, f4);
  }

  /**
   * Like {@link #any(boolean, Future, Future)} but with 5 futures.
   */
  static <T1, T2, T3, T4, T5> CompositeFuture any(boolean collectResults, Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5) {
    return CompositeFutureImpl.any(collectResults, f1, f2, f3, f4, f5);
  }

  /**
   * Like {@link #any(boolean, Future, Future)} but with 6 futures.
   */
  static <T1, T2, T3, T4, T5, T6> CompositeFuture any(boolean collectResults, Future<T1> f1, Future<T2> f2, Future<T3> f3, Future<T4> f4, Future<T5> f5, Future<T6> f6) {
    return CompositeFutureImpl.any(collectResults, f1, f2, f3, f4, f5, f6);
  }

  /**
   * Like {@link #any(boolean, Future, Future)} but with a list of futures.<p>
   *
   * When the list is empty, the returned future will be already completed.
   */
  static CompositeFuture any(boolean collectResults, List<Future> futures) {
    return CompositeFutureImpl.any(collectResults, futures.toArray(new Future[futures.size()]));
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

  /**
   * @return a list of the current completed values. If one future is not yet resolved or is failed, {@code} null
   *         will be used
   */
  @GenIgnore
  default <T> List<T> list() {
    int size = size();
    ArrayList<T> list = new ArrayList<>(size);
    for (int index = 0;index < size;index++) {
      list.add(result(index));
    }
    return list;
  }
}
