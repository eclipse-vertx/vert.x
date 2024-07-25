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
package io.vertx.core.net.impl.endpoint;

/**
 * An endpoint, i.e a set of connection to the same address.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class Endpoint {

  private final Runnable dispose;
  private boolean shutdown;
  private boolean closed;
  private boolean disposed;
  private long pendingRequestCount;
  private long refCount;

  public Endpoint(Runnable dispose) {
    this.dispose = dispose;
  }

  boolean before() {
    synchronized (this) {
      if (disposed) {
        return false;
      }
      pendingRequestCount++;
    }
    return true;
  }

  void after() {
    boolean dispose;
    synchronized (Endpoint.this) {
      pendingRequestCount--;
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
    // CHECK SHOULD CLOSE
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
    dispose.run();
    dispose();
  }

  private boolean checkDispose() {
    if (!disposed && refCount == 0 && pendingRequestCount == 0) {
      disposed = true;
      return true;
    }
    return false;
  }

  /**
   * Hook to cleanup when all metrics have been processed, e.g unregistering metrics, this method is called when
   * the endpoint will not accept anymore requests.
   */
  protected void dispose() {
  }

  /**
   * Close the endpoint, this will close all connections, this method is called by the {@link EndpointManager} when
   * it is closed.
   */
  final void close() {
    shutdown();
    synchronized (this) {
      if (closed) {
        return;
      }
    }
    handleClose();
  }

  protected void handleClose() {
  }

  /**
   * Close the endpoint, this will close all connections, this method is called by the {@link EndpointManager} when
   * it is closed.
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

  protected void handleShutdown() {
  }
}
