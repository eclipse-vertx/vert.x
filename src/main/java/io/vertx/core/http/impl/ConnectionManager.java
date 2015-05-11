/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.http.impl;

import io.vertx.core.Handler;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class ConnectionManager {

  private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

  private final int maxSockets;
  private final boolean keepAlive;
  private final boolean pipelining;
  private final Map<TargetAddress, ConnQueue> connQueues = new ConcurrentHashMap<>();

  ConnectionManager(int maxSockets, boolean keepAlive, boolean pipelining) {
    this.maxSockets = maxSockets;
    this.keepAlive = keepAlive;
    this.pipelining = pipelining;
  }

  public void getConnection(int port, String host, Handler<ClientConnection> handler, Handler<Throwable> connectionExceptionHandler, ContextImpl context) {
    if (!keepAlive && pipelining) {
      connectionExceptionHandler.handle(new IllegalStateException("Cannot have pipelining with no keep alive"));
    } else {
      TargetAddress address = new TargetAddress(host, port);
      ConnQueue connQueue = connQueues.get(address);
      if (connQueue == null) {
        connQueue = new ConnQueue(address);
        ConnQueue prev = connQueues.putIfAbsent(address, connQueue);
        if (prev != null) {
          connQueue = prev;
        }
      }
      connQueue.getConnection(handler, connectionExceptionHandler, context);
    }
  }

  protected abstract void connect(String host, int port, Handler<ClientConnection> connectHandler, Handler<Throwable> connectErrorHandler, ContextImpl context,
                                  ConnectionLifeCycleListener listener);

  public void close() {
    for (ConnQueue queue: connQueues.values()) {
      queue.closeAllConnections();
    }
    connQueues.clear();
  }

  private class ConnQueue implements ConnectionLifeCycleListener {

    private final TargetAddress address;
    private final Queue<Waiter> waiters = new ArrayDeque<>();
    private final Set<ClientConnection> allConnections = new HashSet<>();
    private int connCount;

    ConnQueue(TargetAddress address) {
      this.address = address;
    }

    public synchronized void getConnection(Handler<ClientConnection> handler, Handler<Throwable> connectionExceptionHandler, ContextImpl context) {
      if (connCount == maxSockets) {
        // Wait in queue
        waiters.add(new Waiter(handler, connectionExceptionHandler, context));
      } else {
        // Create a new connection
        createNewConnection(handler, connectionExceptionHandler, context);
      }
    }

    // Called when the request has ended
    public synchronized void requestEnded(ClientConnection conn) {
      if (pipelining) {
        // Maybe the connection can be reused
        Waiter waiter = waiters.poll();
        if (waiter != null) {
          conn.getContext().executeFromIO(() -> waiter.handler.handle(conn));
        }
      }
    }

    // Called when the response has ended
    public synchronized void responseEnded(ClientConnection conn) {
      if (pipelining) {
        // if no outstanding responses on connection and nothing waiting then close it
        if (conn.getOutstandingRequestCount() == 0 && waiters.isEmpty()) {
          conn.close();
        }
      } else if (keepAlive) {
        // Maybe the connection can be reused
        checkReuseConnection(conn);
      } else {
        // Close it now
        conn.close();
      }
    }

    void closeAllConnections() {
      Set<ClientConnection> copy;
      synchronized (this) {
        copy = new HashSet<>(allConnections);
        allConnections.clear();
      }
      // Close outside sync block to avoid deadlock
      for (ClientConnection conn: copy) {
        try {
          conn.close();
        } catch (Throwable t) {
          log.error("Failed to close connection", t);
        }
      }
    }

    private void checkReuseConnection(ClientConnection conn) {
      Waiter waiter = waiters.poll();
      if (waiter != null) {
        conn.getContext().executeFromIO(() -> waiter.handler.handle(conn));
      } else {
        // Close it - we don't keep connections hanging around - even keep alive ones if there are
        // no pending requests
        conn.close();
      }
    }

    private void createNewConnection(Handler<ClientConnection> handler, Handler<Throwable> connectionExceptionHandler, ContextImpl context) {
      connCount++;
      connect(address.host, address.port, conn -> {
        allConnections.add(conn);
        handler.handle(conn);
      }, connectionExceptionHandler, context, this);
    }

    // Called if the connection is actually closed, OR the connection attempt failed - in the latter case
    // conn will be null
    public synchronized void connectionClosed(ClientConnection conn) {
      connCount--;
      if (conn != null) {
        allConnections.remove(conn);
      }
      Waiter waiter = waiters.poll();
      if (waiter != null) {
        // There's a waiter - so it can have a new connection
        createNewConnection(waiter.handler, waiter.connectionExceptionHandler, waiter.context);
      } else if (connCount == 0) {
        // No waiters and no connections - remove the ConnQueue
        connQueues.remove(address);
      }
    }
  }

  private static class TargetAddress {
    final String host;
    final int port;

    private TargetAddress(String host, int port) {
      this.host = host;
      this.port = port;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TargetAddress that = (TargetAddress) o;
      if (port != that.port) return false;
      if (host != null ? !host.equals(that.host) : that.host != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = host != null ? host.hashCode() : 0;
      result = 31 * result + port;
      return result;
    }
  }

  private static class Waiter {
    final Handler<ClientConnection> handler;
    final Handler<Throwable> connectionExceptionHandler;
    final ContextImpl context;

    private Waiter(Handler<ClientConnection> handler, Handler<Throwable> connectionExceptionHandler, ContextImpl context) {
      this.handler = handler;
      this.connectionExceptionHandler = connectionExceptionHandler;
      this.context = context;
    }
  }


}
