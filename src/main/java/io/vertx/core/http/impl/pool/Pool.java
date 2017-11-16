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
import java.util.function.BiConsumer;

/**
 * The pool is a queue of waiters and a list of connections.
 *
 * The pools invariant:
 * - the pool {@link #capacity} is the sum of the {@link Holder#capacity} of the {@link #available} list
 * - a connection in the {@link #available} list has its {@code Holder#capacity > 0}
 * - the {@link #weight} is the sum of all {@link Holder#weight}, i.e the connections in the {@link #all} set
 *
 * A connection is delivered to a {@link Waiter} on the connection's event loop thread, the waiter must take care of
 * calling {@link io.vertx.core.impl.ContextInternal#executeFromIO} if necessary.
 *
 * Calls to the pool are synchronized on the pool to avoid race conditions and maintain its invariants. This pool can
 * be called from different threads safely (although it is not encouraged for performance reasons), since it synchronizes
 * on the pool and callbacks are asynchronous on the event loop thread of the connection without holding the pool
 * lock to avoid deadlocks. We benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * To constrain the number of connections the pool maintains a {@link #weight} value that must remain below the the
 * {@link #maxWeight} value to create a connection. Weight is used instead of counting connection because this pool
 * can mix connections with different concurrency (HTTP/1 and HTTP/2) and this flexibility is necessary.
 *
 * When a connection is created an initial weight is returned by the {@link ConnectionProvider#connect} method and is
 * added to the current weight. When the channel is connected the {@link ConnectionListener#onConnectSuccess} callback
 * provides the initial weight returned by the connect method and the actual connection weight so it can be used to
 * correct the current weight. When the channel fails to connect the {@link ConnectionListener#onConnectFailure} failure
 * provides the initial weight so it can be used to correct the current weight.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Pool<C> {

  /**
   * Pool state associated with a connection.
   *
   * @param <C> the connection type
   */
  public static class Holder<C> {

    C connection;        // The connection instance
    long concurrency;    // How many times we can borrow from the connection
    long capacity;       // How many times the connection is currently borrowed (0 <= capacity <= concurrency)
    Channel channel;     // Transport channel
    ContextImpl context; // Context associated with the connection
    long weight;         // The weight that participates in the pool weight

  }
  private static final Logger log = LoggerFactory.getLogger(Pool.class);

  private final HttpClientMetrics metrics; // todo: later switch to PoolMetrics
  private final Object metric;
  private final ConnectionProvider<C> connector;
  private final long maxWeight;
  private final int queueMaxSize;

  private final Queue<Waiter<C>> waiters = new ArrayDeque<>();
  private final Set<Holder<C>> all;
  private final Deque<Holder<C>> available;
  private boolean closed;
  private long capacity;
  private long weight;
  private final Handler<Void> poolClosed;
  private final BiConsumer<Channel, C> connectionAdded;
  private final BiConsumer<Channel, C> connectionRemoved;

  public Pool(ConnectionProvider<C> connector,
              HttpClientMetrics metrics,
              Object metric,
              int queueMaxSize,
              long maxWeight,
              Handler<Void> poolClosed,
              BiConsumer<Channel, C> connectionAdded,
              BiConsumer<Channel, C> connectionRemoved) {
    this.maxWeight = maxWeight;
    this.connector = connector;
    this.metric = metric;
    this.queueMaxSize = queueMaxSize;
    this.metrics = metrics;
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
      waiter.handleFailure(waiter.context, new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + queueMaxSize));
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
        Holder<C> conn = available.peek();
        if (--conn.capacity == 0) {
          available.poll();
        }
        waiters.poll();
        ContextImpl ctx = conn.context;
        ctx.nettyEventLoop().execute(() -> {
          if (connector.isValid(conn.connection)) {
            boolean handled = deliverToWaiter(conn, waiter);
            if (!handled) {
              synchronized (Pool.this) {
                recycleConnection(conn, 1,false);
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
    Holder<C> holder  = new Holder<>();
    ConnectionListener<C> listener = new ConnectionListener<C>() {
      @Override
      public void onConnectSuccess(C conn, long concurrency, Channel channel, ContextImpl context, long initialWeight, long actualWeight) {
        waiter.initConnection(context, conn);
        boolean usable;
        synchronized (Pool.this) {
          usable = initConnection(waiter, holder, context, concurrency, conn, channel, initialWeight, actualWeight);
        }
        if (usable) {
          boolean consumed = deliverToWaiter(holder, waiter);
          synchronized (Pool.this) {
            if (!consumed) {
              synchronized (this) {
                recycleConnection(holder, 1,false);
              }
            }
            checkPending();
          }
        } else {
          synchronized (Pool.this) {
            checkPending();
          }
        }
      }
      @Override
      public void onConnectFailure(ContextImpl context, Throwable err, long weight) {
        waiter.handleFailure(context, err);
        synchronized (Pool.this) {
          Pool.this.weight -= weight;
          all.remove(holder);
          checkPending();
          checkClose();
        }
      }
      @Override
      public void onConcurrencyChange(long concurrency) {
        synchronized (Pool.this) {
          if (holder.concurrency < concurrency) {
            long diff = concurrency - holder.concurrency;
            capacity += diff;
            if (holder.capacity == 0) {
              available.add(holder);
            }
            holder.capacity += diff;
            holder.concurrency = concurrency;
            checkPending();
          } else if (holder.concurrency > concurrency) {
            throw new UnsupportedOperationException("Not yet implemented");
          }
        }
      }
      @Override
      public void onRecycle(int capacity, boolean disposable) {
        if (capacity < 0) {
          throw new IllegalArgumentException("Illegal capacity");
        }
        Pool.this.recycle(holder, capacity, disposable);
      }
      @Override
      public void onClose() {
        Pool.this.closed(holder);
      }
    };
    all.add(holder);
    return connector.connect(listener, waiter.context);
  }

  private synchronized void recycle(Holder<C> conn, int capacity, boolean closeable) {
    recycleConnection(conn, capacity, closeable);
    checkPending();
    checkClose();
  }

  private synchronized void closed(Holder<C> conn) {
    closeConnection(conn);
    checkPending();
    checkClose();
  }

  /**
   * Should not be called under the pool lock.
   */
  private boolean deliverToWaiter(Holder<C> conn, Waiter<C> waiter) {
    try {
      return waiter.handleConnection(conn.context, conn.connection);
    } catch (Exception e) {
      // Handle this case gracefully
      e.printStackTrace();
      return true;
    }
  }

  // These methods assume to be called under synchronization

  private void recycleConnection(Holder<C> conn, int c, boolean closeable) {
    long nc = conn.capacity + c;
    if (nc > conn.concurrency) {
      log.debug("Attempt to recycle a connection more than permitted");
      return;
    }
    if (closeable && nc == conn.concurrency && waiters.isEmpty()) {
      available.remove(conn);
      capacity -= conn.concurrency;
      conn.capacity = 0;
      connector.close(conn.connection);
    } else {
      capacity += c;
      if (conn.capacity == 0) {
        available.add(conn);
      }
      conn.capacity = nc;
    }
  }

  private void closeConnection(Holder<C> holder) {
    connectionRemoved.accept(holder.channel, holder.connection);
    all.remove(holder);
    if (holder.capacity > 0) {
      available.remove(holder);
      capacity -= holder.capacity;
    }
    weight -= holder.weight;
  }

  private boolean initConnection(Waiter<C> waiter, Holder<C> holder, ContextImpl context, long concurrency, C conn, Channel channel, long oldWeight, long newWeight) {
    weight += newWeight - oldWeight;
    holder.context = context;
    holder.concurrency = concurrency;
    holder.connection = conn;
    holder.channel = channel;
    holder.weight = newWeight;
    holder.capacity = concurrency;
    connectionAdded.accept(holder.channel, holder.connection);
    all.add(holder);
    if (holder.capacity == 0) {
      waiters.add(waiter);
      return false;
    }
    holder.capacity--;
    if (holder.capacity > 0) {
      capacity += holder.capacity;
      available.add(holder);
    }
    return true;
  }

  private void checkClose() {
    if (all.isEmpty()) {
      // No waiters and no connections - remove the ConnQueue
      closed = true;
      poolClosed.handle(null);
    }
  }
}
