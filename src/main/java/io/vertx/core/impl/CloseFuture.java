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

import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CloseFuture implements Closeable {

  private final Logger log;
  private final Promise<Void> promise;
  private boolean closed;
  private Map<Closeable, CloseFuture> closeHooks;

  public CloseFuture() {
    this(null);
  }

  public CloseFuture(Logger log) {
    this.promise = Promise.promise();
    this.log = log;
  }

  /**
   * Add a close hook, notified when the {@link #close(Promise)} )} method is called.
   *
   * @param hook the hook to add
   */
  public synchronized void add(Closeable hook) {
    if (closed) {
      throw new IllegalStateException();
    }
    if (hook instanceof CloseFuture) {
      // Close future might be closed independantly, so we optimize and remove the hooks when
      // the close future completes
      CloseFuture fut = (CloseFuture) hook;
      fut.future().onComplete(ar -> remove(fut));
    }
    if (closeHooks == null) {
      closeHooks = new WeakHashMap<>();
    }
    closeHooks.put(hook, this);
  }

  /**
   * Remove an existing hook.
   *
   * @param hook the hook to remove
   */
  public synchronized void remove(Closeable hook) {
    if (closeHooks != null) {
      closeHooks.remove(hook);
    }
  }

  /**
   * @return whether the future is closed.
   */
  public synchronized boolean isClosed() {
    return closed;
  }

  /**
   * @return the future completed after completion of all close hooks.
   */
  public Future<Void> future() {
    return promise.future();
  }

  /**
   * Run all close hooks, after completion of all hooks, the future is closed.
   *
   * @return the future completed after completion of all close hooks
   */
  public Future<Void> close() {
    boolean close;
    Map<Closeable, CloseFuture> set;
    synchronized (this) {
      close = !closed;
      set = closeHooks;
      closed = true;
      closeHooks = null;
    }
    if (close) {
      // We want an immutable version of the list holding strong references to avoid racing against finalization
      if (set != null && !set.isEmpty()) {
        List<Closeable> list = new ArrayList<>(set.keySet());
        int num = list.size();
        AtomicInteger count = new AtomicInteger();
        for (Closeable hook : list) {
          Promise<Void> closePromise = Promise.promise();
          closePromise.future().onComplete(ar -> {
            if (count.incrementAndGet() == num) {
              promise.complete();
            }
          });
          try {
            hook.close(closePromise);
          } catch (Throwable t) {
            if (log != null) {
              log.warn("Failed to run close hook", t);
            }
            closePromise.tryFail(t);
          }
        }
      } else {
        promise.complete();
      }
    }
    return promise.future();
  }

  /**
   * Run the close hooks.
   *
   * @param completionHandler called when all hooks have been executed
   */
  public void close(Promise<Void> completionHandler) {
    close().onComplete(completionHandler);
  }
}
