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

package io.vertx.core.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.util.ArrayList;
import java.util.Objects;

class FutureImpl<T> implements PromiseInternal<T>, Future<T> {

  private final ContextInternal context;
  private boolean failed;
  private boolean succeeded;
  private Handler<AsyncResult<T>> handler;
  private T result;
  private Throwable throwable;

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
    return result;
  }

  /**
   * An exception describing failure. This will be null if the operation succeeded.
   */
  public synchronized Throwable cause() {
    return throwable;
  }

  /**
   * Did it succeeed?
   */
  public synchronized boolean succeeded() {
    return succeeded;
  }

  /**
   * Did it fail?
   */
  public synchronized boolean failed() {
    return failed;
  }

  /**
   * Has it completed?
   */
  public synchronized boolean isComplete() {
    return failed || succeeded;
  }

  @Override
  public Future<T> onComplete(Handler<AsyncResult<T>> h) {
    Objects.requireNonNull(h, "No null handler accepted");
    synchronized (this) {
      if (!isComplete()) {
        if (handler == null) {
          handler = h;
        } else {
          addHandler(h);
        }
        return this;
      }
    }
    dispatch(h);
    return this;
  }

  private void addHandler(Handler<AsyncResult<T>> h) {
    Handlers<T> handlers;
    if (handler instanceof Handlers) {
      handlers = (Handlers<T>) handler;
    } else {
      handlers = new Handlers<>();
      handlers.add(handler);
      handler = handlers;
    }
    handlers.add(h);
  }

  protected void dispatch(Handler<AsyncResult<T>> handler) {
    if (handler instanceof Handlers) {
      for (Handler<AsyncResult<T>> h : (Handlers<T>)handler) {
        doDispatch(h);
      }
    } else {
      doDispatch(handler);
    }
  }

  private void doDispatch(Handler<AsyncResult<T>> handler) {
    if (context != null) {
      context.dispatch(this, handler);
    } else {
      handler.handle(this);
    }
  }

  @Override
  public boolean tryComplete(T result) {
    Handler<AsyncResult<T>> h;
    synchronized (this) {
      if (succeeded || failed) {
        return false;
      }
      this.result = result;
      succeeded = true;
      h = handler;
      handler = null;
    }
    if (h != null) {
      dispatch(h);
    }
    return true;
  }

  public void handle(Future<T> ar) {
    if (ar.succeeded()) {
      complete(ar.result());
    } else {
      fail(ar.cause());
    }
  }

  @Override
  public boolean tryFail(Throwable cause) {
    Handler<AsyncResult<T>> h;
    synchronized (this) {
      if (succeeded || failed) {
        return false;
      }
      this.throwable = cause != null ? cause : new NoStackTraceThrowable(null);
      failed = true;
      h = handler;
      handler = null;
    }
    if (h != null) {
      dispatch(h);
    }
    return true;
  }

  @Override
  public Future<T> future() {
    return this;
  }

  @Override
  public void operationComplete(io.netty.util.concurrent.Future<T> future) {
    if (future.isSuccess()) {
      complete(future.getNow());
    } else {
      fail(future.cause());
    }
  }

  @Override
  public String toString() {
    synchronized (this) {
      if (succeeded) {
        return "Future{result=" + result + "}";
      }
      if (failed) {
        return "Future{cause=" + throwable.getMessage() + "}";
      }
      return "Future{unresolved}";
    }
  }


  private class Handlers<T> extends ArrayList<Handler<AsyncResult<T>>> implements Handler<AsyncResult<T>> {
    @Override
    public void handle(AsyncResult<T> res) {
      for (Handler<AsyncResult<T>> handler : this) {
        handler.handle(res);
      }
    }
  }
}
