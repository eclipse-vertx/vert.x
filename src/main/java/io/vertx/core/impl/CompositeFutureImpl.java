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
import io.vertx.core.Promise;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CompositeFutureImpl implements CompositeFuture {

  public static CompositeFuture all(Future<?>... results) {
    CompositeFutureImpl composite = new CompositeFutureImpl(results);
    int len = results.length;
    for (Future<?> result : results) {
      result.onComplete(ar -> {
        if (ar.succeeded()) {
          synchronized (composite) {
            composite.count++;
            if (composite.isComplete() || composite.count < len) {
              return;
            }
          }
          composite.succeed();
        } else {
          synchronized (composite) {
            if (composite.isComplete()) {
              return;
            }
          }
          composite.fail(ar.cause());
        }
      });
    }
    if (len == 0) {
      composite.succeed();
    }
    return composite;
  }

  public static CompositeFuture any(Future<?>... results) {
    CompositeFutureImpl composite = new CompositeFutureImpl(results);
    int len = results.length;
    for (Future<?> result : results) {
      result.onComplete(ar -> {
        if (ar.succeeded()) {
          synchronized (composite) {
            if (composite.isComplete()) {
              return;
            }
          }
          composite.succeed();
        } else {
          synchronized (composite) {
            composite.count++;
            if (composite.isComplete() || composite.count < len) {
              return;
            }
          }
          composite.fail(ar.cause());
        }
      });
    }
    if (results.length == 0) {
      composite.succeed();
    }
    return composite;
  }

  private static final Function<CompositeFuture, Object> ALL = cf -> {
    int size = cf.size();
    for (int i = 0;i < size;i++) {
      if (!cf.succeeded(i)) {
        return cf.cause(i);
      }
    }
    return cf;
  };

  public static CompositeFuture join(Future<?>... results) {
    return join(ALL, results);
  }

  private  static CompositeFuture join(Function<CompositeFuture, Object> pred, Future<?>... results) {
    CompositeFutureImpl composite = new CompositeFutureImpl(results);
    int len = results.length;
    for (Future<?> result : results) {
      result.onComplete(ar -> {
        synchronized (composite) {
          composite.count++;
          if (composite.isComplete() || composite.count < len) {
            return;
          }
        }
        composite.complete(pred.apply(composite));
      });
    }
    if (len == 0) {
      composite.succeed();
    }
    return composite;
  }

  private final Future[] results;
  private int count;
  private Object result;
  private Promise<CompositeFuture> promise;

  private CompositeFutureImpl(Future<?>... results) {
    this.results = results;
    this.promise = Promise.promise();
  }

  @Override
  public CompositeFuture onComplete(Handler<AsyncResult<CompositeFuture>> handler) {
    promise.future().onComplete(handler);
    return this;
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
    return result != null;
  }

  @Override
  public synchronized boolean succeeded() {
    return result == this;
  }

  @Override
  public synchronized boolean failed() {
    return result instanceof Throwable;
  }

  @Override
  public synchronized Throwable cause() {
    return result instanceof Throwable ? (Throwable) result : null;
  }

  @Override
  public synchronized CompositeFuture result() {
    return result == this ? this : null;
  }

  private void succeed() {
    complete(this);
  }

  private void fail(Throwable t) {
    complete(t);
  }

  private void complete(Object result) {
    synchronized (this) {
      this.result = result;
    }
    promise.handle(this);
  }
}
