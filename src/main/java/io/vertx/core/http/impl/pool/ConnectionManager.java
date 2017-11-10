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

package io.vertx.core.http.impl.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ConnectionManager<C> {

  public static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

  private final VertxInternal vertx;
  private final int maxWaitQueueSize;
  private final HttpClientMetrics metrics; // Shall be removed later combining the PoolMetrics with HttpClientMetrics
  private final ConnectionProvider<C> connector;
  private final Function<SocketAddress, ConnectionPool<C>> poolFactory;
  private final Map<Channel, C> connectionMap = new ConcurrentHashMap<>();
  private final Map<ConnectionKey, ConnQueue> queueMap = new ConcurrentHashMap<>();

  public ConnectionManager(VertxInternal vertx,
                    HttpClientMetrics metrics,
                    ConnectionProvider<C> connector,
                    Function<SocketAddress,
                      ConnectionPool<C>> poolFactory,
                    int maxWaitQueueSize) {
    this.vertx = vertx;
    this.maxWaitQueueSize = maxWaitQueueSize;
    this.metrics = metrics;
    this.connector = connector;
    this.poolFactory = poolFactory;
  }

  static final class ConnectionKey {

    private final boolean ssl;
    private final int port;
    private final String host;

    ConnectionKey(boolean ssl, int port, String host) {
      this.ssl = ssl;
      this.host = host;
      this.port = port;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ConnectionKey that = (ConnectionKey) o;

      if (ssl != that.ssl) return false;
      if (port != that.port) return false;
      if (!Objects.equals(host, that.host)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = ssl ? 1 : 0;
      result = 31 * result + (host != null ? host.hashCode() : 0);
      result = 31 * result + port;
      return result;
    }
  }

  private ConnQueue getConnQueue(String peerHost, boolean ssl, int port, String host) {
    ConnectionKey key = new ConnectionKey(ssl, port, peerHost);
    return queueMap.computeIfAbsent(key, targetAddress -> {
      ConnectionPool<C> pool =  poolFactory.apply(SocketAddress.inetSocketAddress(port, host));
      return new ConnQueue( connector, peerHost, host, port, ssl, key, pool);
    });
  }

  public void getConnection(String peerHost, boolean ssl, int port, String host, Waiter<C> waiter) {
    ConnQueue connQueue = getConnQueue(peerHost, ssl, port, host);
    connQueue.getConnection(waiter);
  }

  public void close() {
    for (ConnQueue queue: queueMap.values()) {
      queue.closeAllConnections();
    }
    queueMap.clear();
    for (C conn : connectionMap.values()) {
      connector.close(conn);
    }
  }

  /**
   * The connection queue delegates to the connection pool, the pooling strategy.
   */
  class ConnQueue implements ConnectionListener<C> {

    private final String peerHost;
    private final boolean ssl;
    private final int port;
    private final String host;
    private final ConnectionKey key;
    private final Queue<Waiter<C>> waiters = new ArrayDeque<>();
    private final ConnectionPool<C> pool;
    private int connCount;
    private final ConnectionProvider<C> connector;
    final Object metric;

    ConnQueue(ConnectionProvider<C> connector,
              String peerHost,
              String host,
              int port,
              boolean ssl,
              ConnectionKey key,
              ConnectionPool<C> pool) {
      this.key = key;
      this.host = host;
      this.port = port;
      this.ssl = ssl;
      this.peerHost = peerHost;
      this.connector = connector;
      this.pool = pool;
      this.metric = metrics != null ? metrics.createEndpoint(host, port, pool.maxSize()) : null;
    }

    private void closeAllConnections() {
      pool.closeAllConnections();
    }

    private synchronized void getConnection(Waiter<C> waiter) {
      // Enqueue
      if (maxWaitQueueSize < 0 || waiters.size() < maxWaitQueueSize || pool.canBorrow(connCount)) {
        if (metrics != null) {
          waiter.metric = metrics.enqueueRequest(metric);
        }
        waiters.add(waiter);
        checkPending();
      } else {
        waiter.handleFailure(new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + maxWaitQueueSize));
      }
    }

    private synchronized void checkPending() {
      while (true) {
        Waiter<C> waiter = waiters.peek();
        if (waiter == null) {
          break;
        }
        if (metric != null) {
          metrics.dequeueRequest(metric, waiter.metric);
        }
        if (waiter.isCancelled()) {
          waiters.poll();
        } else {
          C conn = pool.pollConnection();
          if (conn != null) {
            waiters.poll();
            deliverInternal(conn, waiter);
          } else if (pool.canCreateConnection(connCount)) {
            waiters.poll();
            createConnection(waiter);
          } else {
            break;
          }
        }
      }
    }

    private void createConnection(Waiter<C> waiter) {
      connCount++;
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(waiter.context.nettyEventLoop());
      bootstrap.channel(vertx.transport().channelType(false));
      connector.connect(this, metric, bootstrap, waiter.context, peerHost, ssl, host, port, ar -> {
        if (ar.succeeded()) {
          initConnection(waiter, ar.result());
        } else {

          // If no specific exception handler is provided, fall back to the HttpClient's exception handler.
          // If that doesn't exist just log it
          // Handler<Throwable> exHandler =
          //  waiter == null ? log::error : waiter::handleFailure;

          closeConnection();

          waiter.context.executeFromIO(() -> {
            waiter.handleFailure(ar.cause());
          });
        }
      });
    }

    @Override
    public synchronized void onRecycle(C conn) {
      pool.recycleConnection(conn);
      checkPending();
    }

    @Override
    public synchronized void onClose(C conn, Channel channel) {
      connectionMap.remove(channel);
      pool.evictConnection(conn);
      closeConnection();
    }

    private synchronized void initConnection(Waiter<C> waiter, C conn) {
      connectionMap.put(connector.channel(conn), conn);
      pool.initConnection(conn);
      pool.getContext(conn).executeFromIO(() -> {
        waiter.initConnection(conn);
      });
      if (waiter.isCancelled()) {
        pool.recycleConnection(conn);
      } else {
        deliverInternal(conn, waiter);
      }
      checkPending();
    }

    private void deliverInternal(C conn, Waiter<C> waiter) {
      ContextImpl ctx = pool.getContext(conn);
      if (ctx.nettyEventLoop().inEventLoop()) {
        ctx.executeFromIO(() -> {
          try {
            waiter.handleConnection(conn);
          } catch (Exception e) {
            getConnection(waiter);
          }
        });
      } else {
        ctx.runOnContext(v -> {
          if (pool.isValid(conn)) {
            deliverInternal(conn, waiter);
          } else {
            getConnection(waiter);
          }
        });
      }
    }

    // Called if the connection is actually closed OR the connection attempt failed
    synchronized void closeConnection() {
      connCount--;
      checkPending();
      if (connCount == 0) {
        // No waiters and no connections - remove the ConnQueue
        queueMap.remove(key);
        if (metrics != null) {
          metrics.closeEndpoint(host, port, metric);
        }
      }
    }
  }
}
