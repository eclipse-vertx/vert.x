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

import io.netty.channel.Channel;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.impl.ContextImpl;
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

  private final int maxWaitQueueSize;
  private final HttpClientMetrics metrics; // Shall be removed later combining the PoolMetrics with HttpClientMetrics
  private final ConnectionProvider<C> connector;
  private final Function<SocketAddress, ConnectionPool<C>> poolFactory;
  private final Map<Channel, C> connectionMap = new ConcurrentHashMap<>();
  private final Map<ConnectionKey, Endpoint> endpointMap = new ConcurrentHashMap<>();

  public ConnectionManager(HttpClientMetrics metrics,
                           ConnectionProvider<C> connector,
                           Function<SocketAddress, ConnectionPool<C>> poolFactory,
                           int maxWaitQueueSize) {
    this.maxWaitQueueSize = maxWaitQueueSize;
    this.metrics = metrics;
    this.connector = connector;
    this.poolFactory = poolFactory;
  }

  private static final class ConnectionKey {

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

  private Endpoint getConnQueue(String peerHost, boolean ssl, int port, String host) {
    ConnectionKey key = new ConnectionKey(ssl, port, peerHost);
    return endpointMap.computeIfAbsent(key, targetAddress -> {
      ConnectionPool<C> pool =  poolFactory.apply(SocketAddress.inetSocketAddress(port, host));
      return new Endpoint( connector, peerHost, host, port, ssl, key, pool);
    });
  }

  public void getConnection(String peerHost, boolean ssl, int port, String host, Waiter<C> waiter) {
    Endpoint connQueue = getConnQueue(peerHost, ssl, port, host);
    connQueue.getConnection(waiter);
  }

  public void close() {
    endpointMap.clear();
    for (C conn : connectionMap.values()) {
      connector.close(conn);
    }
  }

  /**
   * The endpoint is a queue of waiters and it delegates to the connection pool, the pooling strategy.
   *
   * An endpoint is synchronized and should be executed only from the event loop, the underlying pool
   * relies and the synchronization performed by the endpoint.
   */
  private class Endpoint implements ConnectionListener<C> {

    private final String peerHost;
    private final boolean ssl;
    private final int port;
    private final String host;
    private final ConnectionKey key;
    private final ConnectionPool<C> pool;
    private final ConnectionProvider<C> connector;
    private final Object metric;

    private final Queue<Waiter<C>> waiters = new ArrayDeque<>();
    private int connCount;

    private Endpoint(ConnectionProvider<C> connector,
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

    private synchronized void getConnection(Waiter<C> waiter) {
      // Enqueue
      if (maxWaitQueueSize < 0 || waiters.size() < maxWaitQueueSize || pool.canBorrow(connCount)) {
        if (metrics != null) {
          waiter.metric = metrics.enqueueRequest(metric);
        }
        waiters.add(waiter);
        checkPending();
      } else {
        waiter.handleFailure(null, new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + maxWaitQueueSize));
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
        C conn = pool.pollConnection();
        if (conn != null) {
          waiters.poll();
          ContextImpl ctx = pool.getContext(conn);
          ctx.nettyEventLoop().execute(() -> {
            if (pool.isValid(conn)) {
              boolean handled = deliverInternal(conn, waiter);
              if (!handled) {
                pool.recycleConnection(conn);
                checkPending();
              }
            } else {
              closed(conn);
              getConnection(waiter);
            }
          });
        } else if (pool.canCreateConnection(connCount)) {
          waiters.poll();
          createConnection(waiter);
        } else {
          break;
        }
      }
    }

    private void createConnection(Waiter<C> waiter) {
      connCount++;
      connector.connect(this, metric, waiter.context, peerHost, ssl, host, port, ar -> {
        if (ar.succeeded()) {
          initConnection(waiter, ar.result());
        } else {

          // If no specific exception handler is provided, fall back to the HttpClient's exception handler.
          // If that doesn't exist just log it
          // Handler<Throwable> exHandler =
          //  waiter == null ? log::error : waiter::handleFailure;

          connectionClosed();

          waiter.handleFailure(waiter.context, ar.cause());
        }
      });
    }

    @Override
    public void concurrencyChanged(C conn) {
      checkPending();
    }

    @Override
    public void recycle(C conn) {
      pool.recycleConnection(conn);
      checkPending();
    }

    @Override
    public synchronized void closed(C conn) {
      Channel channel = connector.channel(conn);
      connectionMap.remove(channel);
      pool.evictConnection(conn);
      connectionClosed();
    }

    private synchronized void initConnection(Waiter<C> waiter, C conn) {
      connectionMap.put(connector.channel(conn), conn);
      boolean ok = pool.initConnection(conn);
      waiter.initConnection(conn);
      if (ok) {
        boolean handled = deliverInternal(conn, waiter);
        if (!handled) {
          pool.recycleConnection(conn);
        }
        checkPending();
      } else {
        getConnection(waiter);
      }
    }

    private boolean deliverInternal(C conn, Waiter<C> waiter) {
      try {
        return waiter.handleConnection(conn);
      } catch (Exception e) {
        e.printStackTrace();
        return true;
      }
    }

    // Called if the connection is actually closed OR the connection attempt failed
    synchronized void connectionClosed() {
      connCount--;
      checkPending();

      // CHECK WAITERS

      if (connCount == 0) {
        // No waiters and no connections - remove the ConnQueue
        endpointMap.remove(key);
        if (metrics != null) {
          metrics.closeEndpoint(host, port, metric);
        }
        pool.close();
      }
    }
  }
}
