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
import io.vertx.core.Handler;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.*;

/**
 * The endpoint is a queue of waiters and it delegates to the connection pool, the pooling strategy.
 *
 * An endpoint is synchronized and should be executed only from the event loop, the underlying pool
 * relies and the synchronization performed by the endpoint.
 */
public class Pool<C> {

  private static final Logger log = LoggerFactory.getLogger(Pool.class);

  private final HttpClientMetrics metrics;
  private final String peerHost;
  private final boolean ssl;
  private final int port;
  private final String host;
  private final ConnectionProvider<C> connector;
  private final Object metric;
  private final long maxWeight;
  private final int queueMaxSize;

  private final Queue<Waiter<C>> waiters = new ArrayDeque<>();
  private final Set<ConnectionHolder<C>> all;
  private final Deque<ConnectionHolder<C>> available;
  private boolean closed;
  private long capacity;
  private long weight;
  private final Handler<Void> poolClosed;
  private final Handler<ConnectionHolder<C>> connectionAdded;
  private final Handler<ConnectionHolder<C>> connectionRemoved;

  public Pool(ConnectionProvider<C> connector,
              HttpClientMetrics metrics,
              int queueMaxSize,
              String peerHost,
              String host,
              int port,
              boolean ssl,
              long maxWeight,
              Handler<Void> poolClosed,
              Handler<ConnectionHolder<C>> connectionAdded,
              Handler<ConnectionHolder<C>> connectionRemoved) {
    this.maxWeight = maxWeight;
    this.host = host;
    this.port = port;
    this.ssl = ssl;
    this.peerHost = peerHost;
    this.connector = connector;
    this.queueMaxSize = queueMaxSize;
    this.metrics = metrics;
    this.metric = metrics != null ? metrics.createEndpoint(host, port, 10 /* fixme */) : null;
    this.poolClosed = poolClosed;
    this.all = new HashSet<>();
    this.available = new ArrayDeque<>();
    this.connectionAdded = connectionAdded;
    this.connectionRemoved = connectionRemoved;
  }

  public synchronized boolean getConnection(Waiter<C> waiter) {
    if (closed) {
      return false;
    }
    // Enqueue
    if (capacity > 0 || weight < maxWeight || (queueMaxSize < 0 || waiters.size() < queueMaxSize)) {
      if (metrics != null) {
        waiter.metric = metrics.enqueueRequest(metric);
      }
      waiters.add(waiter);
      checkPending();
    } else {
      waiter.handleFailure(null, new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + queueMaxSize));
    }
    return true;
  }

  private void checkPending() {
    while (true) {
      Waiter<C> waiter = waiters.peek();
      if (waiter == null) {
        break;
      }
      if (metric != null) {
        metrics.dequeueRequest(metric, waiter.metric);
      }
      if (capacity > 0) {
        capacity--;
        ConnectionHolder<C> conn = available.peek();
        if (++conn.inflight == conn.concurrency) {
          available.poll();
        }
        waiters.poll();
        ContextImpl ctx = conn.context;
        ctx.nettyEventLoop().execute(() -> {
          if (connector.isValid(conn.connection)) {
            boolean handled = deliverInternal(conn, waiter);
            if (!handled) {
              synchronized (Pool.this) {
                recycleConnection(conn);
                checkPending();
              }
            }
          } else {
            synchronized (Pool.this) {
              waiters.add(waiter);
              closed(conn);
            }
          }
        });
      } else if (weight < maxWeight) {
        waiters.poll();
        weight += createConnection(waiter);
      } else {
        break;
      }
    }
  }

  private long createConnection(Waiter<C> waiter) {
    ConnectionHolder<C> holder  = new ConnectionHolder<>();
    ConnectionListener<C> listener = new ConnectionListener<C>() {
      @Override
      public void onConnectSuccess(C conn, long concurrency, Channel channel, ContextImpl context, long oldWeight, long newWeight, long maxConcurrency) {
        synchronized (Pool.this) {
          weight += newWeight - oldWeight;
          holder.context = context;
          holder.concurrency = Math.min(concurrency, maxConcurrency);
          holder.connection = conn;
          holder.channel = channel;
          holder.weight = newWeight;
          holder.maxConcurrency = maxConcurrency;
          initConnection(waiter, holder);
          checkPending();
        }
      }
      @Override
      public void onConnectFailure(Throwable err, long weight) {
        waiter.handleFailure(waiter.context, err);
        synchronized (Pool.this) {
          Pool.this.weight -= weight;
          all.remove(holder);
          checkPending();
          checkClose();
        }
      }
      @Override
      public void onConcurrencyChange(C conn, long concurrency) {
        synchronized (Pool.this) {
          concurrency = Math.min(concurrency, holder.maxConcurrency);
          if (holder.concurrency < concurrency) {
            long diff = concurrency - holder.concurrency;
            capacity += diff;
            if (holder.inflight == holder.concurrency) {
              available.add(holder);
            }
            holder.concurrency = concurrency;
            checkPending();
          } else {
            throw new UnsupportedOperationException("Not yet implemented");
          }
        }
      }
      @Override
      public void onRecycle(C conn) {
        Pool.this.recycle(holder);
      }
      @Override
      public void onClose(C conn) {
        Pool.this.closed(holder);
      }
    };
    all.add(holder);
    return connector.connect(listener, metric, waiter.context, peerHost, ssl, host, port);
  }

  private synchronized void recycle(ConnectionHolder<C> conn) {
    recycleConnection(conn);
    checkPending();
  }

  private void recycleConnection(ConnectionHolder<C> conn) {
    if (conn.inflight == 0) {
      log.debug("Attempt to recycle a connection more than permitted");
      return;
    }
    capacity++;
    if (conn.inflight == conn.concurrency) {
      available.add(conn);
    }
    conn.inflight--;
  }

  private synchronized void closed(ConnectionHolder<C> conn) {
    closeConnection(conn);
    checkPending();
    checkClose();
  }

  private void closeConnection(ConnectionHolder<C> conn) {
    connectionRemoved.handle(conn);
    all.remove(conn);
    long remaining = conn.concurrency - conn.inflight;
    if (remaining > 0) {
      available.remove(conn);
      capacity -= remaining;
    }
    weight -= conn.weight;
    // Remove if capacity > 0
  }

  private synchronized void initConnection(Waiter<C> waiter, ConnectionHolder<C> holder) {
    connectionAdded.handle(holder);
    all.add(holder);
    waiter.initConnection(holder.connection);
    if (holder.concurrency > 0) {
      holder.inflight++;
      if (holder.inflight < holder.concurrency) {
        capacity += holder.concurrency - holder.inflight;
        available.add(holder);
      }
      boolean consumed = deliverInternal(holder, waiter);
      if (!consumed) {
        recycleConnection(holder);
      }
    } else {
      waiters.add(waiter);
    }
  }

  private boolean deliverInternal(ConnectionHolder<C> conn, Waiter<C> waiter) {
    try {
      return waiter.handleConnection(conn.connection);
    } catch (Exception e) {
      // Handle this case gracefully
      e.printStackTrace();
      return true;
    }
  }

  private void checkClose() {
    if (all.isEmpty()) {
      // No waiters and no connections - remove the ConnQueue
      if (metrics != null) {
        metrics.closeEndpoint(host, port, metric);
      }
      closed = true;
      poolClosed.handle(null);
    }
  }
}
