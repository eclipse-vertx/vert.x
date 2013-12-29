/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.core.http.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;

public abstract class StandardHttpConnectionPool implements HttpPool {

  private static final Logger log = LoggerFactory.getLogger(StandardHttpConnectionPool.class);

  private final Queue<ClientConnection> available = new ArrayDeque<>();
  private int maxPoolSize = 1;
  private int connectionCount;
  private final Queue<Waiter> waiters = new ArrayDeque<>();

  /**
   * Set the maximum pool size to the value specified by {@code maxConnections}<p>
   * The client will maintain up to {@code maxConnections} HTTP connections in an internal pool<p>
   */
  public void setMaxPoolSize(int maxConnections) {
    this.maxPoolSize = maxConnections;
  }

  /**
   * Returns the maximum number of connections in the pool
   */
  public int getMaxPoolSize() {
    return maxPoolSize;
  }

  public synchronized void report() {
    log.trace("available: " + available.size() + " connection count: " + connectionCount + " waiters: " + waiters.size());
  }


  public void getConnection(Handler<ClientConnection> handler, Handler<Throwable> connectExceptionHandler, DefaultContext context) {
    boolean connect = false;
    ClientConnection conn;
    outer: synchronized (this) {
      conn = available.poll();
      if (conn != null) {
        break outer;
      } else {
        if (connectionCount < maxPoolSize) {
          //Create new connection
          connect = true;
          connectionCount++;
          break outer;
        }
        // Add to waiters
        waiters.add(new Waiter(handler, connectExceptionHandler, context));
      }
    }
    // We do this outside the sync block to minimise the critical section
    if (conn != null) {
      handler.handle(conn);
    }
    else if (connect) {
      connect(handler, connectExceptionHandler, context);
    }
  }

  /**
   * Inform the pool that the connection has been closed externally.
   */
  public void connectionClosed() {
    Waiter waiter;
    synchronized (this) {
      connectionCount--;
      if (connectionCount < maxPoolSize) {
        //Now the connection count has come down, maybe there is another waiter that can
        //create a new connection
        waiter = waiters.poll();
        if (waiter != null) {
          connectionCount++;
        }
      } else {
        waiter = null;
      }
    }
    // We do the actual connect outside the sync block to minimise the critical section
    if (waiter != null) {
      connect(waiter.handler, waiter.connectionExceptionHandler, waiter.context);
    }
  }

  /**
   * Return a connection to the pool so it can be used by others.
   */
  public void returnConnection(final ClientConnection conn) {
    Waiter waiter;
    synchronized (this) {
      //Return it to the pool
      waiter = waiters.poll();
      if (waiter == null) {
        available.add(conn);
      }
    }
    if (waiter != null) {
      final Waiter w = waiter;
      w.context.runOnContext(new VoidHandler() {
        public void handle() {
          w.handler.handle(conn);
        }
      });
    }
  }

  /**
   * Close the pool
   */
  public void close() {
    available.clear();
    waiters.clear();
  }

  /**
   * Implement this method in a sub-class to implement the actual connection creation for the specific type of connection
   */
  protected abstract void connect(final Handler<ClientConnection> connectHandler, final Handler<Throwable> connectErrorHandler, final DefaultContext context);

  private static class Waiter {
    final Handler<ClientConnection> handler;
    final Handler<Throwable> connectionExceptionHandler;
    final DefaultContext context;

    private Waiter(Handler<ClientConnection> handler, Handler<Throwable> connectionExceptionHandler, DefaultContext context) {
      this.handler = handler;
      this.connectionExceptionHandler = connectionExceptionHandler;
      this.context = context;
    }
  }
}
