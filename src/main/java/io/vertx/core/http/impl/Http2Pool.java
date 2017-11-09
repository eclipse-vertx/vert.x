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

import io.vertx.core.http.HttpVersion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Pool implements ConnectionManager.Pool<Http2ClientConnection> {

  // Pools must locks on the queue object to keep a single lock
  private final Object lock;
  private final Set<Http2ClientConnection> allConnections = new HashSet<>();
  private final int maxConcurrency;
  private final int maxSockets;

  public Http2Pool(Object lock, int maxConcurrency, int maxSize) {
    this.lock = lock;
    this.maxConcurrency = maxConcurrency;
    this.maxSockets = maxSize;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_2;
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
    synchronized (lock) {
      allConnections.remove(conn);
    }
  }

  @Override
  public void initConnection(Http2ClientConnection conn) {
    synchronized (lock) {
      allConnections.add(conn);
    }
  }

  @Override
  public void recycleConnection(Http2ClientConnection conn) {
    synchronized (lock) {
      conn.streamCount--;
    }
  }

  @Override
  public HttpClientStream createStream(Http2ClientConnection conn) throws Exception {
    return conn.createStream();
  }

  @Override
  public void closeAllConnections() {
    List<Http2ClientConnection> toClose;
    synchronized (lock) {
      toClose = new ArrayList<>(allConnections);
    }
    // Close outside sync block to avoid deadlock
    toClose.forEach(Http2ConnectionBase::close);
  }
}
