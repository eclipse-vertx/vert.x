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

package io.vertx.core.impl.future;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

import java.util.Objects;
import java.util.function.Function;

/**
 * Future base implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class FutureBase<T> implements FutureInternal<T> {

  protected final ContextInternal context;

  /**
   * Create a future that hasn't completed yet
   */
  FutureBase() {
    this(null);
  }

  /**
   * Create a future that hasn't completed yet
   */
  FutureBase(ContextInternal context) {
    this.context = context;
  }

  public final ContextInternal context() {
    return context;
  }

  protected final void emitSuccess(T value, Listener<T> listener) {
    if (context != null && !context.isRunningOnContext()) {
      context.execute(() -> {
        ContextInternal prev = context.beginDispatch();
        try {
          listener.onSuccess(value);
        } catch (Throwable t) {
          context.reportException(t);
        } finally {
          context.endDispatch(prev);
        }
      });
    } else {
      listener.onSuccess(value);
    }
  }

  protected final void emitFailure(Throwable cause, Listener<T> listener) {
    if (context != null && !context.isRunningOnContext()) {
      context.execute(() -> {
        ContextInternal prev = context.beginDispatch();
        try {
          listener.onFailure(cause);
        } catch (Throwable t) {
          context.reportException(t);
        } finally {
          context.endDispatch(prev);
        }
      });
    } else {
      listener.onFailure(cause);
    }
  }

  protected final <U> void emit(U value, Handler<U> handler) {
    if (context != null && !context.isRunningOnContext()) {
      context.execute(() -> {
        ContextInternal prev = context.beginDispatch();
        try {
          handler.handle(value);
        } catch (Throwable t) {
          context.reportException(t);
        } finally {
          context.endDispatch(prev);
        }
      });
    } else {
      handler.handle(value);
    }
  }

  @Override
  public <U> Future<U> compose(Function<T, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper) {
    Objects.requireNonNull(successMapper, "No null success mapper accepted");
    Objects.requireNonNull(failureMapper, "No null failure mapper accepted");
    ComposeTransformation<T, U> transformation = new ComposeTransformation<>(context, successMapper, failureMapper);
    addListener(transformation);
    return transformation;
  }

  @Override
  public <U> Future<U> map(Function<T, U> mapper) {
    Objects.requireNonNull(mapper, "No null mapper accepted");
    MapTransformation<T, U> transformation = new MapTransformation<>(context, mapper);
    addListener(transformation);
    return transformation;
  }

  @Override
  public <V> Future<V> map(V value) {
    MapValueTransformation<T, V> transformation = new MapValueTransformation<>(context, value);
    addListener(transformation);
    return transformation;
  }

  @Override
  public Future<T> otherwise(Function<Throwable, T> mapper) {
    Objects.requireNonNull(mapper, "No null mapper accepted");
    OtherwiseTransformation<T> transformation = new OtherwiseTransformation<>(context, mapper);
    addListener(transformation);
    return transformation;
  }

  @Override
  public Future<T> otherwise(T value) {
    OtherwiseValueTransformation<T> transformation = new OtherwiseValueTransformation<>(context, value);
    addListener(transformation);
    return transformation;
  }
}
