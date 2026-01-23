/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;

/**
 * Interface for asynchronous parallel execution logic.
 * <p>
 * This interface provides methods for executing asynchronous operations in parallel
 * over collections, with different completion strategies (all success, any success,
 * or all complete).
 *
 * @author Sinri Edogawa
 */
interface AsyncLogicParallel extends AsyncLogicCore {
  private <T extends @Nullable Object> List<Future<Void>> buildFutures(
    Iterator<T> iterator,
    Function<T, Future<Void>> itemProcessor
  ) {
    List<Future<Void>> futures = new ArrayList<>();
    while (iterator.hasNext()) {
      Future<Void> f = itemProcessor.apply(iterator.next());
      futures.add(f);
    }
    return futures;
  }


  /**
   * Iterates over an iterable and triggers asynchronous logic for parallel execution;
   * all asynchronous tasks must succeed for this call to be considered successful.
   *
   * @param <T>           the type of objects in the iterable
   * @param collection    the iterable
   * @param itemProcessor the asynchronous processing logic for each object
   * @return an asynchronous result that succeeds only if all asynchronous tasks succeed,
   *         otherwise returns failure
   */
  default <T extends @Nullable Object> Future<Void> parallelForAllSuccess(
    Iterable<T> collection,
    Function<T, Future<Void>> itemProcessor
  ) {
    return parallelForAllSuccess(collection.iterator(), itemProcessor);
  }

  /**
   * Iterates over an iterator and triggers asynchronous logic for parallel execution;
   * all asynchronous tasks must succeed for this call to be considered successful.
   *
   * @param <T>           the type of objects in the iterator
   * @param iterator      the iterator
   * @param itemProcessor the asynchronous processing logic for each object
   * @return an asynchronous result that succeeds only if all asynchronous tasks succeed,
   *         otherwise returns failure
   */
  default <T extends @Nullable Object> Future<Void> parallelForAllSuccess(
    Iterator<T> iterator,
    Function<T, Future<Void>> itemProcessor
  ) {
    List<Future<Void>> futures = buildFutures(iterator, itemProcessor);
    if (futures.isEmpty()) {
      return Future.succeededFuture();
    }
    return Future.all(futures)
                 .mapEmpty();
  }

  /**
   * Iterates over an iterable and triggers asynchronous logic for parallel execution;
   * the call is considered successful if any asynchronous task succeeds.
   *
   * @param <T>           the type of objects in the iterable
   * @param collection    the iterable
   * @param itemProcessor the asynchronous processing logic for each object
   * @return an asynchronous result that succeeds if any asynchronous task succeeds,
   *         otherwise returns failure
   */
  default <T extends @Nullable Object> Future<Void> parallelForAnySuccess(
    Iterable<T> collection,
    Function<T, Future<Void>> itemProcessor
  ) {
    return parallelForAnySuccess(collection.iterator(), itemProcessor);
  }

  /**
   * Iterates over an iterator and triggers asynchronous logic for parallel execution;
   * the call is considered successful if any asynchronous task succeeds.
   *
   * @param <T>           the type of objects in the iterator
   * @param iterator      the iterator
   * @param itemProcessor the asynchronous processing logic for each object
   * @return an asynchronous result that succeeds if any asynchronous task succeeds,
   *         otherwise returns failure
   */
  default <T extends @Nullable Object> Future<Void> parallelForAnySuccess(
    Iterator<T> iterator,
    Function<T, Future<Void>> itemProcessor
  ) {
    List<Future<Void>> futures = buildFutures(iterator, itemProcessor);
    if (futures.isEmpty()) {
      return Future.succeededFuture();
    }
    return Future.any(futures)
                 .mapEmpty();
  }

  /**
   * Iterates over an iterable and triggers asynchronous logic for parallel execution;
   * the call is considered successful when all asynchronous tasks have completed execution.
   *
   * @param <T>           the type of objects in the iterable
   * @param collection    the iterable
   * @param itemProcessor the asynchronous processing logic for each object
   * @return an asynchronous result that succeeds when all asynchronous tasks have completed,
   *         otherwise returns failure
   */
  default <T extends @Nullable Object> Future<Void> parallelForAllComplete(
    Iterable<T> collection,
    Function<T, Future<Void>> itemProcessor
  ) {
    return parallelForAllComplete(collection.iterator(), itemProcessor);
  }

  /**
   * Iterates over an iterator and triggers asynchronous logic for parallel execution;
   * the call is considered successful when all asynchronous tasks have completed execution.
   *
   * @param <T>           the type of objects in the iterator
   * @param iterator      the iterator
   * @param itemProcessor the asynchronous processing logic for each object
   * @return an asynchronous result that succeeds when all asynchronous tasks have completed,
   *         otherwise returns failure
   */
  default <T extends @Nullable Object> Future<Void> parallelForAllComplete(
    Iterator<T> iterator,
    Function<T, Future<Void>> itemProcessor
  ) {
    List<Future<Void>> futures = buildFutures(iterator, itemProcessor);
    if (futures.isEmpty()) {
      return Future.succeededFuture();
    }
    return Future.join(futures).mapEmpty();
  }
}
