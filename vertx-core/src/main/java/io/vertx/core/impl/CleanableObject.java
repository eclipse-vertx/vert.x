/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import io.vertx.core.Future;

import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.time.Duration;

/**
 * Base object for cleanable proxies.
 */
public class CleanableObject<T> {

  public static final Duration DEFAULT_CLEAN_TIMEOUT = Duration.ofSeconds(30);

  private static class Action<T> extends WeakReference<CleanableResource<T>> implements Runnable {

    private Duration timeout = DEFAULT_CLEAN_TIMEOUT;
    private Future<Void> closeFuture;

    public Action(CleanableResource<T> resource) {
      super(resource);
    }

    @Override
    public void run() {
      CleanableResource<T> d = get();
      if (d != null) {
        closeFuture = d.shutdown(timeout);
      }
    }
  }

  private Cleaner.Cleanable cleanable;
  private Action<T> action;

  public CleanableObject(Cleaner cleaner, CleanableResource<T> dispose) {
    this.action = new Action<>(dispose);
    this.cleanable = cleaner.register(this, action);
  }

  protected final T get() {
    Action<T> action = this.action;
    CleanableResource<T> resource;
    return action != null && (resource = action.get()) != null ? resource.get() : null;
  }

  protected final T getOrDie() {
    T resource = get();
    if (resource == null) {
      throw new IllegalStateException();
    } else {
      return resource;
    }
  }

  public final Future<Void> shutdown(Duration timeout) {
    if (timeout.isNegative()) {
      throw new IllegalArgumentException();
    }
    Action<T> action;
    Cleaner.Cleanable cleanable;
    synchronized (this) {
      action = this.action;
      cleanable = this.cleanable;
      this.action = null;
      this.cleanable = null;
    }
    if (action != null) {
      assert cleanable != null;
      action.timeout = timeout;
      cleanable.clean();
      return action.closeFuture;
    } else {
      return Future.succeededFuture();
    }
  }
}
