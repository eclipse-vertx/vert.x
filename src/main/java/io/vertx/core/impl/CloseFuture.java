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
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

/**
 * An helper class for managing close operations.
 */
public class CloseFuture implements Future<Void>, Closeable {

  private final Promise<Void> promise;
  private Closeable resource;
  private CloseHooks hooks;
  private Closeable hook;
  private boolean closed;

  public CloseFuture() {
    this.promise = Promise.promise();
  }

  public CloseFuture(Closeable resource) {
    this.promise = Promise.promise();
    this.resource = resource;
  }

  public synchronized void init(Closeable closeable) {
    if (closed) {
      throw new IllegalStateException();
    }
    this.resource = closeable;
  }

  synchronized Closeable register(CloseHooks hooks) {
    if (closed) {
      return null;
    }
    if (this.hooks != null) {
      throw new IllegalStateException();
    }
    this.hooks = hooks;
    this.hook = p -> {
      boolean close;
      Closeable resource;
      synchronized (CloseFuture.this) {
        close = !closed;
        resource = this.resource;
        this.closed = true;
        this.hook = null;
        this.hooks = null;
        this.resource = null;
      }
      if (close) {
        resource.close(p);
        p.future().onComplete(promise);
      } else {
        p.complete();
      }
    };
    return hook;
  }

  /**
   * Called by client
   */
  public void close(Promise<Void> promise) {
    boolean close;
    CloseHooks hooks;
    Closeable hook;
    Closeable resource;
    synchronized (this) {
      close = !closed;
      hooks = this.hooks;
      hook = this.hook;
      resource = this.resource;
      this.closed = true;
      this.hooks = null;
      this.hook = null;
      this.resource = null;
    }
    if (resource == null) {
      promise.fail("Close future not initialized");
    } else if (close) {
      if (hooks != null) {
        hooks.remove(hook);
      }
      resource.close(promise);
      promise.future().onComplete(this.promise);
    } else {
      promise.complete();
    }
  }

  public synchronized boolean isClosed() {
    return closed;
  }

  @Override
  public boolean isComplete() {
    return promise.future().isComplete();
  }

  @Override
  public Future<Void> onComplete(Handler<AsyncResult<Void>> handler) {
    promise.future().onComplete(handler);
    return this;
  }

  @Override
  public Void result() {
    return promise.future().result();
  }

  @Override
  public Throwable cause() {
    return promise.future().cause();
  }

  @Override
  public boolean succeeded() {
    return promise.future().succeeded();
  }

  @Override
  public boolean failed() {
    return promise.future().failed();
  }
}
