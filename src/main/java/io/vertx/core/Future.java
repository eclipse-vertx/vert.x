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

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.CompositeFutureImpl;
import io.vertx.core.impl.future.FailedFuture;
import io.vertx.core.impl.future.SucceededFuture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Represents the result of an action that may, or may not, have occurred yet.
 * <p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Future<T> extends AsyncResult<T> {

  /**
   * Return a composite future, succeeded when all futures are succeeded, failed when any future is failed.
   * <p/>
   * The returned future fails as soon as one of {@code f1} or {@code f2} fails.
   *
   * @param f1 future
   * @param f2 future
   * @return the composite future
   */
  static CompositeFuture all(Future<?> f1, Future<?> f2) {
    return CompositeFutureImpl.all(f1, f2);
  }

  /**
   * Like {@link #all(Future, Future)} but with 3 futures.
   */
  static CompositeFuture all(Future<?> f1, Future<?> f2, Future<?> f3) {
    return CompositeFutureImpl.all(f1, f2, f3);
  }

  /**
   * Like {@link #all(Future, Future)} but with 4 futures.
   */
  static CompositeFuture all(Future<?> f1, Future<?> f2, Future<?> f3, Future<?> f4) {
    return CompositeFutureImpl.all(f1, f2, f3, f4);
  }

  /**
   * Like {@link #all(Future, Future)} but with 5 futures.
   */
  static CompositeFuture all(Future<?> f1, Future<?> f2, Future<?> f3, Future<?> f4, Future<?> f5) {
    return CompositeFutureImpl.all(f1, f2, f3, f4, f5);
  }

  /**
   * Like {@link #all(Future, Future)} but with 6 futures.
   */
  static CompositeFuture all(Future<?> f1, Future<?> f2, Future<?> f3, Future<?> f4, Future<?> f5, Future<?> f6) {
    return CompositeFutureImpl.all(f1, f2, f3, f4, f5, f6);
  }

  /**
   * Like {@link #all(Future, Future)} but with a list of futures.<p>
   *
   * When the list is empty, the returned future will be already completed.
   */
  static CompositeFuture all(List<? extends Future<?>> futures) {
    return CompositeFutureImpl.all(futures.toArray(new Future[0]));
  }

  /**
   * Return a composite future, succeeded when any futures is succeeded, failed when all futures are failed.
   * <p/>
   * The returned future succeeds as soon as one of {@code f1} or {@code f2} succeeds.
   *
   * @param f1 future
   * @param f2 future
   * @return the composite future
   */
  static CompositeFuture any(Future<?> f1, Future<?> f2) {
    return CompositeFutureImpl.any(f1, f2);
  }

  /**
   * Like {@link #any(Future, Future)} but with 3 futures.
   */
  static CompositeFuture any(Future<?> f1, Future<?> f2, Future<?> f3) {
    return CompositeFutureImpl.any(f1, f2, f3);
  }

  /**
   * Like {@link #any(Future, Future)} but with 4 futures.
   */
  static CompositeFuture any(Future<?> f1, Future<?> f2, Future<?> f3, Future<?> f4) {
    return CompositeFutureImpl.any(f1, f2, f3, f4);
  }

  /**
   * Like {@link #any(Future, Future)} but with 5 futures.
   */
  static CompositeFuture any(Future<?> f1, Future<?> f2, Future<?> f3, Future<?> f4, Future<?> f5) {
    return CompositeFutureImpl.any(f1, f2, f3, f4, f5);
  }

  /**
   * Like {@link #any(Future, Future)} but with 6 futures.
   */
  static CompositeFuture any(Future<?> f1, Future<?> f2, Future<?> f3, Future<?> f4, Future<?> f5, Future<?> f6) {
    return CompositeFutureImpl.any(f1, f2, f3, f4, f5, f6);
  }

  /**
   * Like {@link #any(Future, Future)} but with a list of futures.<p>
   *
   * When the list is empty, the returned future will be already completed.
   */
  static CompositeFuture any(List<? extends Future<?>> futures) {
    return CompositeFutureImpl.any(futures.toArray(new Future[0]));
  }

  /**
   * Return a composite future, succeeded when all futures are succeeded, failed when any future is failed.
   * <p/>
   * It always wait until all its futures are completed and will not fail as soon as one of {@code f1} or {@code f2} fails.
   *
   * @param f1 future
   * @param f2 future
   * @return the composite future
   */
  static CompositeFuture join(Future<?> f1, Future<?> f2) {
    return CompositeFutureImpl.join(f1, f2);
  }

  /**
   * Like {@link #join(Future, Future)} but with 3 futures.
   */
  static CompositeFuture join(Future<?> f1, Future<?> f2, Future<?> f3) {
    return CompositeFutureImpl.join(f1, f2, f3);
  }

  /**
   * Like {@link #join(Future, Future)} but with 4 futures.
   */
  static CompositeFuture join(Future<?> f1, Future<?> f2, Future<?> f3, Future<?> f4) {
    return CompositeFutureImpl.join(f1, f2, f3, f4);
  }

  /**
   * Like {@link #join(Future, Future)} but with 5 futures.
   */
  static CompositeFuture join(Future<?> f1, Future<?> f2, Future<?> f3, Future<?> f4, Future<?> f5) {
    return CompositeFutureImpl.join(f1, f2, f3, f4, f5);
  }

  /**
   * Like {@link #join(Future, Future)} but with 6 futures.
   */
  static CompositeFuture join(Future<?> f1, Future<?> f2, Future<?> f3, Future<?> f4, Future<?> f5, Future<?> f6) {
    return CompositeFutureImpl.join(f1, f2, f3, f4, f5, f6);
  }

  /**
   * Like {@link #join(Future, Future)} but with a list of futures.<p>
   *
   * When the list is empty, the returned future will be already completed.
   */
  static CompositeFuture join(List<? extends Future<?>> futures) {
    return CompositeFutureImpl.join(futures.toArray(new Future[0]));
  }

  /**
   * Create a future that hasn't completed yet and that is passed to the {@code handler} before it is returned.
   *
   * @param handler the handler
   * @param <T> the result type
   * @return the future.
   */
  static <T> Future<T> future(Handler<Promise<T>> handler) {
    Promise<T> promise = Promise.promise();
    try {
      handler.handle(promise);
    } catch (Throwable e){
      promise.tryFail(e);
    }
    return promise.future();
  }

  /**
   * Create a succeeded future with a null result
   *
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Future<T> succeededFuture() {
    return (Future<T>) SucceededFuture.EMPTY;
  }

  /**
   * Created a succeeded future with the specified result.
   *
   * @param result  the result
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Future<T> succeededFuture(T result) {
    if (result == null) {
      return succeededFuture();
    } else {
      return new SucceededFuture<>(result);
    }
  }

  /**
   * Create a failed future with the specified failure cause.
   *
   * @param t  the failure cause as a Throwable
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Future<T> failedFuture(Throwable t) {
    return new FailedFuture<>(t);
  }

  /**
   * Create a failed future with the specified failure message.
   *
   * @param failureMessage  the failure message
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Future<T> failedFuture(String failureMessage) {
    return new FailedFuture<>(failureMessage);
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
   * Add a handler to be notified of the result.
   * <p>
   * <em><strong>WARNING</strong></em>: this is a terminal operation.
   * If several {@code handler}s are registered, there is no guarantee that they will be invoked in order of registration.
   *
   * @param handler the handler that will be called with the result
   * @return a reference to this, so it can be used fluently
   */
  @Fluent
  Future<T> onComplete(Handler<AsyncResult<T>> handler);

  /**
   * Add handlers to be notified on succeeded result and failed result.
   * <p>
   * <em><strong>WARNING</strong></em>: this is a terminal operation.
   * If several {@code handler}s are registered, there is no guarantee that they will be invoked in order of registration.
   *
   * @param successHandler the handler that will be called with the succeeded result
   * @param failureHandler the handler that will be called with the failed result
   * @return a reference to this, so it can be used fluently
   */
  default Future<T> onComplete(Handler<T> successHandler, Handler<Throwable> failureHandler) {
      return onComplete(ar -> {
        if (successHandler != null && ar.succeeded()) {
          successHandler.handle(ar.result());
        } else if (failureHandler != null && ar.failed()) {
          failureHandler.handle(ar.cause());
        }
      });
  }

  /**
   * Add a handler to be notified of the succeeded result.
   * <p>
   * <em><strong>WARNING</strong></em>: this is a terminal operation.
   * If several {@code handler}s are registered, there is no guarantee that they will be invoked in order of registration.
   *
   * @param handler the handler that will be called with the succeeded result
   * @return a reference to this, so it can be used fluently
   */
  @Fluent
  default Future<T> onSuccess(Handler<T> handler) {
    return onComplete(handler, null);
  }

  /**
   * Add a handler to be notified of the failed result.
   * <p>
   * <em><strong>WARNING</strong></em>: this is a terminal operation.
   * If several {@code handler}s are registered, there is no guarantee that they will be invoked in order of registration.
   *
   * @param handler the handler that will be called with the failed result
   * @return a reference to this, so it can be used fluently
   */
  @Fluent
  default Future<T> onFailure(Handler<Throwable> handler) {
    return onComplete(null, handler);
  }

  /**
   * The result of the operation. This will be null if the operation failed.
   *
   * @return the result or null if the operation failed.
   */
  @Override
  T result();

  /**
   * A Throwable describing failure. This will be null if the operation succeeded.
   *
   * @return the cause or null if the operation succeeded.
   */
  @Override
  Throwable cause();

  /**
   * Did it succeed?
   *
   * @return true if it succeded or false otherwise
   */
  @Override
  boolean succeeded();

  /**
   * Did it fail?
   *
   * @return true if it failed or false otherwise
   */
  @Override
  boolean failed();

  /**
   * Alias for {@link #compose(Function)}.
   */
  default <U> Future<U> flatMap(Function<T, Future<U>> mapper) {
    return compose(mapper);
  }

  /**
   * Compose this future with a {@code mapper} function.<p>
   *
   * When this future (the one on which {@code compose} is called) succeeds, the {@code mapper} will be called with
   * the completed value and this mapper returns another future object. This returned future completion will complete
   * the future returned by this method call.<p>
   *
   * If the {@code mapper} throws an exception, the returned future will be failed with this exception.<p>
   *
   * When this future fails, the failure will be propagated to the returned future and the {@code mapper}
   * will not be called.
   *
   * @param mapper the mapper function
   * @return the composed future
   */
  default <U> Future<U> compose(Function<T, Future<U>> mapper) {
    return compose(mapper, Future::failedFuture);
  }

  /**
   * Handles a failure of this Future by returning the result of another Future.
   * If the mapper fails, then the returned future will be failed with this failure.
   *
   * @param mapper A function which takes the exception of a failure and returns a new future.
   * @return A recovered future
   */
  default Future<T> recover(Function<Throwable, Future<T>> mapper) {
    return compose(Future::succeededFuture, mapper);
  }

  /**
   * Compose this future with a {@code successMapper} and {@code failureMapper} functions.<p>
   *
   * When this future (the one on which {@code compose} is called) succeeds, the {@code successMapper} will be called with
   * the completed value and this mapper returns another future object. This returned future completion will complete
   * the future returned by this method call.<p>
   *
   * When this future (the one on which {@code compose} is called) fails, the {@code failureMapper} will be called with
   * the failure and this mapper returns another future object. This returned future completion will complete
   * the future returned by this method call.<p>
   *
   * If any mapper function throws an exception, the returned future will be failed with this exception.<p>
   *
   * @param successMapper the function mapping the success
   * @param failureMapper the function mapping the failure
   * @return the composed future
   */
  <U> Future<U> compose(Function<T, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper);

  /**
   * Transform this future with a {@code mapper} functions.<p>
   *
   * When this future (the one on which {@code transform} is called) completes, the {@code mapper} will be called with
   * the async result and this mapper returns another future object. This returned future completion will complete
   * the future returned by this method call.<p>
   *
   * If any mapper function throws an exception, the returned future will be failed with this exception.<p>
   *
   * @param mapper the function mapping the future
   * @return the transformed future
   */
  <U> Future<U> transform(Function<AsyncResult<T>, Future<U>> mapper);

  /**
   * Compose this future with a {@code function} that will be always be called.
   *
   * <p>When this future (the one on which {@code eventually} is called) completes, the {@code function} will be called
   * and this mapper returns another future object. This returned future completion will complete the future returned
   * by this method call with the original result of the future.
   *
   * <p>The outcome of the future returned by the {@code function} will not influence the nature
   * of the returned future.
   *
   * @param function the function returning the future.
   * @return the composed future
   * @deprecated instead use {@link #eventually(Supplier)}, this method will be removed in Vert.x 5
   */
  @Deprecated
  <U> Future<T> eventually(Function<Void, Future<U>> function);

  /**
   * Compose this future with a {@code supplier} that will be always be called.
   *
   * <p>When this future (the one on which {@code eventually} is called) completes, the {@code supplier} will be called
   * and this mapper returns another future object. This returned future completion will complete the future returned
   * by this method call with the original result of the future.
   *
   * <p>The outcome of the future returned by the {@code supplier} will not influence the nature
   * of the returned future.
   *
   * @param supplier the function returning the future.
   * @return the composed future
   */
  default <U> Future<T> eventually(Supplier<Future<U>> supplier) {
    return eventually(v -> supplier.get());
  }

  /**
   * Apply a {@code mapper} function on this future.<p>
   *
   * When this future succeeds, the {@code mapper} will be called with the completed value and this mapper
   * returns a value. This value will complete the future returned by this method call.<p>
   *
   * If the {@code mapper} throws an exception, the returned future will be failed with this exception.<p>
   *
   * When this future fails, the failure will be propagated to the returned future and the {@code mapper}
   * will not be called.
   *
   * @param mapper the mapper function
   * @return the mapped future
   */
  <U> Future<U> map(Function<T, U> mapper);

  /**
   * Map the result of a future to a specific {@code value}.<p>
   *
   * When this future succeeds, this {@code value} will complete the future returned by this method call.<p>
   *
   * When this future fails, the failure will be propagated to the returned future.
   *
   * @param value the value that eventually completes the mapped future
   * @return the mapped future
   */
  <V> Future<V> map(V value);

  /**
   * Map the result of a future to {@code null}.<p>
   *
   * This is a conveniency for {@code future.map((T) null)} or {@code future.map((Void) null)}.<p>
   *
   * When this future succeeds, {@code null} will complete the future returned by this method call.<p>
   *
   * When this future fails, the failure will be propagated to the returned future.
   *
   * @return the mapped future
   */
  @Override
  default <V> Future<V> mapEmpty() {
    return (Future<V>) AsyncResult.super.mapEmpty();
  }

  /**
   * Apply a {@code mapper} function on this future.<p>
   *
   * When this future fails, the {@code mapper} will be called with the completed value and this mapper
   * returns a value. This value will complete the future returned by this method call.<p>
   *
   * If the {@code mapper} throws an exception, the returned future will be failed with this exception.<p>
   *
   * When this future succeeds, the result will be propagated to the returned future and the {@code mapper}
   * will not be called.
   *
   * @param mapper the mapper function
   * @return the mapped future
   */
  Future<T> otherwise(Function<Throwable, T> mapper);

  /**
   * Map the failure of a future to a specific {@code value}.<p>
   *
   * When this future fails, this {@code value} will complete the future returned by this method call.<p>
   *
   * When this future succeeds, the result will be propagated to the returned future.
   *
   * @param value the value that eventually completes the mapped future
   * @return the mapped future
   */
  Future<T> otherwise(T value);

  /**
   * Map the failure of a future to {@code null}.<p>
   *
   * This is a convenience for {@code future.otherwise((T) null)}.<p>
   *
   * When this future fails, the {@code null} value will complete the future returned by this method call.<p>
   *
   * When this future succeeds, the result will be propagated to the returned future.
   *
   * @return the mapped future
   */
  default Future<T> otherwiseEmpty() {
    return (Future<T>) AsyncResult.super.otherwiseEmpty();
  }

  /**
   * Invokes the given {@code handler} upon completion.
   * <p>
   * If the {@code handler} throws an exception, the returned future will be failed with this exception.
   *
   * @param handler invoked upon completion of this future
   * @return a future completed after the {@code handler} has been invoked
   */
  default Future<T> andThen(Handler<AsyncResult<T>> handler) {
    return transform(ar -> {
      handler.handle(ar);
      return (Future<T>) ar;
    });
  }

  /**
   * Bridges this Vert.x future to a {@link CompletionStage} instance.
   * <p>
   * The {@link CompletionStage} handling methods will be called from the thread that resolves this future.
   *
   * @return a {@link CompletionStage} that completes when this future resolves
   */
  @GenIgnore
  default CompletionStage<T> toCompletionStage() {
    CompletableFuture<T> completableFuture = new CompletableFuture<>();
    onComplete(ar -> {
      if (ar.succeeded()) {
        completableFuture.complete(ar.result());
      } else {
        completableFuture.completeExceptionally(ar.cause());
      }
    });
    return completableFuture;
  }

  /**
   * Bridges a {@link CompletionStage} object to a Vert.x future instance.
   * <p>
   * The Vert.x future handling methods will be called from the thread that completes {@code completionStage}.
   *
   * @param completionStage a completion stage
   * @param <T>             the result type
   * @return a Vert.x future that resolves when {@code completionStage} resolves
   */
  @GenIgnore
  static <T> Future<T> fromCompletionStage(CompletionStage<T> completionStage) {
    Promise<T> promise = Promise.promise();
    completionStage.whenComplete((value, err) -> {
      if (err != null) {
        promise.fail(err);
      } else {
        promise.complete(value);
      }
    });
    return promise.future();
  }

  /**
   * Bridges a {@link CompletionStage} object to a Vert.x future instance.
   * <p>
   * The Vert.x future handling methods will be called on the provided {@code context}.
   *
   * @param completionStage a completion stage
   * @param context         a Vert.x context to dispatch to
   * @param <T>             the result type
   * @return a Vert.x future that resolves when {@code completionStage} resolves
   */
  @GenIgnore
  static <T> Future<T> fromCompletionStage(CompletionStage<T> completionStage, Context context) {
    Promise<T> promise = ((ContextInternal) context).promise();
    completionStage.whenComplete((value, err) -> {
      if (err != null) {
        promise.fail(err);
      } else {
        promise.complete(value);
      }
    });
    return promise.future();
  }
}
