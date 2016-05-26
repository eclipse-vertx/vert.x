/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.vertx.core.Context;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http1xPool implements ConnectionManager.Pool<ClientConnection> {

  // Pools must locks on the queue object to keep a single lock
  private final ConnectionManager.ConnQueue queue;
  private final HttpClientImpl client;
  private final HttpClientMetrics metrics;
  private final Map<Channel, HttpClientConnection> connectionMap;
  private final boolean pipelining;
  private final boolean keepAlive;
  private final boolean ssl;
  private final HttpVersion version;
  private final Set<ClientConnection> allConnections = new HashSet<>();
  private final Queue<ClientConnection> availableConnections = new ArrayDeque<>();

  public Http1xPool(HttpClientImpl client, HttpClientMetrics metrics, HttpClientOptions options, ConnectionManager.ConnQueue queue, Map<Channel, HttpClientConnection> connectionMap, HttpVersion version) {
    this.queue = queue;
    this.version = version;
    this.client = client;
    this.metrics = metrics;
    this.pipelining = options.isPipelining();
    this.keepAlive = options.isKeepAlive();
    this.ssl = options.isSsl();
    this.connectionMap = connectionMap;
  }

  @Override
  public HttpVersion version() {
    // Correct this
    return version;
  }

  @Override
  public ClientConnection pollConnection() {
    return availableConnections.poll();
  }

  @Override
  public HttpClientStream createStream(ClientConnection conn) {
    return conn;
  }

  // Called when the request has ended
  public void recycle(ClientConnection conn) {
    synchronized (queue) {
      if (pipelining) {
        doRecycle(conn);
      }
    }
  }

  // Called when the response has ended
  public synchronized void responseEnded(ClientConnection conn, boolean close) {
    synchronized (queue) {
      if ((pipelining || keepAlive) && !close) {
        if (conn.getCurrentRequest() == null) {
          doRecycle(conn);
        }
      } else {
        // Close it now
        conn.close();
      }
    }
  }

  private void doRecycle(ClientConnection conn) {
    Waiter waiter = queue.getNextWaiter();
    if (waiter != null) {
      Context context = waiter.context;
      if (context == null) {
        context = conn.getContext();
      }
      context.runOnContext(v -> queue.deliverStream(conn, waiter));
    } else if (conn.getOutstandingRequestCount() == 0) {
      // Return to set of available from here to not return it several times
      availableConnections.add(conn);
    }
  }

  void createConn(HttpVersion version, ContextImpl context, int port, String host, Channel ch, Waiter waiter) {
    ClientConnection conn = new ClientConnection(version, client, ch,
        ssl, host, port, context, this, metrics);
    conn.exceptionHandler(waiter::handleFailure);
    ClientHandler handler = ch.pipeline().get(ClientHandler.class);
    handler.conn = conn;
    synchronized (queue) {
      allConnections.add(conn);
    }
    connectionMap.put(ch, conn);
    waiter.handleConnection(conn);
    queue.deliverStream(conn, waiter);
  }

  // Called if the connection is actually closed, OR the connection attempt failed - in the latter case
  // conn will be null
  public synchronized void connectionClosed(ClientConnection conn) {
    synchronized (queue) {
      allConnections.remove(conn);
      availableConnections.remove(conn);
      queue.connectionClosed();
    }
  }

  public void closeAllConnections() {
    Set<ClientConnection> copy;
    synchronized (this) {
      copy = new HashSet<>(allConnections);
      allConnections.clear();
    }
    // Close outside sync block to avoid deadlock
    for (ClientConnection conn : copy) {
      try {
        conn.close();
      } catch (Throwable t) {
        ConnectionManager.log.error("Failed to close connection", t);
      }
    }
  }

  void removeChannel(Channel channel) {
    connectionMap.remove(channel);
  }
}
