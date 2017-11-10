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
import io.vertx.core.impl.ContextImpl;

import java.util.*;

class HttpClientPool implements ConnectionManager.Pool<HttpClientConnection> {

  private ConnectionManager.Pool<? extends HttpClientConnection> current;
  private final HttpClientOptions options;
  private final String host;
  private final int port;
  private final boolean ssl;
  private HttpVersion version;

  HttpClientPool(HttpVersion version, HttpClientOptions options, String host, int port) {
    this.version = version;
    this.options = options;
    this.host = host;
    this.port = port;
    this.ssl = options.isSsl();
    if (version == HttpVersion.HTTP_2) {
      current =  new Http2();
    } else {
      current = new Http1x();
    }
  }

  String host() {
    return host;
  }

  int port() {
    return port;
  }

  boolean ssl() {
    return ssl;
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
  public boolean canCreateStream(int connCount) {
    return current.canCreateStream(connCount);
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
  public void closeAllConnections() {
    current.closeAllConnections();
  }

  @Override
  public void initConnection(HttpClientConnection conn) {
    if (conn instanceof ClientConnection && current instanceof Http2) {
      fallbackToHttp1x(((ClientConnection) conn).version());
    }
    ((ConnectionManager.Pool<HttpClientConnection>) current).initConnection(conn);
  }

  @Override
  public void recycleConnection(HttpClientConnection conn) {
    ((ConnectionManager.Pool<HttpClientConnection>) current).recycleConnection(conn);
  }

  @Override
  public HttpClientStream createStream(HttpClientConnection conn) throws Exception {
    return ((ConnectionManager.Pool<HttpClientConnection>) current).createStream(conn);
  }

  @Override
  public void evictConnection(HttpClientConnection conn) {
    ((ConnectionManager.Pool<HttpClientConnection>) current).evictConnection(conn);
  }

  class Http2 implements ConnectionManager.Pool<Http2ClientConnection> {

    // Pools must locks on the queue object to keep a single lock
    private final Set<Http2ClientConnection> allConnections = new HashSet<>();
    private final int maxConcurrency;
    private final int maxSockets;

    Http2() {
      this.maxConcurrency = options.getHttp2MultiplexingLimit() < 1 ? Integer.MAX_VALUE : options.getHttp2MultiplexingLimit();
      this.maxSockets = options.getHttp2MaxPoolSize();
    }

    @Override
    public boolean canCreateConnection(int connCount) {
      // We create at most one connection concurrently when all others when
      // all others are busy
      return connCount == allConnections.size() && connCount < maxSockets;
    }

    @Override
    public boolean canCreateStream(int connCount) {
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
    public HttpClientStream createStream(Http2ClientConnection conn) throws Exception {
      return conn.createStream();
    }

    @Override
    public void closeAllConnections() {
      List<Http2ClientConnection> toClose = toClose = new ArrayList<>(allConnections);
      // Close outside sync block to avoid deadlock
      toClose.forEach(Http2ConnectionBase::close);
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

  private class Http1x implements ConnectionManager.Pool<ClientConnection> {

    private final Set<ClientConnection> allConnections = new HashSet<>();
    private final Queue<ClientConnection> availableConnections = new ArrayDeque<>();
    private final int maxSockets;

    Http1x() {
      this.maxSockets = options.getMaxPoolSize();
    }

    @Override
    public boolean canCreateStream(int connCount) {
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
    public HttpClientStream createStream(ClientConnection conn) {
      return conn;
    }

    @Override
    public void initConnection(ClientConnection conn) {
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
