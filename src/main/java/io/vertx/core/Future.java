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
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.FutureFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
    if (result == null) {
      return factory.succeededFuture();
    } else {
      return factory.succeededFuture(result);
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
   * Add a handler to be notified of the result.
   * <br/>
   * @param handler the handler that will be called with the result
   * @return a reference to this, so it can be used fluently
   */
  @Fluent
  Future<T> onComplete(Handler<AsyncResult<T>> handler);

  /**
   * Add a handler to be notified of the succeeded result.
   * <br/>
   * @param handler the handler that will be called with the succeeded result
   * @return a reference to this, so it can be used fluently
   */
  @Fluent
  default Future<T> onSuccess(Handler<T> handler) {
    return onComplete(ar -> {
      if (ar.succeeded()) {
        handler.handle(ar.result());
      }
    });
  }

  /**
   * Add a handler to be notified of the failed result.
   * <br/>
   * @param handler the handler that will be called with the failed result
   * @return a reference to this, so it can be used fluently
   */
  @Fluent
  default Future<T> onFailure(Handler<Throwable> handler) {
    return onComplete(ar -> {
      if (ar.failed()) {
        handler.handle(ar.cause());
      }
    });
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
   * @return the context associated with this future or {@code null} when
   */
  default Context context() {
    return null;
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
  default <U> Future<U> compose(Function<T, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper) {
    if (successMapper == null) {
      throw new NullPointerException();
    }
    if (failureMapper == null) {
      throw new NullPointerException();
    }
    ContextInternal ctx = (ContextInternal) context();
    Promise<U> ret;
    if (ctx != null) {
      ret = ctx.promise();
    } else {
      ret = Promise.promise();
    }
    onComplete(ar -> {
      if (ar.succeeded()) {
        Future<U> apply;
        try {
          apply = successMapper.apply(ar.result());
        } catch (Throwable e) {
          ret.fail(e);
          return;
        }
        apply.onComplete(ret);
      } else {
        Future<U> apply;
        try {
          apply = failureMapper.apply(ar.cause());
        } catch (Throwable e) {
          ret.fail(e);
          return;
        }
        apply.onComplete(ret);
      }
    });
    return ret.future();
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
    ContextInternal ctx = (ContextInternal) context();
    Promise<U> ret;
    if (ctx != null) {
      ret = ctx.promise();
    } else {
      ret = Promise.promise();
    }
    onComplete(ar -> {
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
    return ret.future();
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
    ContextInternal ctx = (ContextInternal) context();
    Promise<V> ret;
    if (ctx != null) {
      ret = ctx.promise();
    } else {
      ret = Promise.promise();
    }
    onComplete(ar -> {
      if (ar.succeeded()) {
        ret.complete(value);
      } else {
        ret.fail(ar.cause());
      }
    });
    return ret.future();
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
    ContextInternal ctx = (ContextInternal) context();
    Promise<T> ret;
    if (ctx != null) {
      ret = ctx.promise();
    } else {
      ret = Promise.promise();
    }
    onComplete(ar -> {
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
        mapped.onComplete(ret);
      }
    });
    return ret.future();
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
    ContextInternal ctx = (ContextInternal) context();
    Promise<T> ret;
    if (ctx != null) {
      ret = ctx.promise();
    } else {
      ret = Promise.promise();
    }
    onComplete(ar -> {
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
    return ret.future();
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
    ContextInternal ctx = (ContextInternal) context();
    Promise<T> ret;
    if (ctx != null) {
      ret = ctx.promise();
    } else {
      ret = Promise.promise();
    }
    onComplete(ar -> {
      if (ar.succeeded()) {
        ret.complete(result());
      } else {
        ret.complete(value);
      }
    });
    return ret.future();
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

  @GenIgnore
  FutureFactory factory = ServiceHelper.loadFactory(FutureFactory.class);

}
