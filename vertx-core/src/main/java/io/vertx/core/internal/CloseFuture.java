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

package io.vertx.core.internal;

import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.logging.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A close future object is a state machine managing the closing sequence of a resource. A close future can be closed
 * explicitly with the {@link #close()} method or when the future is unreachable in order to release the resource.
 *
 * <p> A closed future holds a set of nested {@link Closeable} that are processed when the future is closed. When a close
 * future is closed, nested {@link Closeable} will be closed and the close future will notify the completion of the close
 * sequence after the nested closeables are closed.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CloseFuture extends NestedCloseable implements Closeable {

  private final Logger log;
  private final Promise<Void> promise = Promise.promise();
  private boolean closed;
  private Map<Closeable, CloseFuture> children;

  public CloseFuture() {
    this(null);
  }

  public CloseFuture(Logger log) {
    this.log = log;
  }

  /**
   * Add a {@code child} closeable, notified when this instance is closed.
   *
   * @param closeable the closeable to add
   * @return whether the {@code closeable} could be added to the future
   */
  public synchronized boolean add(Closeable closeable) {
    if (closed) {
      return false;
    }
    if (closeable instanceof NestedCloseable) {
      NestedCloseable base = (NestedCloseable) closeable;
      synchronized (base) {
        if (base.owner != null) {
          throw new IllegalStateException();
        }
        base.owner = this;
      }
    }
    if (children == null) {
      children = new HashMap<>();
    }
    children.put(closeable, this);
    return true;
  }

  /**
   * Remove an existing {@code nested} closeable.
   *
   * @param nested the closeable to remove
   */
  public boolean remove(Closeable nested) {
    if (nested instanceof NestedCloseable) {
      NestedCloseable base = (NestedCloseable) nested;
      synchronized (base) {
        if (base.owner == this) {
          base.owner = null;
        }
      }
    }
    synchronized (this) {
      if (children != null) {
        return children.remove(nested) != null;
      }
    }
    return false;
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
    synchronized (this) {
      if (closed) {
        return promise.future();
      }
      closed = true;
    }
    cascadeClose();
    return promise.future();
  }

  private void cascadeClose() {
    List<Closeable> toClose = Collections.emptyList();
    synchronized (this) {
      if (children != null) {
        toClose = new ArrayList<>(children.keySet());
      }
      children = null;
    }
    // We want an immutable version of the list holding strong references to avoid racing against finalization
    int num = toClose.size();
    if (num > 0) {
      AtomicInteger count = new AtomicInteger();
      for (Closeable hook : toClose) {
        // Clear the reference before notifying to avoid a callback to this
        if  (hook instanceof NestedCloseable) {
          NestedCloseable base = (NestedCloseable) hook;
          synchronized (base) {
            base.owner = null;
          }
        }
        Promise<Void> p = Promise.promise();
        p.future().onComplete(ar -> {
          if (count.incrementAndGet() == num) {
            unregisterFromOwner();
            promise.complete();
          }
        });
        try {
          hook.close(p);
        } catch (Throwable t) {
          if (log != null) {
            log.warn("Failed to run close hook", t);
          }
          p.tryFail(t);
        }
      }
    } else {
      unregisterFromOwner();
      promise.complete();
    }
  }

  private void unregisterFromOwner() {
    CloseFuture owner;
    synchronized (this) {
      owner = super.owner;
    }
    if (owner != null) {
      owner.remove(this);
    }
  }

  /**
   * Run the close hooks, this method should not be called directly instead it should be called when
   * this close future is added to another close future.
   *
   * @param promise called when all hooks have been executed
   */
  public void close(Promise<Void> promise) {
    close().onComplete(promise);
  }
}
