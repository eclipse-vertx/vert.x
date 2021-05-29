/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.pool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.impl.EventLoopContext;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * <p> The pool is a state machine that maintains a queue of waiters and a list of connections.
 *
 * <h3>Pool state</h3>
 *
 * <p> Interactions with the pool modifies the pool state and then the pool executes actions to make progress satisfying
 * the pool requests.
 *
 * <p> The pool is implemented as a non blocking state machine.
 *
 * <p> Calls to the pool are serialized to avoid race conditions and maintain its invariants. This pool can
 * be called from different threads safely. The pool state is mutated exclusively by {@link Executor.Action} and actions are serialized
 * by the executor.
 *
 * <h3>Pool capacity</h3>
 *
 * To constrain the number of connections the pool maintains a {@link #capacity} field that must remain lesser than
 * {@link #maxCapacity} to create a connection. Such capacity is used instead of counting connection because the pool
 * can mix connections with different concurrency (HTTP/1 and HTTP/2) and this flexibility is necessary.
 *
 * <h3>Pool connector</h3>
 *
 * The pool interacts with connections with the {@link PoolConnector}. The {@link PoolConnector.Listener}
 * let the connector interact with pool:
 * <ul>
 *   <li>The connector can remove connections from the pool using {@link PoolConnector.Listener#onRemove()}.</li>
 *   <li>The connector can signal the change of the connection capacity using {@link PoolConnector.Listener#onConcurrencyChange(long)}.</li>
 * </ul>
 *
 * <h3>Connection eviction</h3>
 *
 * Connections can be evicted from the pool with {@link ConnectionPool#evict(Predicate, Handler)}. It
 * can be used to implement keep alive timeout.
 *
 * <h3>Waiter lifecycle</h3>
 *
 * Connection requests are done with {@link ConnectionPool#acquire(EventLoopContext, int, Handler)}. Such request
 * creates a {@link PoolWaiter}. When such request is made
 *
 * <ul>
 *   <li>the waiter can be handed back a connection when one is immediately available</li>
 *   <li>a connection can be created and then handed back to the waiter that initiated the action</li>
 *   <li>the waiter can be enqueued when the pool is full and the wait queue is not full</li>
 *   <li>the waiter can be failed</li>
 * </ul>
 *
 * A connection acquisition a {@link PoolWaiter.Listener} can be provided, letting the requester
 * to get a reference on the waiter and later use {@link #cancel(PoolWaiter, Handler)} to cancel
 * a request.
 */
public class SimpleConnectionPool<C> implements ConnectionPool<C> {

  private static final Future POOL_CLOSED = Future.failedFuture("Pool closed");

  /**
   * Select the first available available connection with the same context.
   */
  private static final BiFunction<PoolWaiter, List<PoolConnection>, PoolConnection> SAME_CONTEXT_SELECTOR = (waiter, list) -> {
    int size = list.size();
    for (int i = 0;i < size;i++) {
      PoolConnection slot = list.get(i);
      if (slot.context() == waiter.context() && slot.concurrency() > 0) {
        return slot;
      }
    }
    return null;
  };

  /**
   * Select the first available available connection.
   */
  private static final BiFunction<PoolWaiter, List<PoolConnection>, PoolConnection> FIRST_AVAILABLE_SELECTOR = (waiter, list) -> {
    int size = list.size();
    for (int i = 0;i < size;i++) {
      PoolConnection slot = list.get(i);
      if (slot.concurrency() > 0) {
        return slot;
      }
    }
    return null;
  };

  /**
   * A slot for a connection.
   */
  static class Slot<C> implements PoolConnector.Listener, PoolConnection<C> {

    private final SimpleConnectionPool<C> pool;
    private final EventLoopContext context;
    private final Promise<C> result;
    private PoolWaiter<C> initiator;
    private C connection;    // The actual connection, might be null
    private int index;       // The index in the pool slots array
    private int concurrency;    // The current concurrency, i.e the number of acquisitions this connection supports
    private int maxConcurrency; // The connection maximum concurrency
    private int capacity;      // The connection capacity

    public Slot(SimpleConnectionPool<C> pool, EventLoopContext context, int index, int capacity) {
      this.pool = pool;
      this.context = context;
      this.connection = null;
      this.concurrency = 0;
      this.index = index;
      this.capacity = capacity;
      this.result = context.promise();
    }

    @Override
    public void onRemove() {
      pool.remove(this);
    }

    @Override
    public void onConcurrencyChange(long concurrency) {
      pool.setConcurrency(this, concurrency);
    }

    @Override
    public Context context() {
      return context;
    }

    @Override
    public C get() {
      return connection;
    }

    @Override
    public int concurrency() {
      return concurrency;
    }

    @Override
    public int maxConcurrency() {
      return maxConcurrency;
    }
  }

  private final PoolConnector<C> connector;
  private final int maxWaiters;
  private final int maxCapacity;
  private final int[] capacityFactors;
  private final Executor<SimpleConnectionPool<C>> sync;
  private final ListImpl list = new ListImpl();

  // Whether the pool is closed
  private boolean closed;

  // Selectors
  private BiFunction<PoolWaiter<C>, List<PoolConnection<C>>, PoolConnection<C>> selector;
  private BiFunction<PoolWaiter<C>, List<PoolConnection<C>>, PoolConnection<C>> fallbackSelector;

  // Connection state
  private final Slot<C>[] slots;    // The pool connections, this array is not sparse
  private int size;                 // The number of non null slots
  private int capacity;             // The pool capacity

  // The waiters
  private final Waiters<C> waiters;
  private int requests;

  SimpleConnectionPool(PoolConnector<C> connector, int[] maxSizes) {
    this(connector, maxSizes, -1);
  }

  SimpleConnectionPool(PoolConnector<C> connector, int[] maxSizes, int maxWaiters) {

    int[] capacities = new int[maxSizes.length];
    int maxCapacity = 1;
    int numSlots = 0;
    for (int i = 0;i < maxSizes.length;i++) {
      int maxSize = maxSizes[i];
      if (maxSize < 1) {
        throw new IllegalArgumentException();
      }
      maxCapacity *= maxSize;
      numSlots = Math.max(numSlots, maxSize);
    }
    for (int i = 0;i < maxSizes.length;i++) {
      capacities[i] = maxCapacity / maxSizes[i];
    }

    this.capacityFactors = capacities;
    this.connector = connector;
    this.slots = new Slot[numSlots];
    this.size = 0;
    this.maxWaiters = maxWaiters;
    this.capacity = 0;
    this.maxCapacity = maxCapacity;
    this.sync = new CombinerExecutor<>(this);
    this.selector = (BiFunction) SAME_CONTEXT_SELECTOR;
    this.fallbackSelector = (BiFunction) FIRST_AVAILABLE_SELECTOR;
    this.waiters = new Waiters<>();
  }

  @Override
  public ConnectionPool<C> connectionSelector(BiFunction<PoolWaiter<C>, List<PoolConnection<C>>, PoolConnection<C>> selector) {
    this.selector = selector;
    return this;
  }

  private void execute(Executor.Action<SimpleConnectionPool<C>> action) {
    sync.submit(action);
  }

  public int size() {
      return size;
  }

  public void connect(Slot<C> slot, PoolWaiter<C> waiter) {
    slot.initiator = waiter;
    connector.connect(slot.context, slot, ar -> {
      slot.initiator = null;
      if (ar.succeeded()) {
        execute(new ConnectSuccess<>(slot, ar.result(), waiter));
      } else {
        execute(new ConnectFailed<>(slot, ar.cause(), waiter));
      }
    });
  }

  private static class ConnectSuccess<C> implements Executor.Action<SimpleConnectionPool<C>> {

    private final Slot<C> slot;
    private final ConnectResult<C> result;
    private PoolWaiter<C> waiter;

    private ConnectSuccess(Slot<C> slot, ConnectResult<C> result, PoolWaiter<C> waiter) {
      this.slot = slot;
      this.result = result;
      this.waiter = waiter;
    }

    @Override
    public Runnable execute(SimpleConnectionPool<C> pool) {

      int capacity = pool.capacityFactors[(int)result.weight()];

      int initialCapacity = slot.capacity;
      slot.connection = result.connection();
      slot.maxConcurrency = (int)result.concurrency();
      slot.capacity = capacity;
      slot.concurrency = slot.maxConcurrency;
      pool.requests--;
      pool.capacity += (capacity - initialCapacity);
      if (pool.closed) {
        if (waiter.disposed) {
          waiter = null;
        } else {
          waiter.disposed = true;
        }
        return () -> {
          if (waiter != null) {
            slot.context.emit(POOL_CLOSED, waiter.handler);
          }
          slot.result.complete(slot.connection);
        };
      } else {
        if (slot.concurrency == 0) {
          if (!waiter.disposed) {
            pool.waiters.addFirst(waiter);
          }
          return null;
        }
        int c = 1;
        LeaseImpl<C>[] extra;
        int concurrency;
        if (waiter.disposed) {
          waiter = null;
          concurrency = slot.concurrency;
        } else {
          waiter.disposed = true;
          concurrency = slot.concurrency - 1;
        }
        int m = Math.min(concurrency, pool.waiters.size());
        if (m > 0) {
          c += m;
          extra = new LeaseImpl[m];
          for (int i = 0;i < m;i++) {
            extra[i] = new LeaseImpl<>(slot, pool.waiters.poll().handler);
          }
        } else {
          extra = null;
        }
        slot.concurrency -= c;
        return () -> {
          if (waiter != null) {
            new LeaseImpl<>(slot, waiter.handler).emit();
          }
          if (extra != null) {
            for (LeaseImpl<C> lease : extra) {
              lease.emit();
            }
          }
          slot.result.complete(slot.connection);
        };
      }
    }
  }

  private static class ConnectFailed<C> implements Executor.Action<SimpleConnectionPool<C>> {

    private final Slot<C> removed;
    private final Throwable cause;
    private PoolWaiter<C> waiter;

    public ConnectFailed(Slot<C> removed, Throwable cause, PoolWaiter<C> waiter) {
      this.removed = removed;
      this.cause = cause;
      this.waiter = waiter;
    }

    public Runnable execute(SimpleConnectionPool<C> pool) {
      pool.requests--;
      if (waiter.disposed) {
        waiter = null;
      } else {
        waiter.disposed = true;
      }
      if (pool.closed) {
        return () -> waiter.handler.handle(POOL_CLOSED);
      }
      pool.remove(removed);
      return () -> {
        if (waiter != null) {
          removed.context.emit(Future.failedFuture(cause), waiter.handler);
        }
        removed.result.fail(cause);
      };
    }
  }

  private static class Remove<C> implements Executor.Action<SimpleConnectionPool<C>> {

    protected final Slot<C> removed;

    private Remove(Slot<C> removed) {
      this.removed = removed;
    }

    @Override
    public Runnable execute(SimpleConnectionPool<C> pool) {
      if (pool.closed || pool.slots[removed.index] != removed) {
        return null;
      }
      int w = removed.capacity;
      removed.concurrency = 0;
      removed.maxConcurrency = 0;
      removed.connection = null;
      removed.capacity = 0;
      PoolWaiter<C> waiter = pool.waiters.poll();
      if (waiter != null) {
        Slot<C> slot = new Slot<>(pool, waiter.context, removed.index, waiter.capacity);
        pool.capacity -= w;
        pool.capacity += waiter.capacity;
        pool.slots[removed.index] = slot;
        pool.requests++;
        return () -> {
          if (waiter.listener != null) {
            waiter.listener.onConnect(waiter);
          }
          pool.connect(slot, waiter);
        };
      } else if (pool.size > 1) {
        Slot<C> tmp = pool.slots[pool.size - 1];
        tmp.index = removed.index;
        pool.slots[removed.index] = tmp;
        pool.slots[pool.size - 1] = null;
        pool.size--;
        pool.capacity -= w;
        return null;
      } else {
        pool.slots[0] = null;
        pool.size--;
        pool.capacity -= w;
        return null;
      }
    }
  }

  private static class SetConcurrency<C> implements Executor.Action<SimpleConnectionPool<C>> {

    private final Slot<C> slot;
    private final long concurrency;

    SetConcurrency(Slot<C> slot, long concurrency) {
      this.slot = slot;
      this.concurrency = concurrency;
    }

    @Override
    public Runnable execute(SimpleConnectionPool<C> pool) {
      if (slot.connection != null) {
        if (slot.maxConcurrency < concurrency) {
          long diff = concurrency - slot.maxConcurrency;
          slot.concurrency += diff;
          slot.maxConcurrency += diff;
          LeaseImpl<C>[] extra;
          int m = Math.min(slot.concurrency, pool.waiters.size());
          if (m > 0) {
            extra = new LeaseImpl[m];
            for (int i = 0;i < m;i++) {
              extra[i] = new LeaseImpl<>(slot, pool.waiters.poll().handler);
            }
            slot.concurrency -= m;
            return () -> {
              for (LeaseImpl<C> lease : extra) {
                lease.emit();
              }
            };
          } else {
            return null;
          }
        } else {
          throw new UnsupportedOperationException("Not yet implemented");
        }
      } else {
        return null;
      }
    }
  }

  private void setConcurrency(Slot<C> slot, long concurrency) {
    execute(new SetConcurrency<>(slot, concurrency));
  }

  private void remove(Slot<C> removed) {
    execute(new Remove<>(removed));
  }

  private static class Evict<C> implements Executor.Action<SimpleConnectionPool<C>> {

    private final Predicate<C> predicate;
    private final Handler<AsyncResult<List<C>>> handler;

    public Evict(Predicate<C> predicate, Handler<AsyncResult<List<C>>> handler) {
      this.predicate = predicate;
      this.handler = handler;
    }

    @Override
    public Runnable execute(SimpleConnectionPool<C> pool) {
      if (pool.closed) {
        return () -> handler.handle(POOL_CLOSED);
      }
      List<C> res = new ArrayList<>();
      List<Slot<C>> removed = new ArrayList<>();
      for (int i = pool.size - 1;i >= 0;i--) {
        Slot<C> slot = pool.slots[i];
        if (slot.connection != null && slot.concurrency == slot.maxConcurrency && predicate.test(slot.connection)) {
          removed.add(slot);
          res.add(slot.connection);
        }
      }
      for (Slot<C> slot : removed) {
        pool.remove(slot);
      }
      return () -> handler.handle(Future.succeededFuture(res));
    }
  }

  @Override
  public void evict(Predicate<C> predicate, Handler<AsyncResult<List<C>>> handler) {
    execute(new Evict<>(predicate, handler));
  }

  private static class Acquire<C> extends PoolWaiter<C> implements Executor.Action<SimpleConnectionPool<C>> {

    public Acquire(EventLoopContext context, PoolWaiter.Listener<C> listener, int capacity, Handler<AsyncResult<Lease<C>>> handler) {
      super(listener, context, capacity, handler);
    }

    @Override
    public Runnable execute(SimpleConnectionPool<C> pool) {
      if (pool.closed) {
        return () -> context.emit(POOL_CLOSED, handler);
      }

      // 1. Try reuse a existing connection with the same context
      Slot<C> slot1 = (Slot<C>) pool.selector.apply(this, pool.list);
      if (slot1 != null) {
        slot1.concurrency--;
        return () -> {
          new LeaseImpl<>(slot1, handler).emit();
        };
      }

      // 2. Try create connection
      if (pool.capacity < pool.maxCapacity) {
        pool.capacity += capacity;
        Slot<C> slot2 = new Slot<>(pool, context, pool.size, capacity);
        pool.slots[pool.size++] = slot2;
        pool.requests++;
        return () -> {
          if (listener != null) {
            listener.onConnect(this);
          }
          pool.connect(slot2, this);
        };
      }

      // 3. Try use another context
      Slot<C> slot3 = (Slot<C>) pool.fallbackSelector.apply(this, pool.list);
      if (slot3 != null) {
        slot3.concurrency--;
        return () -> {
          new LeaseImpl<>(slot3, handler).emit();
        };
      }

      // 4. Fall in waiters list
      if (pool.maxWaiters == -1 || (pool.waiters.size() + pool.requests) < pool.maxWaiters) {
        pool.waiters.addLast(this);
        return () -> {
          if (listener != null) {
            listener.onEnqueue(this);
          }
        };
      } else {
        return () -> context.emit(Future.failedFuture(new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + pool.maxWaiters)), handler);
      }
    }
  }

  @Override
  public void acquire(EventLoopContext context, PoolWaiter.Listener<C> listener, int kind, Handler<AsyncResult<Lease<C>>> handler) {
    execute(new Acquire<>(context, listener, capacityFactors[kind], handler));
  }

  public void acquire(EventLoopContext context, int kind, Handler<AsyncResult<Lease<C>>> handler) {
    acquire(context, PoolWaiter.NULL_LISTENER, kind, handler);
  }

  @Override
  public void cancel(PoolWaiter<C> waiter, Handler<AsyncResult<Boolean>> handler) {
    execute(new Cancel<>(waiter, handler));
  }

  private static class Cancel<C> implements Executor.Action<SimpleConnectionPool<C>>, Runnable {

    private final PoolWaiter<C> waiter;
    private final Handler<AsyncResult<Boolean>> handler;
    private boolean cancelled;

    public Cancel(PoolWaiter<C> waiter, Handler<AsyncResult<Boolean>> handler) {
      this.waiter = waiter;
      this.handler = handler;
    }

    @Override
    public Runnable execute(SimpleConnectionPool<C> pool) {
      if (pool.closed) {
        return () -> handler.handle(POOL_CLOSED);
      }
      if (pool.waiters.removeFirst(waiter)) {
        cancelled = true;
      } else if (!waiter.disposed) {
        waiter.disposed = true;
        cancelled = true;
      } else {
        cancelled = false;
      }
      return this;
    }

    @Override
    public void run() {
      handler.handle(Future.succeededFuture(cancelled));
    }
  }

  static class LeaseImpl<C> implements Lease<C> {

    private final Handler<AsyncResult<Lease<C>>> handler;
    private final Slot<C> slot;
    private final C connection;
    private boolean recycled;

    public LeaseImpl(Slot<C> slot, Handler<AsyncResult<Lease<C>>> handler) {
      this.handler = handler;
      this.slot = slot;
      this.connection = slot.connection;
    }

    @Override
    public C get() {
      return connection;
    }

    @Override
    public void recycle() {
      slot.pool.recycle(this);
    }

    void emit() {
      slot.context.emit(Future.succeededFuture(new LeaseImpl<>(slot, handler)), handler);
    }
  }

  private static class Recycle<C> implements Executor.Action<SimpleConnectionPool<C>> {

    private final Slot<C> slot;

    public Recycle(Slot<C> slot) {
      this.slot = slot;
    }

    @Override
    public Runnable execute(SimpleConnectionPool<C> pool) {
      if (!pool.closed && slot.connection != null) {
        if (pool.waiters.size() > 0) {
          PoolWaiter<C> waiter = pool.waiters.poll();
          return () -> new LeaseImpl<>(slot, waiter.handler).emit();
        } else {
          slot.concurrency++;
        }
      }
      return null;
    }
  }

  private void recycle(LeaseImpl<C> lease) {
    if (lease.recycled) {
      throw new IllegalStateException("Attempt to recycle more than permitted");
    }
    lease.recycled = true;
    execute(new Recycle<>(lease.slot));
  }

  public int waiters() {
    return waiters.size();
  }

  public int capacity() {
    return capacity;
  }

  @Override
  public int requests() {
    return requests;
  }

  private static class Close<C> implements Executor.Action<SimpleConnectionPool<C>> {

    private final Handler<AsyncResult<List<Future<C>>>> handler;

    private Close(Handler<AsyncResult<List<Future<C>>>> handler) {
      this.handler = handler;
    }

    @Override
    public Runnable execute(SimpleConnectionPool<C> pool) {
      if (pool.closed) {
        return () -> handler.handle(POOL_CLOSED);
      }
      pool.closed = true;
      List<PoolWaiter<C>> waiters = pool.waiters.clear();
      List<Future<C>> list = new ArrayList<>();
      for (int i = 0;i < pool.size;i++) {
        Slot<C> slot = pool.slots[i];
        pool.slots[i] = null;
        if (slot.initiator != null) {
          waiters.add(slot.initiator);
          slot.initiator = null;
        }
        pool.capacity -= slot.capacity;
        list.add(slot.result.future());
      }
      pool.size = 0;
      return () -> {
        waiters.forEach(w -> w.context.emit(POOL_CLOSED, w.handler));
        handler.handle(Future.succeededFuture(list));
      };
    }
  }

  @Override
  public void close(Handler<AsyncResult<List<Future<C>>>> handler) {
    execute(new Close<>(handler));
  }

  private static class Waiters<C> implements Iterable<PoolWaiter<C>> {

    private final PoolWaiter<C> head;
    private int size;

    public Waiters() {
      head = new PoolWaiter<>(null, null, 0, null);
      head.next = head.prev = head;
    }

    PoolWaiter<C> poll() {
      if (head.next == head) {
        return null;
      }
      PoolWaiter<C> node = head.next;
      removeFirst(node);
      return node;
    }

    void addLast(PoolWaiter<C> node) {
      node.prev = head.prev;
      node.next = head;
      head.prev.next = node;
      head.prev = node;
      size++;
    }

    void addFirst(PoolWaiter<C> node) {
      node.prev = head;
      node.next = head.prev;
      head.next.prev = node;
      head.next = node;
      size++;
    }

    boolean removeFirst(PoolWaiter<C> node) {
      if (node.next == null) {
        return false;
      }
      node.next.prev = node.prev;
      node.prev.next = node.next;
      size--;
      return true;
    }

    List<PoolWaiter<C>> clear() {
      List<PoolWaiter<C>> lst = new ArrayList<>(size);
      this.forEach(lst::add);
      size = 0;
      head.next = head.prev = head;
      return lst;
    }

    int size() {
      return size;
    }

    @Override
    public Iterator<PoolWaiter<C>> iterator() {
      return new Iterator<PoolWaiter<C>>() {
        PoolWaiter<C> current = head;
        @Override
        public boolean hasNext() {
          return current.next != head;
        }
        @Override
        public PoolWaiter<C> next() {
          if (current.next == head) {
            throw new NoSuchElementException();
          }
          try {
            return current.next;
          } finally {
            current = current.next;
          }
        }
      };
    }
  }

  class ListImpl extends AbstractList<PoolConnection<C>> {
    @Override
    public PoolConnection<C> get(int index) {
      return slots[index];
    }
    @Override
    public int size() {
      return size;
    }
  }
}
