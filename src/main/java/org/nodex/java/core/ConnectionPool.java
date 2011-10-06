/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core;

import org.nodex.java.core.internal.NodexInternal;
import org.nodex.java.core.logging.Logger;

import java.util.LinkedList;
import java.util.Queue;

/**
 * <p>A simple, non-blocking pool implementation</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class ConnectionPool<T> {

  private static final Logger log = Logger.getLogger(ConnectionPool.class);

  private final Queue<T> available = new LinkedList<>();
  private int maxPoolSize = 1;
  private int connectionCount;
  private final Queue<Waiter> waiters = new LinkedList<>();

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

  /**
   * Get a connection from the pool. The connection is returned in the handler, some time in the future if a
   * connection becomes available.
   * @param handler The handler
   * @param contextID The context id
   */
  public void getConnection(Handler<T> handler, long contextID) {
    boolean connect = false;
    T conn;
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
        waiters.add(new Waiter(handler, contextID));
      }
    }
    // We do this outside the sync block to minimise the critical section
    if (conn != null) {
      handler.handle(conn);
    }
    else if (connect) {
      connect(handler, contextID);
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
      connect(waiter.handler, waiter.contextID);
    }
  }

  /**
   * Return a connection to the pool so it can be used by others.
   */
  public void returnConnection(final T conn) {
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
      NodexInternal.instance.executeOnContext(w.contextID, new Runnable() {
        public void run() {
          NodexInternal.instance.setContextID(w.contextID);
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
  protected abstract void connect(final Handler<T> connectHandler, final long contextID);

  private class Waiter {
    final Handler<T> handler;
    final long contextID;

    private Waiter(Handler<T> handler, long contextID) {
      this.handler = handler;
      this.contextID = contextID;
    }
  }
}
