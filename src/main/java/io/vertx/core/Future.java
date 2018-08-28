/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.spi.FutureFactory;

import java.util.function.Function;

/**
 * Represents the result of an action that may, or may not, have occurred yet.
 * <p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Future<T> extends AsyncResult<T>, Handler<AsyncResult<T>> {

  /**
   * Create a future that hasn't completed yet and that is passed to the {@code handler} before it is returned.
   *
   * @param handler the handler
   * @param <T> the result type
   * @return the future.
   */
  static <T> Future<T> future(Handler<Future<T>> handler) {
    Future<T> fut = future();
    handler.handle(fut);
    return fut;
  }

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
    return factory.succeededFuture();
  }

  /**
   * Created a succeeded future with the specified result.
   *
   * @param result  the result
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Future<T> succeededFuture(T result) {
    return factory.succeededFuture(result);
  }

  /**
   * Create a failed future with the specified failure cause.
   *
   * @param t  the failure cause as a Throwable
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Future<T> failedFuture(Throwable t) {
    return factory.failedFuture(t);
  }

  /**
   * Create a failed future with the specified failure message.
   *
   * @param failureMessage  the failure message
   * @param <T>  the result type
   * @return  the future
   */
  static <T> Future<T> failedFuture(String failureMessage) {
    return factory.failureFuture(failureMessage);
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
   * @return a reference to this, so it can be used fluently
   *
   */
  @Fluent
  Future<T> setHandler(Handler<AsyncResult<T>> handler);

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
   * Compose this future with a provided {@code next} future.<p>
   *
   * When this (the one on which {@code compose} is called) future succeeds, the {@code handler} will be called with
   * the completed value, this handler should complete the next future.<p>
   *
   * If the {@code handler} throws an exception, the returned future will be failed with this exception.<p>
   *
   * When this future fails, the failure will be propagated to the {@code next} future and the {@code handler}
   * will not be called.
   *
   * @param handler the handler
   * @param next the next future
   * @return the next future, used for chaining
   */
  default <U> Future<U> compose(Handler<T> handler, Future<U> next) {
    setHandler(ar -> {
      if (ar.succeeded()) {
        try {
          handler.handle(ar.result());
        } catch (Throwable err) {
          if (next.isComplete()) {
            throw err;
          }
          next.fail(err);
        }
      } else {
        next.fail(ar.cause());
      }
    });
    return next;
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
    if (mapper == null) {
      throw new NullPointerException();
    }
    Future<U> ret = Future.future();
    setHandler(ar -> {
      if (ar.succeeded()) {
        Future<U> apply;
        try {
          apply = mapper.apply(ar.result());
        } catch (Throwable e) {
          ret.fail(e);
          return;
        }
        apply.setHandler(ret);
      } else {
        ret.fail(ar.cause());
      }
    });
    return ret;
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
  default <U> Future<U> map(Function<T, U> mapper) {
    if (mapper == null) {
      throw new NullPointerException();
    }
    Future<U> ret = Future.future();
    setHandler(ar -> {
      if (ar.succeeded()) {
        U mapped;
        try {
          mapped = mapper.apply(ar.result());
        } catch (Throwable e) {
          ret.fail(e);
          return;
        }
        ret.complete(mapped);
      } else {
        ret.fail(ar.cause());
      }
    });
    return ret;
  }

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
  default <V> Future<V> map(V value) {
    Future<V> ret = Future.future();
    setHandler(ar -> {
      if (ar.succeeded()) {
        ret.complete(value);
      } else {
        ret.fail(ar.cause());
      }
    });
    return ret;
  }

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
   * Succeed or fail this future with the {@link AsyncResult} event.
   *
   * @param asyncResult the async result to handle
   */
  @GenIgnore
  @Override
  void handle(AsyncResult<T> asyncResult);

  /**
   * @return an handler completing this future
   */
  @CacheReturn
  default Handler<AsyncResult<T>> completer() {
    return this;
  }

  /**
   * Handles a failure of this Future by returning the result of another Future.
   * If the mapper fails, then the returned future will be failed with this failure.
   *
   * @param mapper A function which takes the exception of a failure and returns a new future.
   * @return A recovered future
   */
  default Future<T> recover(Function<Throwable, Future<T>> mapper) {
    if (mapper == null) {
      throw new NullPointerException();
    }
    Future<T> ret = Future.future();
    setHandler(ar -> {
      if (ar.succeeded()) {
        ret.complete(result());
      } else {
        Future<T> mapped;
        try {
          mapped = mapper.apply(ar.cause());
        } catch (Throwable e) {
          ret.fail(e);
          return;
        }
        mapped.setHandler(ret);
      }
    });
    return ret;
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
  default Future<T> otherwise(Function<Throwable, T> mapper) {
    if (mapper == null) {
      throw new NullPointerException();
    }
    Future<T> ret = Future.future();
    setHandler(ar -> {
      if (ar.succeeded()) {
        ret.complete(result());
      } else {
        T value;
        try {
          value = mapper.apply(ar.cause());
        } catch (Throwable e) {
          ret.fail(e);
          return;
        }
        ret.complete(value);
      }
    });
    return ret;
  }

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
  default Future<T> otherwise(T value) {
    Future<T> ret = Future.future();
    setHandler(ar -> {
      if (ar.succeeded()) {
        ret.complete(result());
      } else {
        ret.complete(value);
      }
    });
    return ret;
  }

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

  @GenIgnore
  FutureFactory factory = ServiceHelper.loadFactory(FutureFactory.class);

}
