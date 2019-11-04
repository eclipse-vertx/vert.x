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
import io.vertx.core.CompositeFuture;
import io.vertx.core.Handler;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CompositeFutureImpl implements CompositeFuture {

  private static final Handler<AsyncResult<CompositeFuture>> NO_HANDLER = c -> {};

  public static CompositeFuture all(Future<?>... results) {
    CompositeFutureImpl composite = new CompositeFutureImpl(results);
    int len = results.length;
    for (int i = 0; i < len; i++) {
      results[i].setHandler(ar -> {
        Handler<AsyncResult<CompositeFuture>> handler = null;
        if (ar.succeeded()) {
          synchronized (composite) {
            composite.count++;
            if (!composite.isComplete() && composite.count == len) {
              handler = composite.setCompleted(null);
            }
          }
        } else {
          synchronized (composite) {
            if (!composite.isComplete()) {
              handler = composite.setCompleted(ar.cause());
            }
          }
        }
        if (handler != null) {
          handler.handle(composite);
        }
      });
    }
    if (len == 0) {
      composite.setCompleted(null);
    }
    return composite;
  }

  public static CompositeFuture any(Future<?>... results) {
    CompositeFutureImpl composite = new CompositeFutureImpl(results);
    int len = results.length;
    for (int i = 0;i < len;i++) {
      results[i].setHandler(ar -> {
        Handler<AsyncResult<CompositeFuture>> handler = null;
        if (ar.succeeded()) {
          synchronized (composite) {
            if (!composite.isComplete()) {
              handler = composite.setCompleted(null);
            }
          }
        } else {
          synchronized (composite) {
            composite.count++;
            if (!composite.isComplete() && composite.count == len) {
              handler = composite.setCompleted(ar.cause());
            }
          }
        }
        if (handler != null) {
          handler.handle(composite);
        }
      });
    }
    if (results.length == 0) {
      composite.setCompleted(null);
    }
    return composite;
  }

  private static final Function<CompositeFuture, Throwable> ALL = cf -> {
    int size = cf.size();
    for (int i = 0;i < size;i++) {
      if (!cf.succeeded(i)) {
        return cf.cause(i);
      }
    }
    return null;
  };

  public static CompositeFuture join(Future<?>... results) {
    return join(ALL, results);
  }

  private  static CompositeFuture join(Function<CompositeFuture, Throwable> pred, Future<?>... results) {
    CompositeFutureImpl composite = new CompositeFutureImpl(results);
    int len = results.length;
    for (int i = 0; i < len; i++) {
      results[i].setHandler(ar -> {
        Handler<AsyncResult<CompositeFuture>> handler = null;
        synchronized (composite) {
          composite.count++;
          if (!composite.isComplete() && composite.count == len) {
            // Take decision here
            Throwable failure = pred.apply(composite);
            handler = composite.setCompleted(failure);
          }
        }
        if (handler != null) {
          handler.handle(composite);
        }
      });
    }
    if (len == 0) {
      composite.setCompleted(null);
    }
    return composite;
  }

  private final Future[] results;
  private int count;
  private boolean completed;
  private Throwable cause;
  private Handler<AsyncResult<CompositeFuture>> handler;

  private CompositeFutureImpl(Future<?>... results) {
    this.results = results;
  }

  @Override
  public CompositeFuture onComplete(Handler<AsyncResult<CompositeFuture>> handler) {
    boolean call;
    synchronized (this) {
      this.handler = handler;
      call = completed;
    }
    if (call) {
      handler.handle(this);
    }
    return this;
  }

  @Override
  public synchronized Handler<AsyncResult<CompositeFuture>> getHandler() {
    return this.handler;
  }

  @Override
  public Throwable cause(int index) {
    return future(index).cause();
  }

  @Override
  public boolean succeeded(int index) {
    return future(index).succeeded();
  }

  @Override
  public boolean failed(int index) {
    return future(index).failed();
  }

  @Override
  public boolean isComplete(int index) {
    return future(index).isComplete();
  }

  @Override
  public <T> T resultAt(int index) {
    return this.<T>future(index).result();
  }

  private <T> Future<T> future(int index) {
    if (index < 0 || index > results.length) {
      throw new IndexOutOfBoundsException();
    }
    return (Future<T>) results[index];
  }

  @Override
  public int size() {
    return results.length;
  }

  @Override
  public synchronized boolean isComplete() {
    return completed;
  }

  @Override
  public synchronized boolean succeeded() {
    return completed && cause == null;
  }

  @Override
  public synchronized boolean failed() {
    return completed && cause != null;
  }

  @Override
  public synchronized Throwable cause() {
    return completed && cause != null ? cause : null;
  }

  @Override
  public synchronized CompositeFuture result() {
    return completed && cause == null ? this : null;
  }

  private Handler<AsyncResult<CompositeFuture>> setCompleted(Throwable cause) {
    synchronized (this) {
      if (completed) {
        return null;
      }
      this.completed = true;
      this.cause = cause;
      return handler != null ? handler : NO_HANDLER;
    }
  }
}
