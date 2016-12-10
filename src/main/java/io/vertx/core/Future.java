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
  void fail(Throwable throwable);

  /**
   * Set the failure. Any handler will be called, if there is one, and the future will be marked as completed.
   *
   * @param failureMessage  the failure message
   */
  void fail(String failureMessage);

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
        apply.setHandler(ret.completer());
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
   * @return an handler completing this future
   */
  @CacheReturn
  default Handler<AsyncResult<T>> completer() {
    return ar -> {
      if (ar.succeeded()) {
        complete(ar.result());
      } else {
        fail(ar.cause());
      }
    };
  }

  FutureFactory factory = ServiceHelper.loadFactory(FutureFactory.class);

}
