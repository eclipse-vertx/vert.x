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

import io.vertx.core.impl.NoStackTraceThrowable;

import java.util.function.Function;

/**
 * Encapsulates the result of an asynchronous operation.
 * <p>
 * Many operations in Vert.x APIs provide results back by passing an instance of this in a {@link io.vertx.core.Handler}.
 * <p>
 * The result can either have failed or succeeded.
 * <p>
 * If it failed then the cause of the failure is available with {@link #cause}.
 * <p>
 * If it succeeded then the actual result is available with {@link #result}
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface AsyncResult<T> {

  /**
   * Created a succeeded async result with the specified result.
   *
   * @param result  the result
   * @param <T>  the result type
   * @return  the async result
   */
  static <T> AsyncResult<T> succeededAsyncResult(T result) {
    return new AsyncResult<T>() {
      @Override
      public T result() {
        return result;
      }

      @Override
      public Throwable cause() {
        return null;
      }

      @Override
      public boolean succeeded() {
        return true;
      }

      @Override
      public boolean failed() {
        return false;
      }
    };
  }

  /**
   * Create a failed async result with the specified failure cause.
   *
   * @param t  the failure cause as a Throwable
   * @param <T>  the result type
   * @return  the async result
   */
  static <T> AsyncResult<T> failedAsyncResult(Throwable t) {
    return new AsyncResult<T>() {
      @Override
      public T result() {
        return null;
      }

      @Override
      public Throwable cause() {
        return t;
      }

      @Override
      public boolean succeeded() {
        return false;
      }

      @Override
      public boolean failed() {
        return true;
      }
    };
  }

  /**
   * Create a failed async result with the specified failure message.
   *
   * @param failureMessage  the failure message
   * @param <T>  the result type
   * @return  the async result
   */
  static <T> AsyncResult<T> failedAsyncResult(String failureMessage) {
    return failedAsyncResult(new NoStackTraceThrowable(failureMessage));
  }

  /**
   * The result of the operation. This will be null if the operation failed.
   *
   * @return the result or null if the operation failed.
   */
  T result();

  /**
   * A Throwable describing failure. This will be null if the operation succeeded.
   *
   * @return the cause or null if the operation succeeded.
   */
  Throwable cause();

  /**
   * Did it succeed?
   *
   * @return true if it succeded or false otherwise
   */
  boolean succeeded();

  /**
   * Did it fail?
   *
   * @return true if it failed or false otherwise
   */
  boolean failed();

  /**
   * Apply a {@code mapper} function on this async result.<p>
   *
   * The {@code mapper} is called with the completed value and this mapper returns a value. This value will complete the result returned by this method call.<p>
   *
   * When this async result is failed, the failure will be propagated to the returned async result and the {@code mapper} will not be called.
   *
   * @param mapper the mapper function
   * @return the mapped async result
   */
  default <U> AsyncResult<U> map(Function<T, U> mapper) {
    if (mapper == null) {
      throw new NullPointerException();
    }
    if (succeeded()) {
      U mapped;
      try {
        mapped = mapper.apply(result());
      } catch (Throwable t) {
        return failedAsyncResult(t);
      }
      return succeededAsyncResult(mapped);
    } else {
      return failedAsyncResult(cause());
    }
  }

  /**
   * Map the result of this async result to a specific {@code value}.<p>
   *
   * When this async result succeeds, this {@code value} will succeeed the async result returned by this method call.<p>
   *
   * When this async result fails, the failure will be propagated to the returned async result.
   *
   * @param value the value that eventually completes the mapped async result
   * @return the mapped async result
   */
  default <V> AsyncResult<V> map(V value) {
    return map(t -> value);
  }

  /**
   * Map the result of this async result to {@code null}.<p>
   *
   * This is a convenience for {@code asyncResult.map((T) null)} or {@code asyncResult.map((Void) null)}.<p>
   *
   * When this async result succeeds, {@code null} will succeeed the async result returned by this method call.<p>
   *
   * When this async result fails, the failure will be propagated to the returned async result.
   *
   * @return the mapped async result
   */
  default <V> AsyncResult<V> mapEmpty() {
    return map((V)null);
  }

  /**
   * Apply a {@code mapper} function on this async result.<p>
   *
   * The {@code mapper} is called with the failure and this mapper returns a value. This value will complete the result returned by this method call.<p>
   *
   * When this async result is succeeded, the value will be propagated to the returned async result and the {@code mapper} will not be called.
   *
   * @param mapper the mapper function
   * @return the mapped async result
   */
  default AsyncResult<T> otherwise(Function<Throwable, T> mapper) {
    if (mapper == null) {
      throw new NullPointerException();
    }
    if (succeeded()) {
      return this;
    } else {
      T mapped;
      try {
        mapped = mapper.apply(cause());
      } catch (Throwable t) {
        return failedAsyncResult(t);
      }
      return succeededAsyncResult(mapped);
    }
  }

  /**
   * Map the failure of this async result to a specific {@code value}.<p>
   *
   * When this async result fails, this {@code value} will succeeed the async result returned by this method call.<p>
   *
   * When this async succeeds, the result will be propagated to the returned async result.
   *
   * @param value the value that eventually completes the mapped async result
   * @return the mapped async result
   */
  default AsyncResult<T> otherwise(T value) {
    return otherwise(err -> value);
  }

  /**
   * Map the failure of this async result to {@code null}.<p>
   *
   * This is a convenience for {@code asyncResult.otherwise((T) null)}.<p>
   *
   * When this async result fails, the {@code null} will succeeed the async result returned by this method call.<p>
   *
   * When this async succeeds, the result will be propagated to the returned async result.
   *
   * @return the mapped async result
   */
  default AsyncResult<T> otherwiseEmpty() {
    return otherwise(err -> null);
  }
}
