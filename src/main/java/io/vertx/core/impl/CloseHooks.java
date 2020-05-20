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
import io.vertx.core.impl.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CloseHooks {

  private final Logger log;
  private boolean closeHooksRun;
  private Map<Closeable, CloseHooks> closeHooks;

  CloseHooks(Logger log) {
    this.log = log;
    this.closeHooks = new WeakHashMap<>();
  }

  /**
   * Add a close hook, notified when the {@link #run(Handler)} method is called.
   *
   * @param hook the hook to add
   */
  public synchronized void add(Closeable hook) {
    if (closeHooks == null) {
      throw new IllegalStateException();
    }
    if (hook instanceof CloseFuture) {
      CloseFuture fut = (CloseFuture) hook;
      hook = fut.register(this);
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
   * Run the close hooks.
   *
   * @param completionHandler called when all hooks have beene executed
   */
  void run(Handler<AsyncResult<Void>> completionHandler) {
    Map<Closeable, CloseHooks> copy;
    synchronized (this) {
      if (closeHooksRun) {
        // Sanity check
        throw new IllegalStateException("Close hooks already run");
      }
      closeHooksRun = true;
      copy = closeHooks;
      closeHooks = null;
    }
    // We want an immutable version of the list holding strong references to avoid racing against finalization
    List<Closeable> list = new ArrayList<>(copy.size());
    copy.keySet().forEach(list::add);
    int num = list.size();
    if (num > 0) {
      AtomicInteger count = new AtomicInteger();
      for (Closeable hook : list) {
        Promise<Void> promise = Promise.promise();
        promise.future().onComplete(ar -> {
          if (count.incrementAndGet() == num) {
            completionHandler.handle(Future.succeededFuture());
          }
        });
        try {
          hook.close(promise);
        } catch (Throwable t) {
          log.warn("Failed to run close hook", t);
          promise.tryFail(t);
        }
      }
    } else {
      completionHandler.handle(Future.succeededFuture());
    }
  }
}
