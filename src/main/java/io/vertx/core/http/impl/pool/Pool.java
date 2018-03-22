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
 * When the channel is connected the {@link ConnectionListener#onConnectSuccess} callback
 * provides the initial weight returned by the connect method and the actual connection weight so it can be used to
 * correct the current weight. When the channel fails to connect the {@link ConnectionListener#onConnectFailure} failure
 * provides the initial weight so it can be used to correct the current weight.
 *
 * When a connection is recycled and reaches its full capacity (i.e {@code Holder#concurrency == Holder#capacity},
 * the behavior depends on the {@link ConnectionListener#onRecycle(boolean)} event that release this connection.
 * When {@code disposable} is {@code true} the connection is closed, otherwise it is maintained in the pool, letting
 * the borrower define the behavior. HTTP/1 will close the connection and HTTP/2 will maintain it.
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

    boolean removed;     // Removed
    C connection;        // The connection instance
    long concurrency;    // How many times we can borrow from the connection
    long capacity;       // How many times the connection is currently borrowed (0 <= capacity <= concurrency)
    Channel channel;     // Transport channel
    ContextInternal context; // Context associated with the connection
    long weight;         // The weight that participates in the pool weight

  }
  private static final Logger log = LoggerFactory.getLogger(Pool.class);

  private final ConnectionProvider<C> connector;
  private final BiConsumer<Channel, C> connectionAdded;
  private final BiConsumer<Channel, C> connectionRemoved;

  private final int queueMaxSize;                                   // the queue max size (does not include inflight waiters)
  private final Queue<Waiter<C>> waitersQueue = new ArrayDeque<>(); // The waiters pending
  private int waitersCount;                                         // The number of waiters (including the inflight waiters not in the queue)

  private final Deque<Holder<C>> available;                         // Available connections

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
              BiConsumer<Channel, C> connectionRemoved) {
    this.maxWeight = maxWeight;
    this.initialWeight = initialWeight;
    this.connector = connector;
    this.queueMaxSize = queueMaxSize;
    this.poolClosed = poolClosed;
    this.available = new ArrayDeque<>();
    this.connectionAdded = connectionAdded;
    this.connectionRemoved = connectionRemoved;
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
   * @param waiter the waiter
   * @return whether the pool can satisfy the request
   */
  public synchronized boolean getConnection(Waiter<C> waiter) {
    if (closed) {
      return false;
    }
    int size = waitersQueue.size();
    if (size == 0 && acquireConnection(waiter)) {
      waitersCount++;
    } else if (queueMaxSize < 0  || size < queueMaxSize) {
      waitersCount++;
      waitersQueue.add(waiter);
    } else {
      waiter.context.nettyEventLoop().execute(() -> {
        waiter.handleFailure(waiter.context, new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + queueMaxSize));
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
      if (--conn.capacity == 0) {
        available.poll();
      }
      ContextInternal ctx = conn.context;
      ctx.nettyEventLoop().execute(() -> {
        boolean handled = deliverToWaiter(conn, waiter);
        synchronized (Pool.this) {
          waitersCount--;
          if (!handled) {
            synchronized (Pool.this) {
              recycleConnection(conn, 1,false);
              checkPending();
            }
          }
        }
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

  private void createConnection(Waiter<C> waiter) {
    Holder<C> holder  = new Holder<>();
    ConnectionListener<C> listener = new ConnectionListener<C>() {
      @Override
      public void onConnectSuccess(C conn, long concurrency, Channel channel, ContextInternal context, long actualWeight) {
        // Update state
        synchronized (Pool.this) {
          initConnection(holder, context, concurrency, conn, channel, actualWeight);
        }
        // Init connection - state might change (i.e init could close the connection)
        waiter.initConnection(context, conn);
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
        }
        boolean consumed = deliverToWaiter(holder, waiter);
        synchronized (Pool.this) {
          if (!consumed) {
            recycleConnection(holder, 1,false);
          }
          checkPending();
        }
      }
      @Override
      public void onConnectFailure(ContextInternal context, Throwable err) {
        waiter.handleFailure(context, err);
        synchronized (Pool.this) {
          waitersCount--;
          Pool.this.weight -= initialWeight;
          holder.removed = true;
          checkPending();
          checkClose();
        }
      }
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
      public void onRecycle(boolean disposable) {
        synchronized (Pool.this) {
          if (holder.removed) {
            return;
          }
          recycle(holder, 1, disposable);
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
    connector.connect(listener, waiter.context);
  }

  private synchronized void recycle(Holder<C> holder, int capacity, boolean closeable) {
    recycleConnection(holder, capacity, closeable);
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
    long newCapacity = conn.capacity + c;
    if (newCapacity > conn.concurrency) {
      log.debug("Attempt to recycle a connection more than permitted");
      return;
    }
    if (closeable && newCapacity == conn.concurrency && waitersQueue.isEmpty()) {
      available.remove(conn);
      conn.capacity = 0;
      connector.close(conn.connection);
    } else {
      if (conn.capacity == 0) {
        available.add(conn);
      }
      conn.capacity = newCapacity;
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
