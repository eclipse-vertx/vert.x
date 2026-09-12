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
package io.vertx.core.internal;

import io.vertx.core.Future;

import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.time.Duration;

/**
 * Base object for cleanable resource proxies, that means proxies that can be collected and if they do will release
 * the actual underlying resource.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanableResource<R> {

  public static final Duration DEFAULT_CLEAN_TIMEOUT = Duration.ofSeconds(30);

  private static class Action<T> extends WeakReference<CloseableResource<? extends T>> implements Runnable {

    private Duration timeout = DEFAULT_CLEAN_TIMEOUT;
    private Future<Void> closeFuture;

    public Action(CloseableResource<? extends T> resource) {
      super(resource);
    }

    @Override
    public void run() {
      CloseableResource<? extends T> d = get();
      if (d != null) {
        closeFuture = d.shutdown(timeout);
      } else {
        closeFuture = Future.succeededFuture();
      }
    }
  }

  private Cleaner.Cleanable cleanable;
  private Action<R> action;

  public CleanableResource(Cleaner cleaner, CloseableResource<? extends R> dispose) {
    this.action = new Action<>(dispose);
    this.cleanable = cleaner.register(this, action);
  }

  /**
   * @return the actual resource or {@code null} when not available
   */
  protected final R get() {
    Action<R> action = this.action;
    CloseableResource<? extends R> resource;
    return action != null && (resource = action.get()) != null ? resource.get() : null;
  }

  /**
   * @return the actual resource or throws an {@link IllegalStateException} when not available
   */
  protected final R getOrDie() {
    R resource = get();
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
    Action<R> action;
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
