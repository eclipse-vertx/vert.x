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

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.impl.ContextInternal;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The pool is a state machine that maintains a queue of waiters and a list of available connections.
 * <p/>
 * Interactions with the pool modifies the pool state and then the pool will run tasks to make progress satisfying
 * the pool requests.
 * <p/>
 * The pool maintains a few invariants:
 * <ul>
 *   <li>a connection in the {@link #available} set has its {@link Holder#capacity}{@code > 0}</li>
 *   <li>{@link #weight} is the sum of all connection's {@link Holder#weight}</li>
 *   <li>{@link #capacity} is the sum of all connection's {@link Holder#capacity}</li>
 *   <li>{@link #connecting} is the number of the connections connecting but not yet connected</li>
 * </ul>
 *
 * A connection is delivered to a {@link Waiter} on the pool's context event loop thread, the waiter must take care of
 * calling {@link io.vertx.core.impl.ContextInternal#executeFromIO} if necessary.
 * <p/>
 * Calls to the pool are synchronized on the pool to avoid race conditions and maintain its invariants. This pool can
 * be called from different threads safely (although it is not encouraged for performance reasons, we benefit from biased
 * locking which makes the overhead of synchronized near zero), since it synchronizes on the pool.
 *
 * <h3>Pool weight</h3>
 * To constrain the number of connections the pool maintains a {@link #weight} field that must remain lesser than
 * {@link #maxWeight} to create a connection. Such weight is used instead of counting connection because the pool
 * can mix connections with different concurrency (HTTP/1 and HTTP/2) and this flexibility is necessary.
 * <p/>
 * When a connection is created an {@link #initialWeight} is added to the current weight.
 * When the channel is connected the {@link ConnectResult} callback value provides actual connection weight so it
 * can be used to correct the pool weight. When the channel fails to connect the initial weight is used
 * to correct the pool weight.
 *
 * <h3>Recycling a connection</h3>
 * When a connection is recycled and reaches its full capacity (i.e {@code Holder#concurrency == Holder#capacity},
 * the behavior depends on the {@link ConnectionListener#onRecycle(long)} event that releases this connection.
 * When {@code expirationTimestamp} is {@code 0L} the connection is closed, otherwise it is maintained in the pool,
 * letting the borrower define the expiration timestamp. The value is set according to the HTTP client connection
 * keep alive timeout.
 *
 * <h3>Acquiring a connection</h3>
 * When a waiter wants to acquire a connection it is added to the {@link #waitersQueue} and the request
 * is handled by the pool asynchronously:
 * <ul>
 *   <li>when there is an available pooled connection, this connection is delivered to the waiter and it is removed
 *   from the queue</li>
 *   <li>when there is no available connection a connection is created when {@link #weight}{@code <}{@link #maxWeight}</li>
 *   <li>when the max number of waiters is reached, the request is failed</li>
 *   <li>otherwise the waiter remains in the queue until progress can be done (i.e a connection is recycled, etc...)</li>
 * </ul>
 * Waiter notifications happens on the event-loop thread to avoid races with connection event happening on the same thread.
 *
 * <h3>Connection eviction</h3>
 * Connection can be evicted from the pool with {@link ConnectionListener#onEvict()}, after this call, the connection
 * is fully managed by the caller. This can be used for signalling a connection close or when the connection has
 * been upgraded for an HTTP connection.
 *
 * <h3>Pool progress</h3>
 * When the pool state is modified, an asynchronous task is executed to make the pool state progress. The pool ensures
 * that a single progress task is executed with the {@link #checkInProgress} flag.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Pool<C> {

  /**
   * Pool state associated with a connection.
   */
  public class Holder implements ConnectionListener<C> {

    boolean removed;          // Removed
    C connection;             // The connection instance
    long concurrency;         // How many times we can borrow from the connection
    long capacity;            // How many times the connection is currently borrowed (0 <= capacity <= concurrency)
    long weight;              // The weight that participates in the pool weight
    long expirationTimestamp; // The expiration timestamp when (concurrency == capacity) otherwise -1L

    private void init(long concurrency, C conn, long weight) {
      this.concurrency = concurrency;
      this.connection = conn;
      this.weight = weight;
      this.capacity = concurrency;
      this.expirationTimestamp = -1L;
    }

    @Override
    public void onConcurrencyChange(long concurrency) {
      setConcurrency(this, concurrency);
    }

    @Override
    public void onRecycle(long expirationTimestamp) {
      recycle(this, expirationTimestamp);
    }

    @Override
    public void onEvict() {
      evicted(this);
    }

    void connect() {
      connector.connect(this, context, ar -> {
        if (ar.succeeded()) {
          connectSucceeded(this, ar.result());
        } else {
          connectFailed(this, ar.cause());
        }
      });
    }

    @Override
    public String toString() {
      return "Holder[removed=" + removed + ",capacity=" + capacity + ",concurrency=" + concurrency + ",expirationTimestamp=" + expirationTimestamp + "]";
    }
  }

  private final ContextInternal context;
  private final ConnectionProvider<C> connector;
  private final Consumer<C> connectionAdded;
  private final Consumer<C> connectionRemoved;
  private final LongSupplier clock;

  private final int queueMaxSize;                                   // the queue max size (does not include inflight waiters)
  private final Deque<Waiter<C>> waitersQueue = new ArrayDeque<>(); // The waiters pending

  private final Deque<Holder> available;                            // Available connections, i.e having capacity > 0
  private final boolean fifo;                                       // Recycling policy
  private long capacity;                                            // The total available connection capacity
  private long connecting;                                          // The number of connections in progress

  private final long initialWeight;                                 // The initial weight of a connection
  private final long maxWeight;                                     // The max weight (equivalent to max pool size)
  private long weight;                                              // The actual pool weight (equivalent to connection count)

  private boolean checkInProgress;                                  // A flag to avoid running un-necessary checks
  private boolean closed;
  private final Handler<Void> poolClosed;

  public Pool(Context context,
              ConnectionProvider<C> connector,
              LongSupplier clock,
              int queueMaxSize,
              long initialWeight,
              long maxWeight,
              Handler<Void> poolClosed,
              Consumer<C> connectionAdded,
              Consumer<C> connectionRemoved,
              boolean fifo) {
    this.clock = clock;
    this.context = (ContextInternal) context;
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

  public synchronized long weight() {
    return weight;
  }

  public synchronized long capacity() {
    return capacity;
  }

  /**
   * Get a connection for a waiter asynchronously.
   *
   * @param handler the handler
   * @return whether the pool can satisfy the request
   */
  public synchronized boolean getConnection(Handler<AsyncResult<C>> handler) {
    if (closed) {
      return false;
    }
    Waiter<C> waiter = new Waiter<>(handler);
    waitersQueue.add(waiter);
    checkProgress();
    return true;
  }

  /**
   * Close all unused connections with a {@code timestamp} greater than expiration timestamp.
   *
   * @return the number of closed connections when calling this method
   */
  public synchronized void closeIdle() {
    checkProgress();
  }

  /**
   * Check whether the pool can make progress toward satisfying the waiters.
   */
  private void checkProgress() {
    if (!checkInProgress && (canProgress() || canClose())) {
      checkInProgress = true;
      context.nettyEventLoop().execute(this::checkPendingTasks);
    }
  }

  private boolean canProgress() {
    if (waitersQueue.size() > 0) {
      return (canAcquireConnection() || needToCreateConnection() || canEvictWaiter());
    } else {
      return capacity > 0L;
    }
  }

  /**
   * Run pending progress tasks.
   */
  private void checkPendingTasks() {
    while (true) {
      Runnable task;
      synchronized (this) {
        task = nextTask();
        if (task == null) {
          // => Can't make more progress
          checkInProgress = false;
          checkClose();
          break;
        }
      }
      task.run();
    }
  }

  /**
   * @return {@code true} if a connection can be acquired from the pool
   */
  private boolean canAcquireConnection() {
    return capacity > 0;
  }

  /**
   * @return {@code true} if a connection needs to be created
   */
  private boolean needToCreateConnection() {
    return weight < maxWeight && (waitersQueue.size() - connecting) > 0;
  }

  /**
   * @return {@code true} if a waiter can be evicted from the queue
   */
  private boolean canEvictWaiter() {
    return queueMaxSize >= 0 && (waitersQueue.size() - connecting) > queueMaxSize;
  }

  private Runnable nextTask() {
    if (waitersQueue.size() > 0) {
      // Acquire a task that will deliver a connection
      if (canAcquireConnection()) {
        Holder conn = available.peek();
        capacity--;
        if (--conn.capacity == 0) {
          conn.expirationTimestamp = -1L;
          available.poll();
        }
        Waiter<C> waiter = waitersQueue.poll();
        return () -> waiter.handler.handle(Future.succeededFuture(conn.connection));
      } else if (needToCreateConnection()) {
        connecting++;
        weight += initialWeight;
        Holder holder  = new Holder();
        return holder::connect;
      } else if (canEvictWaiter()) {
        Waiter<C> waiter = waitersQueue.removeLast();
        return () -> waiter.handler.handle(Future.failedFuture(new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + queueMaxSize)));
      }
    } else if (capacity > 0) {
      long now = clock.getAsLong();
      List<Holder> expired = null;
      for (Iterator<Holder> it  = available.iterator();it.hasNext();) {
        Holder holder = it.next();
        if (holder.capacity == holder.concurrency && (holder.expirationTimestamp == 0 || now >= holder.expirationTimestamp)) {
          it.remove();
          if (holder.capacity > 0) {
            capacity -= holder.capacity;
          }
          holder.expirationTimestamp = -1L;
          holder.capacity = 0;
          if (expired == null) {
            expired = new ArrayList<>();
          }
          expired.add(holder);
        }
      }
      if (expired != null) {
        List<Holder> toClose = expired;
        return () -> {
          toClose.forEach(holder -> {
            connector.close(holder.connection);
          });
        };
      }
    }
    return null;
  }

  private boolean canClose() {
    return weight == 0 && waitersQueue.isEmpty();
  }

  private void checkClose() {
    if (canClose()) {
      // No waitersQueue and no connections - remove the ConnQueue
      closed = true;
      poolClosed.handle(null);
    }
  }

  /**
   * Handle connect success, a number of waiters will be satisfied according to the connection's concurrency.
   */
  private void connectSucceeded(Holder holder, ConnectResult<C> result) {
    List<Waiter<C>> waiters;
    synchronized (this) {
      connecting--;
      weight += initialWeight - result.weight();
      holder.init(result.concurrency(), result.connection(), result.weight());
      waiters = new ArrayList<>();
      while (holder.capacity > 0 && waitersQueue.size() > 0) {
        waiters.add(waitersQueue.poll());
        holder.capacity--;
      }
      if (holder.capacity > 0) {
        available.add(holder);
        capacity += holder.capacity;
      }
      checkProgress();
    }
    connectionAdded.accept(holder.connection);
    for (Waiter<C> waiter : waiters) {
      waiter.handler.handle(Future.succeededFuture(holder.connection));
    }
  }

  /**
   * Handle connect failures, the first waiter is always failed to avoid infinite reconnection.
   */
  private void connectFailed(Holder holder, Throwable cause) {
    Waiter<C> waiter;
    synchronized (this) {
      connecting--;
      waiter = waitersQueue.poll();
      weight -= initialWeight;
      holder.removed = true;
      checkProgress();
    }
    if (waiter != null) {
      waiter.handler.handle(Future.failedFuture(cause));
    }
  }

  private synchronized void setConcurrency(Holder holder, long concurrency) {
    if (concurrency < 0L) {
      throw new IllegalArgumentException("Cannot set a negative concurrency value");
    }
    if (holder.removed) {
      assert false : "Cannot recycle removed holder";
      return;
    }
    if (holder.concurrency < concurrency) {
      long diff = concurrency - holder.concurrency;
      if (holder.capacity == 0) {
        available.add(holder);
      }
      capacity += diff;
      holder.capacity += diff;
      holder.concurrency = concurrency;
      checkProgress();
    } else if (holder.concurrency > concurrency) {
      throw new UnsupportedOperationException("Not yet implemented");
    }
  }

  private void recycle(Holder holder, long timestamp) {
    if (timestamp < 0L) {
      throw new IllegalArgumentException("Invalid timestamp");
    }
    if (holder.removed) {
      return;
    }
    C toClose;
    synchronized (this) {
      if (recycleConnection(holder, timestamp)) {
        toClose = holder.connection;
      } else {
        toClose = null;
      }
    }
    if (toClose != null) {
      connector.close(holder.connection);
    } else {
      synchronized (this) {
        checkProgress();
      }
    }
  }

  private synchronized void evicted(Holder holder) {
    if (holder.removed) {
      return;
    }
    evictConnection(holder);
    checkProgress();
  }

  private void evictConnection(Holder holder) {
    holder.removed = true;
    connectionRemoved.accept(holder.connection);
    if (holder.capacity > 0) {
      capacity -= holder.capacity;
      available.remove(holder);
      holder.capacity = 0;
    }
    weight -= holder.weight;
  }

  // These methods assume to be called under synchronization

  /**
   * Recycles a connection.
   *
   * @param holder the connection to recycle
   * @param timestamp the expiration timestamp of the connection
   * @return {@code true} if the connection shall be closed
   */
  private boolean recycleConnection(Holder holder, long timestamp) {
    long newCapacity = holder.capacity + 1;
    if (newCapacity > holder.concurrency) {
      throw new AssertionError("Attempt to recycle a connection more than permitted");
    }
    if (timestamp == 0L && newCapacity == holder.concurrency && capacity >= waitersQueue.size()) {
      if (holder.capacity > 0) {
        capacity -= holder.capacity;
        available.remove(holder);
      }
      holder.expirationTimestamp = -1L;
      holder.capacity = 0;
      return true;
    } else {
      capacity++;
      if (holder.capacity == 0) {
        if (fifo) {
          available.addLast(holder);
        } else {
          available.addFirst(holder);
        }
      }
      holder.expirationTimestamp = timestamp;
      holder.capacity++;
      return false;
    }
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    synchronized (this) {
      sb.append("Available:").append(File.separator);
      available.forEach(holder -> {
        sb.append(holder).append(File.separator);
      });
      sb.append("Waiters").append(File.separator);
      waitersQueue.forEach(w -> {
        sb.append(w.handler).append(File.separator);
      });
      sb.append("InitialWeight:").append(initialWeight).append(File.separator);
      sb.append("MaxWeight:").append(maxWeight).append(File.separator);
      sb.append("Weight:").append(weight).append(File.separator);
      sb.append("Capacity:").append(capacity).append(File.separator);
      sb.append("Connecting:").append(connecting).append(File.separator);
      sb.append("CheckInProgress:").append(checkInProgress).append(File.separator);
      sb.append("Closed:").append(closed).append(File.separator);
    }
    return sb.toString();
  }
}
