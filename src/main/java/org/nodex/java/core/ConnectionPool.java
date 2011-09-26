/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core;

import org.nodex.java.core.internal.NodexInternal;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>A simple, non-blocking pool implementation</p>
 *
 * <p>TODO the implementation can be improved. Currently it uses synchronization which may cause
 * contention issues under high load. Consider replacing with lock-free algorithm.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class ConnectionPool<T> {

  private final Queue<T> available = new ConcurrentLinkedQueue<>();
  private int maxPoolSize = 1;
  private final AtomicInteger connectionCount = new AtomicInteger(0);
  private final Queue<Waiter> waiters = new ConcurrentLinkedQueue<>();

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

  /**
   * Get a connection from the pool. The connection is returned in the handler, some time in the future if a
   * connection becomes available.
   * @param handler The handler
   * @param contextID The context id
   */
  public synchronized void getConnection(Handler<T> handler, long contextID) {
    T conn = available.poll();
    if (conn != null) {
      handler.handle(conn);
    } else {
      if (connectionCount.get() < maxPoolSize) {
        if (connectionCount.incrementAndGet() <= maxPoolSize) {
          //Create new connection
          connect(handler, contextID);
          return;
        } else {
          connectionCount.decrementAndGet();
        }
      }
      // Add to waiters
      waiters.add(new Waiter(handler, contextID));
    }
  }

  /**
   * Inform the pool that the connection has been closed externally.
   */
  public synchronized void connectionClosed() {
    if (connectionCount.decrementAndGet() < maxPoolSize) {
      //Now the connection count has come down, maybe there is another waiter that can
      //create a new connection
      Waiter waiter = waiters.poll();
      if (waiter != null) {
        getConnection(waiter.handler, waiter.contextID);
      }
    }
  }

  /**
   * Return a connection to the pool so it can be used by others.
   */
  public synchronized void returnConnection(final T conn) {

    //Return it to the pool
    final Waiter waiter = waiters.poll();

    if (waiter != null) {
      NodexInternal.instance.executeOnContext(waiter.contextID, new Runnable() {
        public void run() {
          NodexInternal.instance.setContextID(waiter.contextID);
          waiter.handler.handle(conn);
        }
      });
    } else {
      available.add(conn);
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
