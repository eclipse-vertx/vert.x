/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 *
 * @author Nathan Pahucki
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class PriorityHttpConnectionPool implements HttpPool {

  private static final Logger log = LoggerFactory.getLogger(PriorityHttpConnectionPool.class);

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

  public void getConnection(Handler<ClientConnection> handler,Handler<Throwable> connectExceptionHandler, DefaultContext context) {
    boolean connect = false;
    ClientConnection conn;
    outer: synchronized (this) {
      conn = selectConnection(available, connectionCount, maxPoolSize);
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
    } else if (connect) {
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
      w.context.execute(new Runnable() {
        public void run() {
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

  private ClientConnection selectConnection(Queue<ClientConnection> available, int connectionCount, int maxPoolSize) {
    ClientConnection conn = null;

    if (!available.isEmpty()) {
      final boolean useOccupiedConnections = connectionCount >= maxPoolSize;

      for (final ClientConnection c : available) {

        // Ideal situation for all cases, a cached but unoccupied connection.
        if (c.getOutstandingRequestCount() == 0 && !c.isClosed()) {
          conn = c;
          break;
        }

        if (useOccupiedConnections) {
          // Otherwise, lets try to pick the connection that has the least amount of outstanding requests on it,
          // even though we don't have any good way to know how long the requests in the front of this one might take
          // it's still better than the old behavior which seems to glob all the requests into the first connection
          // in the available list.
          if (conn == null || (conn.getOutstandingRequestCount() > c.getOutstandingRequestCount() && !c.isClosed())) {
            conn = c;
          }
        }
      }

      if (conn != null) available.remove(conn);
    }
    return conn; // might still be null, which would either create a connection, or put the request in a wait list
  }


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
