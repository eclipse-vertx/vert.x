/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl.pool;

import io.netty.channel.Channel;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * The pool is a queue of waiters and a list of connections.
 *
 * Pool invariants:
 * - a connection in the {@link #available} list has its {@code Holder#capacity > 0}
 * - the {@link #weight} is the sum of all inflight connections {@link Holder#weight}
 *
 * A connection is delivered to a {@link Waiter} on the connection's event loop thread, the waiter must take care of
 * calling {@link io.vertx.core.impl.ContextInternal#executeFromIO} if necessary.
 *
 * Calls to the pool are synchronized on the pool to avoid race conditions and maintain its invariants. This pool can
 * be called from different threads safely (although it is not encouraged for performance reasons, we benefit from biased
 * locking which makes the overhead of synchronized near zero), since it synchronizes on the pool.
 *
 * In order to avoid deadlocks, acquisition events (success or failure) are dispatched on the event loop thread of the
 * connection without holding the pool lock.
 *
 * To constrain the number of connections the pool maintains a {@link #weight} value that must remain below the the
 * {@link #maxWeight} value to create a connection. Weight is used instead of counting connection because this pool
 * can mix connections with different concurrency (HTTP/1 and HTTP/2) and this flexibility is necessary.
 *
 * When a connection is created an {@link #initialWeight} is added to the current weight.
 * When the channel is connected the {@link ConnectResult} callback value provides actual connection weight so it
 * can be used to correct the pool weight. When the channel fails to connect the initial weight is used
 * to correct the pool weight.
 *
 * When a connection is recycled and reaches its full capacity (i.e {@code Holder#concurrency == Holder#capacity},
 * the behavior depends on the {@link ConnectionListener#onRecycle(long)} event that releases this connection.
 * When {@code expirationTimestamp} is {@code 0L} the connection is closed, otherwise it is maintained in the pool,
 * letting the borrower define the expiration timestamp. The value is set according to the HTTP client connection
 * keep alive timeout.
 *
 * When a waiter asks for a connection, it is either added to the queue (when it's not empty) or attempted to be
 * served (from the pool or by creating a new connection) or failed. The {@link #waitersCount} is the number
 * of total waiters (the waiters in {@link #waitersQueue} but also the inflight) so we know if we can close the pool
 * or not. The {@link #waitersCount} is incremented when a waiter wants to acquire a connection succesfully (i.e
 * it is either added to the queue or served from the pool) and decremented when the it gets a reply (either with
 * a connection or with a failure).
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

    boolean removed;          // Removed
    C connection;             // The connection instance
    long concurrency;         // How many times we can borrow from the connection
    long capacity;            // How many times the connection is currently borrowed (0 <= capacity <= concurrency)
    Channel channel;          // Transport channel
    ContextInternal context;  // Context associated with the connection
    long weight;              // The weight that participates in the pool weight
    long expirationTimestamp; // The expiration timestamp when (concurrency == capacity) otherwise -1L

    @Override
    public String toString() {
      return "Holder[removed=" + removed + ",capacity=" + capacity + ",concurrency=" + concurrency + ",expirationTimestamp=" + expirationTimestamp + "]";
    }
  }

  private static final Logger log = LoggerFactory.getLogger(Pool.class);

  private final ConnectionProvider<C> connector;
  private final BiConsumer<Channel, C> connectionAdded;
  private final BiConsumer<Channel, C> connectionRemoved;

  private final int queueMaxSize;                                   // the queue max size (does not include inflight waiters)
  private final Queue<Waiter<C>> waitersQueue = new ArrayDeque<>(); // The waiters pending
  private int waitersCount;                                         // The number of waiters (including the inflight waiters not in the queue)

  private final Deque<Holder<C>> available;                         // Available connections
  private final boolean fifo;                                       // Recycling policy

  private final long initialWeight;                                 // The initial weight of a connection
  private final long maxWeight;                                     // The max weight (equivalent to max pool size)
  private long weight;                                              // The actual pool weight (equivalent to connection count)

  private boolean closed;
  private final Handler<Void> poolClosed;


  public Pool(ConnectionProvider<C> connector,
              int queueMaxSize,
              long initialWeight,
              long maxWeight,
              Handler<Void> poolClosed,
              BiConsumer<Channel, C> connectionAdded,
              BiConsumer<Channel, C> connectionRemoved,
              boolean fifo) {
    this.maxWeight = maxWeight;
    this.initialWeight = initialWeight;
    this.connector = connector;
    this.queueMaxSize = queueMaxSize;
    this.poolClosed = poolClosed;
    this.available = new ArrayDeque<>();
    this.connectionAdded = connectionAdded;
    this.connectionRemoved = connectionRemoved;
    this.fifo = fifo;
  }

  public synchronized int waitersInQueue() {
    return waitersQueue.size();
  }

  public synchronized int waitersCount() {
    return waitersCount;
  }

  public synchronized long weight() {
    return weight;
  }

  public synchronized long capacity() {
    return available.stream().mapToLong(c -> c.capacity).sum();
  }

  /**
   * Get a connection for a waiter asynchronously.
   *
   * @param context the context
   * @param handler the handler
   * @return whether the pool can satisfy the request
   */
  public synchronized boolean getConnection(ContextInternal context, Handler<AsyncResult<C>> handler) {
    if (closed) {
      return false;
    }
    Waiter<C> waiter = new Waiter<>(context, handler);
    int size = waitersQueue.size();
    if (size == 0 && acquireConnection(waiter)) {
      waitersCount++;
    } else if (queueMaxSize < 0  || size < queueMaxSize) {
      waitersCount++;
      waitersQueue.add(waiter);
    } else {
      waiter.context.nettyEventLoop().execute(() -> {
        waiter.handler.handle(Future.failedFuture(new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + queueMaxSize)));
      });
    }
    return true;
  }

  /**
   * Attempt to acquire a connection for the waiter, either borrowed from the pool or by creating a new connection.
   *
   * This method does not modify the waitersQueue list.
   *
   * @return wether the waiter is assigned a connection (or a future connection)
   */
  private boolean acquireConnection(Waiter<C> waiter) {
    if (available.size() > 0) {
      Holder<C> conn = available.peek();
      long capacity = conn.capacity--;
      if (capacity == 1) {
        conn.expirationTimestamp = -1L;
        available.poll();
      }
      ContextInternal ctx = conn.context;
      waitersCount--;
      if (capacity == conn.concurrency) {
        connector.activate(conn.connection);
      }
      ctx.nettyEventLoop().execute(() -> {
        waiter.handler.handle(Future.succeededFuture(conn.connection));
      });
      return true;
    } else if (weight < maxWeight) {
      weight += initialWeight;
      waiter.context.nettyEventLoop().execute(() -> {
        createConnection(waiter);
      });
      return true;
    } else {
      return false;
    }
  }

  /**
   * Close all unused connections with a {@code timestamp} greater than expiration timestamp.
   *
   * @param timestamp the timestamp value
   * @return the number of closed connections when calling this method
   */
  public synchronized int closeIdle(long timestamp) {
    int removed = 0;
    if (waitersQueue.isEmpty() && available.size() > 0) {
      List<Holder<C>> copy = new ArrayList<>(available);
      for (Holder<C> conn : copy) {
        if (conn.capacity == conn.concurrency && conn.expirationTimestamp <= timestamp) {
          removed++;
          closeConnection(conn);
          connector.close(conn.connection);
        }
      }
    }
    return removed;
  }

  private void checkPending() {
    while (waitersQueue.size() > 0) {
      Waiter<C> waiter = waitersQueue.peek();
      if (acquireConnection(waiter)) {
        waitersQueue.poll();
      } else {
        break;
      }
    }
  }

  private ConnectionListener<C> createListener(Holder<C> holder) {
    return new ConnectionListener<C>() {
      @Override
      public void onConcurrencyChange(long concurrency) {
        synchronized (Pool.this) {
          if (holder.removed) {
            return;
          }
          if (holder.concurrency < concurrency) {
            long diff = concurrency - holder.concurrency;
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
      public void onRecycle(long expirationTimestamp) {
        if (expirationTimestamp < 0L) {
          throw new IllegalArgumentException("Invalid TTL");
        }
        synchronized (Pool.this) {
          if (holder.removed) {
            return;
          }
          recycle(holder, 1, expirationTimestamp);
        }
      }

      @Override
      public void onDiscard() {
        synchronized (Pool.this) {
          if (holder.removed) {
            return;
          }
          closed(holder);
        }
      }
    };
  }

  private void createConnection(Waiter<C> waiter) {
    Holder<C> holder  = new Holder<>();
    ConnectionListener<C> listener = createListener(holder);
    connector.connect(listener, waiter.context, ar -> {
      if (ar.succeeded()) {
        ConnectResult<C> result = ar.result();
        // Update state
        synchronized (Pool.this) {
          initConnection(holder, result.context(), result.concurrency(), result.connection(), result.channel(), result.weight());
        }
        // Init connection - state might change (i.e init could close the connection)
        synchronized (Pool.this) {
          if (holder.capacity == 0) {
            waitersQueue.add(waiter);
            checkPending();
            return;
          }
          waitersCount--;
          holder.capacity--;
          if (holder.capacity > 0) {
            available.add(holder);
          }
          connector.activate(holder.connection);
        }
        waiter.handler.handle(Future.succeededFuture(holder.connection));
        synchronized (Pool.this) {
          checkPending();
        }
      } else {
        waiter.handler.handle(Future.failedFuture(ar.cause()));
        synchronized (Pool.this) {
          waitersCount--;
          Pool.this.weight -= initialWeight;
          holder.removed = true;
          checkPending();
          checkClose();
        }
      }
    });
  }

  private synchronized void recycle(Holder<C> holder, int capacity, long timestamp) {
    recycleConnection(holder, capacity, timestamp);
    checkPending();
    checkClose();
  }

  private synchronized void closed(Holder<C> holder) {
    closeConnection(holder);
    checkPending();
    checkClose();
  }

  private void closeConnection(Holder<C> holder) {
    holder.removed = true;
    connectionRemoved.accept(holder.channel, holder.connection);
    if (holder.capacity > 0) {
      available.remove(holder);
      holder.capacity = 0;
    }
    weight -= holder.weight;
  }

  // These methods assume to be called under synchronization

  private void recycleConnection(Holder<C> conn, int c, long timestamp) {
    long newCapacity = conn.capacity + c;
    if (newCapacity > conn.concurrency) {
      log.debug("Attempt to recycle a connection more than permitted");
      return;
    }
    if (timestamp == 0L && newCapacity == conn.concurrency && waitersQueue.isEmpty()) {
      available.remove(conn);
      conn.expirationTimestamp = -1L;
      conn.capacity = 0;
      connector.close(conn.connection);
    } else {
      if (conn.capacity == 0) {
        if (fifo) {
          available.addLast(conn);
        } else {
          available.addFirst(conn);
        }
      }
      conn.expirationTimestamp = timestamp;
      conn.capacity = newCapacity;
      if (newCapacity == conn.concurrency) {
        connector.deactivate(conn.connection);
      }
    }
  }

  private void initConnection(Holder<C> holder, ContextInternal context, long concurrency, C conn, Channel channel, long weight) {
    this.weight += initialWeight - weight;
    holder.context = context;
    holder.concurrency = concurrency;
    holder.connection = conn;
    holder.channel = channel;
    holder.weight = weight;
    holder.capacity = concurrency;
    holder.expirationTimestamp = -1L;
    connectionAdded.accept(holder.channel, holder.connection);
  }

  private void checkClose() {
    if (weight == 0 && waitersCount == 0) {
      // No waitersQueue and no connections - remove the ConnQueue
      closed = true;
      poolClosed.handle(null);
    }
  }
}
