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
package io.vertx.core.net.impl.clientconnection;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.impl.ContextInternal;

import java.util.Set;

/**
 * An endpoint, i.e a set of connection to the same address.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class Endpoint<C> {

  private final Set<C> connectionMap = new ConcurrentHashSet<>();
  private final Runnable dispose;
  private boolean closed;
  private boolean disposed;
  private long pendingRequestCount;
  private long openConnectionCount;

  public Endpoint(Runnable dispose) {
    this.dispose = dispose;
  }

  public boolean getConnection(ContextInternal ctx, Handler<AsyncResult<C>> handler) {
    synchronized (this) {
      if (disposed) {
        return false;
      }
      pendingRequestCount++;
    }
    requestConnection(ctx, ar -> {
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
      handler.handle(ar);
    });
    return true;
  }

  public abstract void requestConnection(ContextInternal ctx, Handler<AsyncResult<C>> handler);

  protected void connectionAdded(C conn) {
    synchronized (this) {
      if (connectionMap.add(conn)) {
        openConnectionCount++;
      } else {
        System.out.println("BUG!!!");
      }
      if (!closed) {
        return;
      }
    }
    close(conn);
  }

  protected void connectionRemoved(C conn) {

    // CHECK SHOULD CLOSE

    synchronized (this) {
      if (connectionMap.remove(conn)) {
        openConnectionCount--;
      } else {
        System.out.println("BUG!!!!");
      }
      if (!checkDispose()) {
        return;
      }
    }
    disposeInternal();
  }

  private void disposeInternal() {
    dispose.run();
    dispose();
  }

  private boolean checkDispose() {
    if (!disposed && openConnectionCount == 0 && pendingRequestCount == 0) {
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
   * Close the {@code connection}.
   */
  protected void close(C connection) {
  }

  /**
   * Close the endpoint, this will close all connections, this method is called by the {@link ConnectionManager} when
   * it is closed.
   */
  protected void close() {
    synchronized (this) {
      if (closed) {
        throw new IllegalStateException();
      }
      closed = true;
    }
    for (C conn : connectionMap) {
      close(conn);
    }
  }
}
