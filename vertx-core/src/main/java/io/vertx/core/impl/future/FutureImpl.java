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
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.internal.ContextInternal;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Future implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FutureImpl<T> extends FutureBase<T> {

  private static final Object NULL_VALUE = new Object();

  private Object value;
  private Completable<T> listener;

  /**
   * Create a future that hasn't completed yet
   */
  protected FutureImpl() {
    super();
  }

  /**
   * Create a future that hasn't completed yet
   */
  protected FutureImpl(ContextInternal context) {
    super(context);
  }

  /**
   * The result of the operation. This will be null if the operation failed.
   */
  public synchronized T result() {
    return value instanceof CauseHolder ? null : value == NULL_VALUE ? null : (T) value;
  }

  /**
   * An exception describing failure. This will be null if the operation succeeded.
   */
  public synchronized Throwable cause() {
    return value instanceof CauseHolder ? ((CauseHolder)value).cause : null;
  }

  /**
   * Did it succeed?
   */
  public synchronized boolean succeeded() {
    return value != null && !(value instanceof CauseHolder);
  }

  /**
   * Did it fail?
   */
  public synchronized boolean failed() {
    return value instanceof CauseHolder;
  }

  /**
   * Has it completed?
   */
  public synchronized boolean isComplete() {
    return value != null;
  }

  @Override
  public Future<T> onSuccess(Handler<? super T> handler) {
    Objects.requireNonNull(handler, "No null handler accepted");
    addListener(new Completable<T>() {
      @Override
      public void complete(T result, Throwable failure) {
        if (failure == null) {
          try {
            handler.handle(result);
          } catch (Throwable t) {
            if (context != null) {
              context.reportException(t);
            } else {
              throw t;
            }
          }
        }
      }
    });
    return this;
  }

  @Override
  public Future<T> onFailure(Handler<? super Throwable> handler) {
    Objects.requireNonNull(handler, "No null handler accepted");
    addListener((value, err) -> {
      if (err != null) {
        try {
          handler.handle(err);
        } catch (Throwable t) {
          if (context != null) {
            context.reportException(t);
          } else {
            throw t;
          }
        }
      }
    });
    return this;
  }

  @Override
  public Future<T> onComplete(Handler<? super T> successHandler, Handler<? super Throwable> failureHandler) {
    addListener((value, err) -> {
      try {
        if (err == null) {
          if (successHandler != null) {
            successHandler.handle(value);
          }
        } else {
          if (failureHandler != null) {
            failureHandler.handle(err);
          }
        }
      } catch (Throwable t) {
        if (context != null) {
          context.reportException(t);
        } else {
          throw t;
        }
      }
    });
    return this;
  }

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> handler) {
    Objects.requireNonNull(handler, "No null handler accepted");
    Completable<T> listener;
    if (handler instanceof Completable) {
      listener = (Completable<T>) handler;
    } else {
      listener = (value, err) -> {
        try {
          handler.handle(FutureImpl.this);
        } catch (Throwable t) {
          if (context != null) {
            context.reportException(t);
          } else {
            throw t;
          }
        }
      };
    }
    addListener(listener);
    return this;
  }

  @Override
  public void addListener(Completable<T> listener) {
    Object v;
    synchronized (this) {
      v = value;
      if (v == null) {
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
    if (v instanceof CauseHolder) {
      emitResult(null, ((CauseHolder)v).cause, listener);
    } else {
      if (v == NULL_VALUE) {
        v = null;
      }
      emitResult((T) v, null, listener);
    }
  }

  @Override
  public void removeListener(Completable<T> l) {
    synchronized (this) {
      Object listener = this.listener;
      if (listener == l) {
        this.listener = null;
      } else if (listener instanceof ListenerArray<?>) {
        ListenerArray<?> listeners = (ListenerArray<?>) listener;
        listeners.remove(l);
      }
    }
  }

  final boolean handleInternal(T result, Throwable err) {
    Completable<? super T> l;
    synchronized (this) {
      if (value != null) {
        return false;
      }
      value = err != null ? new CauseHolder(err) : (result == null ? NULL_VALUE : result);
      l = listener;
      listener = null;
    }
    if (l != null) {
      emitResult(result, err, l);
    }
    return true;
  }

  public final boolean tryComplete(T result) {
    return handleInternal(result, null);
  }

  public final boolean tryFail(Throwable cause) {
    if (cause == null) {
      cause = new NoStackTraceThrowable(null);
    }
    return handleInternal(null, cause);
  }

  @Override
  public String toString() {
    synchronized (this) {
      if (value instanceof CauseHolder) {
        return "Future{cause=" + ((CauseHolder)value).cause.getMessage() + "}";
      }
      if (value != null) {
        if (value == NULL_VALUE) {
          return "Future{result=null}";
        }
        StringBuilder sb = new StringBuilder("Future{result=");
        formatValue(value, sb);
        sb.append("}");
        return sb.toString();
      }
      return "Future{unresolved}";
    }
  }

  protected void formatValue(Object value, StringBuilder sb) {
    sb.append(value);
  }

  private static class ListenerArray<T> extends ArrayList<Completable<T>> implements Completable<T> {
    @Override
    public void complete(T result, Throwable failure) {
      for (Completable<T> handler : this) {
        handler.complete(result, failure);
      }
    }
  }

  private static class CauseHolder {

    private final Throwable cause;

    CauseHolder(Throwable cause) {
      this.cause = cause;
    }
  }
}
