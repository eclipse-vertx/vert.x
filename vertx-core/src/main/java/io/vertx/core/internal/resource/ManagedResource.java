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
package io.vertx.core.internal.resource;

/**
 * A managed resource.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class ManagedResource {

  Runnable cleaner;
  private boolean shutdown;
  private boolean closed;
  private boolean disposed;
  private long acquireInProgress;
  private long refCount;

  public ManagedResource() {
  }

  boolean before() {
    synchronized (this) {
      if (disposed) {
        return false;
      }
      acquireInProgress++;
    }
    return true;
  }

  void after() {
    boolean dispose;
    synchronized (this) {
      acquireInProgress--;
      dispose = checkDispose();
    }
    // Dispose before callback otherwise we can have the callback handler retrying the same
    // endpoint and never get the callback it expects to creating an infinite loop
    if (dispose) {
      disposeInternal();
    }
  }

  protected void checkExpired() {
  }

  protected boolean incRefCount() {
    synchronized (this) {
      refCount++;
      return !closed;
    }
  }

  protected boolean decRefCount() {
    synchronized (this) {
      refCount--;
      if (!checkDispose()) {
        return false;
      }
    }
    disposeInternal();
    return true;
  }

  private void disposeInternal() {
    cleaner.run();
    cleanup();
  }

  private boolean checkDispose() {
    if (!disposed && refCount == 0 && acquireInProgress == 0) {
      disposed = true;
      return true;
    }
    return false;
  }

  /**
   * Close the resource, this method is called by the {@link ResourceManager} when it is closed.
   */
  final void close() {
    shutdown();
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
    }
    handleClose();
  }

  /**
   * Close the resource, this method is called by the {@link ResourceManager} when it is closed.
   */
  final void shutdown() {
    synchronized (this) {
      if (shutdown) {
        return;
      }
      shutdown = true;
    }
    handleShutdown();
  }

  /**
   * Hook to clean-up, e.g. unregistering metrics, this method is called when
   * the resource will not accept anymore reference counting.
   */
  protected void cleanup() {
  }

  /**
   * Hook to shut down the resource.
   */
  protected void handleShutdown() {
  }

  /**
   * Hook to close the resource.
   */
  protected void handleClose() {
  }
}
