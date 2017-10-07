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
  private final int pipeliningLimit;
  private final boolean ssl;
  private final String host;
  private final int port;
  private final HttpVersion version;
  private final Set<ClientConnection> allConnections = new HashSet<>();
  private final Queue<ClientConnection> availableConnections = new ArrayDeque<>();
  private final int maxSockets;

  public Http1xPool(HttpClientImpl client,
                    HttpClientMetrics metrics,
                    HttpClientOptions options,
                    ConnectionManager.ConnQueue queue,
                    Map<Channel, HttpClientConnection> connectionMap,
                    HttpVersion version,
                    int maxSockets,
                    String host,
                    int port) {
    this.queue = queue;
    this.version = version;
    this.client = client;
    this.metrics = metrics;
    this.pipelining = options.isPipelining();
    this.keepAlive = options.isKeepAlive();
    this.pipeliningLimit = options.getPipeliningLimit();
    this.ssl = options.isSsl();
    this.connectionMap = connectionMap;
    this.maxSockets = maxSockets;
    this.host = host;
    this.port = port;
  }

  boolean ssl() {
    return ssl;
  }

  String host() {
    return host;
  }

  int port() {
    return port;
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
  public boolean canCreateConnection(int connCount) {
    return connCount < maxSockets;
  }

  @Override
  public HttpClientStream createStream(ClientConnection conn) {
    return conn;
  }

  public void recycle(ClientConnection conn) {
    synchronized (queue) {
      Waiter waiter = queue.getNextWaiter();
      if (waiter != null) {
        queue.deliverStream(conn, waiter);
      } else if (conn.getOutstandingRequestCount() == 0) {
        // Return to set of available from here to not return it several times
        availableConnections.add(conn);
      }
    }
  }

  void requestEnded(ClientConnection conn) {
    ContextImpl context = conn.getContext();
    context.runOnContext(v -> {
      if (pipelining && conn.getOutstandingRequestCount() < pipeliningLimit) {
        recycle(conn);
      }
    });
  }

  void responseEnded(ClientConnection conn, boolean close) {
    if (!keepAlive || close) {
      conn.close();
    } else {
      ContextImpl ctx = conn.getContext();
      ctx.runOnContext(v -> {
        if (conn.getCurrentRequest() == null) {
          recycle(conn);
        }
      });
    }
  }

  void createConn(ContextImpl context, Channel ch, Waiter waiter) {
    ClientHandler handler = new ClientHandler(
      context,
      this,
      client,
      queue.metric,
      metrics);
    handler.addHandler(conn -> {
      synchronized (queue) {
        allConnections.add(conn);
      }
      connectionMap.put(ch, conn);
    });
    handler.removeHandler(this::connectionClosed);
    ch.pipeline().addLast("handler", handler);
    ClientConnection conn = handler.getConnection();
    context.executeFromIO(() -> {
      waiter.handleConnection(conn);
      queue.deliverStream(conn, waiter);
    });
  }

  // Called if the connection is actually closed, OR the connection attempt failed - in the latter case
  // conn will be null
  // The connection has been closed - tell the pool about it, this allows the pool to create more
  // connections. Note the pool doesn't actually remove the connection, when the next person to get a connection
  // gets the closed on, they will check if it's closed and if so get another one.
  private synchronized void connectionClosed(ClientConnection conn) {
    synchronized (queue) {
      connectionMap.remove(conn.channel());
      allConnections.remove(conn);
      availableConnections.remove(conn);
      queue.connectionClosed();
    }
    if (metrics != null) {
      metrics.endpointDisconnected(queue.metric, conn.metric());
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
