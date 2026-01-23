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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Core interface for asynchronous logic operations.
 * <p>
 * This interface provides the fundamental building blocks for asynchronous operations
 * including non-blocking sleep, repeated calls, iterations, and stepwise loops.
 *
 * @author Sinri Edogawa
 */
interface AsyncLogicCore {
  /**
   * Returns the Vert.x instance associated with this async logic.
   *
   * @return the Vert.x instance
   */
  Vertx vertx();

  /**
   * Non-blocking sleep for a specified duration.
   * <p>
   * Note that this is not a true thread sleep, but rather implemented through a timer
   * callback mechanism. Therefore, it does not block the thread and will not prevent
   * process exit.
   *
   * @param time the duration in milliseconds, minimum valid value is 1 millisecond;
   *             invalid values will be forced to 1 millisecond
   * @return a {@link Future} that completes when the specified time has elapsed
   */
  default Future<Void> asyncSleep(long time) {
    return asyncSleep(time, null);
  }

  /**
   * Non-blocking sleep for a specified duration with optional interrupt capability.
   * <p>
   * The sleep can be interrupted early by completing the provided interrupter promise.
   *
   * @param time        the duration in milliseconds, minimum valid value is 1 millisecond;
   *                    invalid values will be forced to 1 millisecond
   * @param interrupter an optional {@link Promise} for asynchronous interruption
   * @return a {@link Future} that completes when the specified time has elapsed,
   *         or when the interrupt is triggered
   */
  default Future<Void> asyncSleep(long time, @Nullable Promise<Void> interrupter) {
    Promise<Void> promise = Promise.promise();
    time = Math.max(1, time);
    long timer_id = vertx().setTimer(time, timerID -> {
      promise.complete();
    });
    if (interrupter != null) {
      interrupter.future().onSuccess(interrupted -> {
        vertx().cancelTimer(timer_id);
        promise.tryComplete();
      });
    }
    return promise.future();
  }

  /**
   * Executes an asynchronous loop based on the given repeatedly call task.
   *
   * @param repeatedlyCallTask the given asynchronous repeatedly call task
   * @return the result of the asynchronous loop execution
   */
  private Future<Void> asyncCallRepeatedly(RepeatedlyCallTask repeatedlyCallTask) {
    Promise<Void> promise = Promise.promise();
    RepeatedlyCallTask.start(vertx(), repeatedlyCallTask, promise);
    return promise.future();
  }

  /**
   * Executes an asynchronous loop based on the given processor function.
   * <p>
   * The processor function is called repeatedly until the task is stopped via
   * {@link RepeatedlyCallTask#stop()}.
   *
   * @param processor the loop logic function used to build the asynchronous repeatedly call task
   * @return the result of the asynchronous loop execution
   */
  default Future<Void> asyncCallRepeatedly(Function<RepeatedlyCallTask, Future<Void>> processor) {
    return asyncCallRepeatedly(new RepeatedlyCallTask(processor));
  }

  /**
   * Performs asynchronous batch iteration over an iterator using asynchronous loop calls,
   * with the ability to interrupt the task early within the iteration body if needed.
   *
   * @param <T>            the type of objects in the iterator
   * @param iterator       the iterator
   * @param itemsProcessor the batch iteration execution logic
   * @param batchSize      the batch size for execution
   * @return the result of the asynchronous loop execution
   */
  default <T extends @Nullable Object> Future<Void> asyncCallIteratively(
    Iterator<T> iterator,
    BiFunction<List<T>, RepeatedlyCallTask, Future<Void>> itemsProcessor,
    int batchSize
  ) {
    if (batchSize <= 0)
      throw new IllegalArgumentException("batchSize must be greater than 0");

    return asyncCallRepeatedly(repeatedlyCallTask -> {
      List<T> buffer = new ArrayList<>();

      while (buffer.size() < batchSize) {
        if (iterator.hasNext()) {
          buffer.add(iterator.next());
        } else {
          break;
        }
      }

      if (buffer.isEmpty()) {
        repeatedlyCallTask.stop();
        return Future.succeededFuture();
      }

      return itemsProcessor.apply(buffer, repeatedlyCallTask);
    });
  }

  /**
   * Performs asynchronous batch iteration over an iterator using asynchronous loop calls.
   *
   * @param <T>            the type of objects in the iterator
   * @param iterator       the iterator
   * @param itemsProcessor the batch iteration execution logic
   * @param batchSize      the batch size for execution
   * @return the result of the asynchronous loop execution
   */
  default <T extends @Nullable Object> Future<Void> asyncCallIteratively(
    Iterator<T> iterator,
    Function<List<T>, Future<Void>> itemsProcessor,
    int batchSize
  ) {
    return asyncCallIteratively(
      iterator,
      (ts, repeatedlyCallTask) -> itemsProcessor.apply(ts),
      batchSize);
  }

  /**
   * Performs asynchronous batch iteration over an iterable using asynchronous loop calls.
   *
   * @param <T>            the type of objects in the iterable
   * @param iterable       the iterable
   * @param itemsProcessor the batch iteration execution logic
   * @param batchSize      the batch size for execution
   * @return the result of the asynchronous loop execution
   */
  default <T extends @Nullable Object> Future<Void> asyncCallIteratively(
    Iterable<T> iterable,
    BiFunction<List<T>, RepeatedlyCallTask, Future<Void>> itemsProcessor,
    int batchSize
  ) {
    return asyncCallIteratively(iterable.iterator(), itemsProcessor, batchSize);
  }

  /**
   * Performs asynchronous iteration over an iterator using asynchronous loop calls,
   * with the ability to interrupt the task early within the iteration body if needed.
   *
   * @param <T>           the type of objects in the iterator
   * @param iterator      the iterator
   * @param itemProcessor the iteration execution logic
   * @return the result of the asynchronous loop execution
   */
  default <T extends @Nullable Object> Future<Void> asyncCallIteratively(
    Iterator<T> iterator,
    BiFunction<T, RepeatedlyCallTask, Future<Void>> itemProcessor
  ) {
    return asyncCallRepeatedly(routineResult -> Future
      .succeededFuture()
      .compose(v -> {
        if (iterator.hasNext()) {
          return itemProcessor.apply(iterator.next(), routineResult);
        } else {
          routineResult.stop();
          return Future.succeededFuture();
        }
      }));
  }

  /**
   * Performs asynchronous iteration over an iterator using asynchronous loop calls.
   *
   * @param <T>           the type of objects in the iterator
   * @param iterator      the iterator
   * @param itemProcessor the iteration execution logic
   * @return the result of the asynchronous loop execution
   */
  default <T extends @Nullable Object> Future<Void> asyncCallIteratively(
    Iterator<T> iterator,
    Function<T, Future<Void>> itemProcessor) {
    return asyncCallIteratively(
      iterator,
      (t, repeatedlyCallTask) -> itemProcessor.apply(t)
    );
  }

  /**
   * Performs asynchronous iteration over an iterable using asynchronous loop calls.
   *
   * @param <T>           the type of objects in the iterable
   * @param iterable      the iterable
   * @param itemProcessor the iteration execution logic
   * @return the result of the asynchronous loop execution
   */
  default <T extends @Nullable Object> Future<Void> asyncCallIteratively(
    Iterable<T> iterable,
    Function<T, Future<Void>> itemProcessor) {
    return asyncCallIteratively(iterable.iterator(), itemProcessor);
  }

  /**
   * Performs asynchronous iteration over an iterable using asynchronous loop calls,
   * with the ability to interrupt the task early within the iteration body if needed.
   *
   * @param <T>           the type of objects in the iterable
   * @param iterable      the iterable
   * @param itemProcessor the iteration execution logic
   * @return the result of the asynchronous loop execution
   */
  default <T extends @Nullable Object> Future<Void> asyncCallIteratively(
    Iterable<T> iterable,
    BiFunction<T, RepeatedlyCallTask, Future<Void>> itemProcessor) {
    return asyncCallIteratively(iterable.iterator(), itemProcessor);
  }

  /**
   * Performs an asynchronous stepwise loop based on given start, end, and step values
   * using asynchronous loop calls.
   * <p>
   * The step direction must be incremental and reachable. Therefore, if the start value
   * is greater than the end value, or the step value is less than or equal to 0,
   * an exception will be thrown.
   *
   * @param start     the start value
   * @param end       the end value
   * @param step      the step value
   * @param processor the asynchronous stepwise loop logic
   * @return the result of the asynchronous loop execution
   * @throws IllegalArgumentException if the step is not incremental and reachable
   */
  default Future<Void> asyncCallStepwise(
    long start, long end, long step,
    BiFunction<Long, RepeatedlyCallTask, Future<Void>> processor) {
    if (step <= 0)
      throw new IllegalArgumentException("step must be greater than 0");
    if (start > end)
      throw new IllegalArgumentException("start must not be greater than end");
    AtomicLong ptr = new AtomicLong(start);
    return asyncCallRepeatedly(task -> Future
      .succeededFuture()
      .compose(vv -> processor.apply(ptr.get(), task)
                              .compose(v -> {
                                long y = ptr.addAndGet(step);
                                if (y >= end) {
                                  task.stop();
                                }
                                return Future.succeededFuture();
                              })));
  }

  /**
   * Performs an asynchronous stepwise loop for a specified number of times using
   * asynchronous loop calls, with the ability to interrupt early.
   * <p>
   * Based on {@link #asyncCallStepwise(long, long, long, BiFunction)}, with the start
   * value set to 0 and the step value set to 1.
   *
   * @param times     the number of iterations (i.e., the end value). When the number
   *                  of iterations is less than or equal to 0, a successful result
   *                  will be returned directly.
   * @param processor the asynchronous stepwise loop logic
   * @return the result of the asynchronous loop execution
   */
  default Future<Void> asyncCallStepwise(
    long times,
    BiFunction<Long, RepeatedlyCallTask, Future<Void>> processor
  ) {
    if (times <= 0) {
      return Future.succeededFuture();
    }
    return asyncCallStepwise(0, times, 1, processor);
  }

  /**
   * Performs an asynchronous stepwise loop for a specified number of times using
   * asynchronous loop calls.
   *
   * @param times     the number of iterations (i.e., the end value). When the number
   *                  of iterations is less than or equal to 0, a successful result
   *                  will be returned directly.
   * @param processor the asynchronous stepwise loop logic
   * @return the result of the asynchronous loop execution
   */
  default Future<Void> asyncCallStepwise(
    long times,
    Function<Long, Future<Void>> processor
  ) {
    if (times <= 0) {
      return Future.succeededFuture();
    }
    return asyncCallStepwise(0, times, 1, (aLong, repeatedlyCallTask) -> processor.apply(aLong));
  }

  /**
   * Executes an asynchronous logic in an infinite loop, continuing even if the loop
   * body throws an exception.
   * <p>
   * Before using this method, ensure that the logic meets the requirements and has
   * no side effects.
   *
   * @param supplier the asynchronous loop logic
   */
  default void asyncCallEndlessly(Supplier<Future<Void>> supplier) {
    asyncCallRepeatedly(routineResult -> Future
      .succeededFuture()
      .compose(v -> supplier.get())
      .eventually(Future::succeededFuture));
  }
}
