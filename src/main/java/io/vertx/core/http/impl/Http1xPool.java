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

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http1xPool implements ConnectionManager.Pool<ClientConnection> {

  // Pools must locks on the queue object to keep a single lock
  private final Object lock;
  private final boolean ssl;
  private final String host;
  private final int port;
  private final HttpVersion version;
  private final Set<ClientConnection> allConnections = new HashSet<>();
  private final Queue<ClientConnection> availableConnections = new ArrayDeque<>();
  private final int maxSockets;

  public Http1xPool(HttpClientOptions options,
                    ConnectionManager.ConnQueue lock,
                    HttpVersion version,
                    int maxSockets,
                    String host,
                    int port) {
    this.lock = lock;
    this.version = version;
    this.ssl = options.isSsl();
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
  public boolean canCreateStream(int connCount) {
    return connCount < maxSockets;
  }

  @Override
  public ClientConnection pollConnection() {
    ClientConnection conn;
    synchronized (lock) {
      while ((conn = availableConnections.poll()) != null && !conn.isValid()) {
      }
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
    synchronized (lock) {
      availableConnections.add(conn);
    }
  }

  public void closeAllConnections() {
    Set<ClientConnection> copy;
    synchronized (lock) {
      copy = new HashSet<>(allConnections);
      allConnections.clear();
    }
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
    synchronized (lock) {
      allConnections.remove(conn);
      availableConnections.remove(conn);
    }
  }
}
