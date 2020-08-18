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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.NoStackTraceThrowable;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;

/**
 * Future implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class FutureImpl<T> implements FutureInternal<T> {

  private static final Object NULL_VALUE = new Object();

  private final ContextInternal context;
  private Object value;
  private Listener<T> listener;

  /**
   * Create a future that hasn't completed yet
   */
  FutureImpl() {
    this(null);
  }

  /**
   * Create a future that hasn't completed yet
   */
  FutureImpl(ContextInternal context) {
    this.context = context;
  }

  public ContextInternal context() {
    return context;
  }

  /**
   * The result of the operation. This will be null if the operation failed.
   */
  public synchronized T result() {
    return value instanceof Throwable ? null : value == NULL_VALUE ? null : (T) value;
  }

  /**
   * An exception describing failure. This will be null if the operation succeeded.
   */
  public synchronized Throwable cause() {
    return value instanceof Throwable ? (Throwable) value : null;
  }

  /**
   * Did it succeeed?
   */
  public synchronized boolean succeeded() {
    return value != null && !(value instanceof Throwable);
  }

  /**
   * Did it fail?
   */
  public synchronized boolean failed() {
    return value instanceof Throwable;
  }

  /**
   * Has it completed?
   */
  public synchronized boolean isComplete() {
    return value != null;
  }

  @Override
  public Future<T> onSuccess(Handler<T> handler) {
    Objects.requireNonNull(handler, "No null handler accepted");
    addListener(new Listener<T>() {
      @Override
      public void onSuccess(T value) {
        if (context != null) {
          context.emit(value, handler);
        } else {
          handler.handle(value);
        }
      }
      @Override
      public void onFailure(Throwable failure) {
      }
    });
    return this;
  }

  @Override
  public Future<T> onFailure(Handler<Throwable> handler) {
    Objects.requireNonNull(handler, "No null handler accepted");
    addListener(new Listener<T>() {
      @Override
      public void onSuccess(T value) {
      }
      @Override
      public void onFailure(Throwable failure) {
        if (context != null) {
          context.emit(failure, handler);
        } else {
          handler.handle(failure);
        }
      }
    });
    return this;
  }

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> handler) {
    Objects.requireNonNull(handler, "No null handler accepted");
    Listener<T> listener;
    if (handler instanceof Listener) {
      listener = (Listener<T>) handler;
    } else {
      listener = new Listener<T>() {
        @Override
        public void onSuccess(T value) {
          if (context != null) {
            context.emit(FutureImpl.this, handler);
          } else {
            handler.handle(FutureImpl.this);
          }
        }
        @Override
        public void onFailure(Throwable failure) {
          if (context != null) {
            context.emit(FutureImpl.this, handler);
          } else {
            handler.handle(FutureImpl.this);
          }
        }
      };
    }
    addListener(listener);
    return this;
  }

  @Override
  public void addListener(Listener<T> listener) {
    Object value;
    synchronized (this) {
      value = this.value;
      if (value == null) {
        if (this.listener == null) {
          this.listener = listener;
        } else {
          ListenerArray<T> listeners;
          if (this.listener instanceof FutureImpl.ListenerArray) {
            listeners = (ListenerArray<T>) this.listener;
          } else {
            listeners = new ListenerArray<>();
            listeners.add(this.listener);
            this.listener = listeners;
          }
          listeners.add(listener);
        }
        return;
      }
    }
    if (value instanceof Throwable) {
      listener.onFailure((Throwable) value);
    } else {
      listener.onSuccess(value == NULL_VALUE ? null : (T) value);
    }
  }

  public boolean tryComplete(T result) {
    Listener<T> e;
    synchronized (this) {
      if (value != null) {
        return false;
      }
      value = result == null ? NULL_VALUE : result;
      e = listener;
      listener = null;
    }
    if (e != null) {
      e.onSuccess(result);
    }
    return true;
  }

  public boolean tryFail(Throwable cause) {
    Listener<T> e;
    synchronized (this) {
      if (value != null) {
        return false;
      }
      value = cause != null ? cause : new NoStackTraceThrowable(null);
      e = listener;
      listener = null;
    }
    if (e != null) {
      e.onFailure(cause);
    }
    return true;
  }

  @Override
  public String toString() {
    synchronized (this) {
      if (value instanceof Throwable) {
        return "Future{cause=" + ((Throwable)value).getMessage() + "}";
      }
      if (value != null) {
        return "Future{result=" + (value == NULL_VALUE ? "null" : value) + "}";
      }
      return "Future{unresolved}";
    }
  }

  private static class ListenerArray<T> extends ArrayList<Listener<T>> implements Listener<T> {
    @Override
    public void onSuccess(T value) {
      for (Listener<T> handler : this) {
        handler.onSuccess(value);
      }
    }
    @Override
    public void onFailure(Throwable failure) {
      for (Listener<T> handler : this) {
        handler.onFailure(failure);
      }
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
