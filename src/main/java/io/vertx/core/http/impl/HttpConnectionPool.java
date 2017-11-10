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

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.pool.ConnectionManager;
import io.vertx.core.http.impl.pool.ConnectionPool;
import io.vertx.core.impl.ContextImpl;

import java.util.*;

/**
 * The logic for the connection pool for HTTP:
 *
 * - HTTP/1.x pools several connections
 * - HTTP/2 uses can multiplex on a single connections but can handle several connections
 * <p/>
 * When this pool is initialized with an HTTP/2 pool, this pool can be changed to an HTTP/1/1
 * pool if the remote server does not support HTTP/2 or after ALPN negotiation.
 * <p/>
 * In this case further connections will be HTTP/1.1 connections, until the pool is closed.
 */
class HttpConnectionPool implements ConnectionPool<HttpClientConnection> {

  private ConnectionPool<? extends HttpClientConnection> current;
  private final HttpClientOptions options;
  private HttpVersion version;

  HttpConnectionPool(HttpVersion version, HttpClientOptions options) {
    this.version = version;
    this.options = options;
    if (version == HttpVersion.HTTP_2) {
      current =  new Http2();
    } else {
      current = new Http1x();
    }
  }

  private void fallbackToHttp1x(HttpVersion version) {
    this.current = new Http1x();
    this.version = version;
  }

  public HttpVersion version() {
    return version;
  }

  @Override
  public boolean isValid(HttpClientConnection conn) {
    return conn.isValid();
  }

  @Override
  public ContextImpl getContext(HttpClientConnection conn) {
    return conn.getContext();
  }

  @Override
  public boolean canBorrow(int connCount) {
    return current.canBorrow(connCount);
  }

  @Override
  public HttpClientConnection pollConnection() {
    return current.pollConnection();
  }

  @Override
  public boolean canCreateConnection(int connCount) {
    return current.canCreateConnection(connCount);
  }

  @Override
  public int maxSize() {
    return current.maxSize();
  }

  @Override
  public void initConnection(HttpClientConnection conn) {
    if (conn instanceof ClientConnection && current instanceof Http2) {
      fallbackToHttp1x(((ClientConnection) conn).version());
    }
    ((ConnectionPool<HttpClientConnection>) current).initConnection(conn);
  }

  @Override
  public void recycleConnection(HttpClientConnection conn) {
    ((ConnectionPool<HttpClientConnection>) current).recycleConnection(conn);
  }

  @Override
  public void evictConnection(HttpClientConnection conn) {
    ((ConnectionPool<HttpClientConnection>) current).evictConnection(conn);
  }

  class Http2 implements ConnectionPool<Http2ClientConnection> {

    // Pools must locks on the queue object to keep a single lock
    private final Set<Http2ClientConnection> allConnections = new HashSet<>();
    private final int maxConcurrency;
    private final int maxSockets;

    Http2() {
      this.maxConcurrency = options.getHttp2MultiplexingLimit() < 1 ? Integer.MAX_VALUE : options.getHttp2MultiplexingLimit();
      this.maxSockets = options.getHttp2MaxPoolSize();
    }

    @Override
    public int maxSize() {
      return maxSockets;
    }

    @Override
    public boolean canCreateConnection(int connCount) {
      // We create at most one connection concurrently when all others when
      // all others are busy
      return connCount == allConnections.size() && connCount < maxSockets;
    }

    @Override
    public boolean canBorrow(int connCount) {
      for (Http2ClientConnection conn : allConnections) {
        if (canReserveStream(conn)) {
          return true;
        }
      }
      return canCreateConnection(connCount);
    }

    @Override
    public Http2ClientConnection pollConnection() {
      for (Http2ClientConnection conn : allConnections) {
        // Julien : check conn is valid ?
        if (canReserveStream(conn)) {
          conn.streamCount++;
          return conn;
        }
      }
      return null;
    }

    private boolean canReserveStream(Http2ClientConnection handler) {
      int maxConcurrentStreams = Math.min(handler.handler.connection().local().maxActiveStreams(), maxConcurrency);
      return handler.streamCount < maxConcurrentStreams;
    }

    @Override
    public void evictConnection(Http2ClientConnection conn) {
      allConnections.remove(conn);
    }

    @Override
    public void initConnection(Http2ClientConnection conn) {
      allConnections.add(conn);
    }

    @Override
    public void recycleConnection(Http2ClientConnection conn) {
      conn.streamCount--;
    }

    @Override
    public boolean isValid(Http2ClientConnection conn) {
      return conn.isValid();
    }

    @Override
    public ContextImpl getContext(Http2ClientConnection conn) {
      return conn.getContext();
    }
  }

  private class Http1x implements ConnectionPool<ClientConnection> {

    private final Set<ClientConnection> allConnections = new HashSet<>();
    private final Queue<ClientConnection> availableConnections = new ArrayDeque<>();
    private final int maxSockets;

    Http1x() {
      this.maxSockets = options.getMaxPoolSize();
    }

    @Override
    public int maxSize() {
      return maxSockets;
    }

    @Override
    public boolean canBorrow(int connCount) {
      return connCount < maxSockets;
    }

    @Override
    public ClientConnection pollConnection() {
      ClientConnection conn;
      while ((conn = availableConnections.poll()) != null && !conn.isValid()) {
      }
      return conn;
    }

    @Override
    public boolean canCreateConnection(int connCount) {
      return connCount < maxSockets;
    }

    @Override
    public void initConnection(ClientConnection conn) {
      allConnections.add(conn);
    }

    @Override
    public void recycleConnection(ClientConnection conn) {
      availableConnections.add(conn);
    }

    public void closeAllConnections() {
      Set<ClientConnection> copy = new HashSet<>(allConnections);
      allConnections.clear();
      // Close outside sync block to avoid potential deadlock
      for (ClientConnection conn : copy) {
        try {
          conn.close();
        } catch (Throwable t) {
          ConnectionManager.log.error("Failed to close connection", t);
        }
      }
    }

    @Override
    public void evictConnection(ClientConnection conn) {
      allConnections.remove(conn);
      availableConnections.remove(conn);
    }

    @Override
    public boolean isValid(ClientConnection conn) {
      return conn.isValid();
    }

    @Override
    public ContextImpl getContext(ClientConnection conn) {
      return conn.getContext();
    }
  }
}
