/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.pool;

import io.vertx.core.*;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.internal.ContextInternal;

import java.time.Duration;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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
 * Connections can be evicted from the pool with {@link ConnectionPool#evict(Predicate)}. It
 * can be used to implement keep alive timeout.
 *
 * <h3>Waiter lifecycle</h3>
 *
 * Connection requests are done with {@link ConnectionPool#acquire(ContextInternal, int)}. Such request
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
 * to get a reference on the waiter and later use {@link #cancel(PoolWaiter)} to cancel
 * a request.
 */
public class DefaultConnectionPool<C> implements ConnectionPool<C> {

  private static final VertxException POOL_CLOSED_EXCEPTION = new VertxException("Pool closed", true);

  /**
   * Select the first available available connection with the same event loop.
   */
  private static final BiFunction<PoolWaiter, List<PoolConnection>, PoolConnection> SAME_EVENT_LOOP_SELECTOR = (waiter, list) -> {
    int size = list.size();
    for (int i = 0;i < size;i++) {
      PoolConnection slot = list.get(i);
      if (slot.context().nettyEventLoop() == waiter.context().nettyEventLoop() && slot.available() > 0) {
        return slot;
      }
    }
    return null;
  };

  /**
   * Select the first available connection.
   */
  private static final BiFunction<PoolWaiter, List<PoolConnection>, PoolConnection> FIRST_AVAILABLE_SELECTOR = (waiter, list) -> {
    int size = list.size();
    for (int i = 0;i < size;i++) {
      PoolConnection slot = list.get(i);
      if (slot.available() > 0) {
        return slot;
      }
    }
    return null;
  };

  /**
   * A slot for a connection.
   */
  static class Slot<C> implements PoolConnector.Listener, PoolConnection<C> {

    private final DefaultConnectionPool<C> pool;
    private final ContextInternal context;
    private Promise<C> result;
    private C connection;    // The actual connection, might be null
    private int index;       // The index in the pool slots array
    private int usage;    // The number of times this connection is acquired
    private long concurrency; // The total number of times the connection can be acquired
    private int capacity;      // The connection capacity

    public Slot(DefaultConnectionPool<C> pool, ContextInternal context, int index, int capacity) {
      this.pool = pool;
      this.context = context;
      this.connection = null;
      this.usage = 0;
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
    public ContextInternal context() {
      return context;
    }

    @Override
    public C get() {
      return connection;
    }

    @Override
    public int usage() {
      return usage;
    }

    @Override
    public long available() {
      return concurrency - usage;
    }

    @Override
    public long concurrency() {
      return concurrency;
    }
  }

  private final PoolConnector<C> connector;
  private final int maxWaiters;
  private final int maxCapacity;
  private final int[] capacityFactors;
  private final Executor<DefaultConnectionPool<C>> sync;
  private final ListImpl list = new ListImpl();

  // Whether the pool is closed
  private boolean closed;

  // Selectors
  private BiFunction<PoolWaiter<C>, List<PoolConnection<C>>, PoolConnection<C>> selector;
  private Function<ContextInternal, ContextInternal> contextProvider;
  private BiFunction<PoolWaiter<C>, List<PoolConnection<C>>, PoolConnection<C>> fallbackSelector;

  // Connection state
  private final Slot<C>[] slots;    // The pool connections, this array is not sparse
  private int size;                 // The number of non null slots
  private int capacity;             // The pool capacity

  // The submitted waiters, not yet acknowledged
  private final Waiters<C> submitted;

  // The waiters acknowledged in the wait queue
  private final Waiters<C> waiters;

  // Scratch structures of the progress, allocated once and reused between runs: the executor
  // guarantees a single progress at a time
  private final TaskList tasks;
  private final List<PoolWaiter<C>> admitted;

  private int connecting;

  // The recorded connect failure
  private Throwable failureCause;

  // The number of connect successes since the pool creation
  private long connectCount;

  // The reconnection strategy: no connect attempt before the suspension deadline, cleared at the
  // round boundary when it expired or when a connect succeeded since it was armed
  private final long retryDelayNanos;
  private long suspendedUntilNanos;
  private long suspendedConnectCount;

  // The time of the execution round, captured by its first progress: the round reasons
  // against a single timestamp, assuming tasks execute quickly
  private long roundNanos;

  // The minimum capacity, computed from the minimum sizes: missing connections are created at
  // the end of each execution round with the supplied connection properties, paced by the
  // reconnection strategy
  private final int minCapacity;
  private final int defaultKind;
  private final Supplier<ContextInternal> contextSupplier;

  // Whether the next progress is the first of the execution round
  private boolean first = true;

  public static <C> DefaultConnectionPool<C> pool(PoolConnector<C> connector, int[] maxSizes) {
    return new DefaultConnectionPool<>(connector, maxSizes);
  }

  public static <C> DefaultConnectionPool<C> pool(PoolConnector<C> connector, int[] maxSizes, int maxWaiters) {
    return new DefaultConnectionPool<>(connector, maxSizes, maxWaiters, null, null, 0, null);
  }

  public static <C> Builder<C> builder(PoolConnector<C> connector) {
    return new Builder<>(connector);
  }

  public static class Builder<C> {

    private final PoolConnector<C> connector;
    private int[] maxSizes;
    private int maxWaiters = -1;
    private Duration connectRetryDelay;
    private int[] minSizes;
    private int defaultKind;
    private Supplier<ContextInternal> contextSupplier;

    Builder(PoolConnector<C> connector) {
      this.connector = Objects.requireNonNull(connector);
    }

    public Builder<C> withMaxSizes(int[] maxSizes) {
      this.maxSizes = maxSizes;
      return this;
    }

    public Builder<C> withMaxWaiters(int maxWaiters) {
      this.maxWaiters = maxWaiters;
      return this;
    }

    public Builder<C> withConnectRetryDelay(Duration connectRetryDelay) {
      this.connectRetryDelay = connectRetryDelay;
      return this;
    }

    public Builder<C> withMinSizes(int[] minSizes) {
      this.minSizes = minSizes;
      return this;
    }

    public Builder<C> withDefaultKind(int defaultKind) {
      this.defaultKind = defaultKind;
      return this;
    }

    public Builder<C> withContextSupplier(Supplier<ContextInternal> contextSupplier) {
      this.contextSupplier = contextSupplier;
      return this;
    }

    public DefaultConnectionPool<C> build() {
      return new DefaultConnectionPool<>(connector, Objects.requireNonNull(maxSizes), maxWaiters, connectRetryDelay, minSizes, defaultKind, contextSupplier);
    }
  }

  DefaultConnectionPool(PoolConnector<C> connector, int[] maxSizes) {
    this(connector, maxSizes, -1, null, null, 0, null);
  }

  DefaultConnectionPool(PoolConnector<C> connector, int[] maxSizes, int maxWaiters, Duration connectRetryDelay,
                        int[] minSizes, int defaultKind, Supplier<ContextInternal> contextSupplier) {

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
    int minCapacity = 0;
    if (minSizes != null) {
      if (minSizes.length != maxSizes.length) {
        throw new IllegalArgumentException("Minimum sizes must have one size per kind, like the maximum sizes");
      }
      for (int i = 0;i < minSizes.length;i++) {
        if (minSizes[i] < 0 || minSizes[i] > maxSizes[i]) {
          throw new IllegalArgumentException("Minimum size must be between 0 and the maximum size of its kind");
        }
        minCapacity = Math.max(minCapacity, minSizes[i] * capacities[i]);
      }
    }
    if (defaultKind < 0 || defaultKind >= maxSizes.length) {
      throw new IllegalArgumentException("Default kind must be an index in the maximum sizes");
    }
    if (minCapacity > 0) {
      if (contextSupplier == null) {
        throw new IllegalArgumentException("A minimum pool size requires a context supplier");
      }
      if (connectRetryDelay == null) {
        throw new IllegalArgumentException("A minimum pool size requires a connect retry delay");
      }
    }

    this.capacityFactors = capacities;
    this.connector = connector;
    this.slots = new Slot[numSlots];
    this.size = 0;
    this.maxWaiters = maxWaiters;
    this.capacity = 0;
    this.maxCapacity = maxCapacity;
    this.sync = new CombinerExecutor<>(this, this::finalAction);
    this.selector = (BiFunction) SAME_EVENT_LOOP_SELECTOR;
    this.fallbackSelector = (BiFunction) FIRST_AVAILABLE_SELECTOR;
    this.contextProvider = EVENT_LOOP_CONTEXT_PROVIDER;
    this.submitted = new Waiters<>();
    this.waiters = new Waiters<>();
    this.tasks = new TaskList();
    this.admitted = new ArrayList<>();
    this.retryDelayNanos = connectRetryDelay == null ? 0L : connectRetryDelay.toNanos();
    this.minCapacity = minCapacity;
    this.defaultKind = defaultKind;
    this.contextSupplier = contextSupplier;
  }

  @Override
  public ConnectionPool<C> connectionSelector(BiFunction<PoolWaiter<C>, List<PoolConnection<C>>, PoolConnection<C>> selector) {
    this.selector = selector;
    return this;
  }

  @Override
  public ConnectionPool<C> contextProvider(Function<ContextInternal, ContextInternal> contextProvider) {
    this.contextProvider = contextProvider;
    return this;
  }

  private void execute(Executor.Action<DefaultConnectionPool<C>> action) {
    sync.submit(action);
  }

  private Executor.Action<DefaultConnectionPool<C>> finalAction() {
    return first ? null : new Cleanup<>();
  }

  private static class Cleanup<C> implements Executor.Action<DefaultConnectionPool<C>> {

    @Override
    public Task execute(DefaultConnectionPool<C> pool) {
      Task head = pool.last();
      pool.first = true;
      return head;
    }
  }

  private void removeSlot(Slot<C> slot) {
    int w = slot.capacity;
    slot.usage = 0;
    slot.concurrency = 0;
    slot.connection = null;
    slot.capacity = 0;
    if (size > 1) {
      Slot<C> tmp = slots[size - 1];
      tmp.index = slot.index;
      slots[slot.index] = tmp;
      slots[size - 1] = null;
    } else {
      slots[0] = null;
    }
    size--;
    capacity -= w;
  }

  private boolean isWaitersQueueFull() {
    return maxWaiters != -1 && waiters.size() >= maxWaiters;
  }

  private Task progress() {
    progress(tasks);
    Task head = tasks.first();
    tasks.clear();
    return head;
  }

  private void progress(TaskList tasks) {

    // On the first progress of the execution round, evict the timed out waiters and the stale
    // connections before any demand is served: a connection that is not valid is expired
    if (first) {
      first = false;
      roundNanos = System.nanoTime();
      if (suspendedUntilNanos != 0 && (roundNanos - suspendedUntilNanos >= 0 || connectCount > suspendedConnectCount)) {
        // The suspension of the reconnection strategy is over or a connect succeeded since it was armed
        suspendedUntilNanos = 0;
      }
      PoolWaiter<C> current = waiters.peek();
      while (current != null) {
        PoolWaiter<C> waiter = current;
        current = waiters.next(current);
        if (waiter.deadlineNanos != 0 && roundNanos - waiter.deadlineNanos >= 0) {
          waiters.remove(waiter);
          Task task = new Task() {
            @Override
            public void run() {
              waiter.listener.onTimeout(waiter);
            }
          };
          tasks.append(task);
        }
      }
      for (int i = size - 1;i >= 0;i--) {
        Slot<C> slot = slots[i];
        if (slot.connection != null && slot.usage == 0 && !connector.isValid(slot.connection)) {
          C connection = slot.connection;
          removeSlot(slot);
          Task task = new Task() {
            @Override
            public void run() {
              connector.close(connection);
            }
          };
          tasks.append(task);
        }
      }
    }

    // Connect failure policy: fail one pending acquisition per recorded failure, alternative
    // policies (bounded retry, fail-fast, ...) plug in here.
    // Under persistent failures this signals every waiter: each failure fails the head while the
    // loop below connects for the next one, until the wait queue is empty. To reconsider if the
    // pool gets time based eviction of waiters, guaranteeing the signalling instead
    if (failureCause != null) {
      Throwable cause = failureCause;
      failureCause = null;
      if (retryDelayNanos > 0) {
        suspendedUntilNanos = roundNanos + retryDelayNanos;
        suspendedConnectCount = connectCount;
      }
      PoolWaiter<C> waiter = waiters.poll();
      if (waiter != null) {
        waiter.disposed = true;
        Task task = new Task() {
          @Override
          public void run() {
            waiter.handler.fail(cause);
          }
        };
        tasks.append(task);
      }
    }


    // Satisfy the demand: peek from the wait queue, when it is exhausted peek from the submitted
    // acquisitions, stop at the first demand that cannot be satisfied; the first connecting waiters
    // are covered by in-flight connects: they can be granted but no connection is created for them.
    // The reconnection strategy suspends connect attempts after a failure
    boolean suspended = suspendedUntilNanos != 0;
    int pos = 0;
    PoolWaiter<C> next = waiters.peek();
    while (true) {
      boolean submission;
      PoolWaiter<C> waiter;
      if (next != null) {
        submission = false;
        waiter = next;
        next = waiters.next(next);
      } else if ((waiter = submitted.poll()) != null) {
        // Admit a submission in the wait queue, the bound is enforced only when it remains waiting:
        // a submission served by a grant or a connect is exempted, like the existing pool
        submission = true;
        waiters.addLast(waiter);
        admitted.add(waiter);
      } else {
        break;
      }
      Task task = null;
      Slot<C> slot1;
      Slot<C> slot3;
      // 1. Try reuse a existing connection with the same context
      if ((slot1 = (Slot<C>) selector.apply(waiter, list)) != null) {
        waiters.remove(waiter);
        waiter.disposed = true;
        slot1.usage++;
        LeaseImpl<C> lease = new LeaseImpl<>(slot1, waiter.handler);
        task = new Task() {
          @Override
          public void run() {
            lease.emit();
          }
        };
      } else if (pos >= connecting && capacity < maxCapacity && !suspended) {
        // 2. Try create connection, the waiter joins the covered prefix of the wait queue
        capacity += waiter.capacity;
        ContextInternal connectionContext = contextProvider.apply(waiter.context);
        Slot<C> slot2 = new Slot<>(this, connectionContext, size, waiter.capacity);
        slots[size++] = slot2;
        connecting++;
        pos++;
        task = new Task() {
          @Override
          public void run() {
            if (waiter.listener != null) {
              waiter.listener.onConnect(waiter);
            }
            connect(slot2);
          }
        };
      } else if ((slot3 = (Slot<C>) fallbackSelector.apply(waiter, list)) != null) {
        // 3. Try use another context
        waiters.remove(waiter);
        waiter.disposed = true;
        slot3.usage++;
        LeaseImpl<C> lease = new LeaseImpl<>(slot3, waiter.handler);
        task = new Task() {
          @Override
          public void run() {
            lease.emit();
          }
        };
      } else if (pos < connecting) {
        // 4. Covered by an in-flight connect, wait for it
        pos++;
      } else if (submission) {
        // 5. The submission remains waiting, reject it when the wait queue exceeds the bound
        if (maxWaiters != -1 && waiters.size() > maxWaiters) {
          waiters.remove(waiter);
          task = new Task() {
            @Override
            public void run() {
              Future<Lease<C>> fut = waiter.context.failedFuture(new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + maxWaiters));
              fut.onComplete(waiter.handler);
            }
          };
        } else {
          pos++;
        }
      } else {
        // 6. No satisfaction is possible anymore
        break;
      }
      if (task != null) {
        tasks.append(task);
      }
    }


    // Move the remaining submissions to the wait queue, reject them when it is full
    for (PoolWaiter<C> extra = submitted.poll();extra != null;extra = submitted.poll()) {
      if (!isWaitersQueueFull()) {
        waiters.addLast(extra);
        admitted.add(extra);
      } else {
        PoolWaiter<C> rejected = extra;
        Task task = new Task() {
          @Override
          public void run() {
            Future<Lease<C>> fut = rejected.context.failedFuture(new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + maxWaiters));
            fut.onComplete(rejected.handler);
          }
        };
        tasks.append(task);
      }
    }


    // Notify the admitted submissions still waiting
    for (PoolWaiter<C> waiter : admitted) {
      if (waiter.next != null && waiter.listener != null) {
        Task task = new Task() {
          @Override
          public void run() {
            waiter.listener.onEnqueue(waiter);
          }
        };
        tasks.append(task);
      }
    }
    admitted.clear();


    // Signal the connect results
    for (int i = 0;i < size;i++) {
      Slot<C> slot = slots[i];
      if (slot.result != null && slot.connection != null) {
        Promise<C> result = slot.result;
        C connection = slot.connection;
        slot.result = null;
        Task task = new Task() {
          @Override
          public void run() {
            result.complete(connection);
          }
        };
        tasks.append(task);
      }
    }
  }

  private Task last() {
    // Satisfy the minimum capacity after all demand is served, creating one connection at a
    // time of the default kind with the supplied context, when no connect is in flight: the
    // actual weight of a connection is known on connect success only, connecting one at a time
    // avoids overshooting the capacity. A floor connect counts as connecting, covering
    // waiters arriving while it is in flight. The reconnection strategy paces the floor retries
    boolean suspended = suspendedUntilNanos != 0;
    if (!closed && !suspended && connecting == 0 && capacity < minCapacity) {
      int floorCapacity = capacityFactors[defaultKind];
      capacity += floorCapacity;
      ContextInternal connectionContext = contextProvider.apply(contextSupplier.get());
      Slot<C> slot = new Slot<>(this, connectionContext, size, floorCapacity);
      slots[size++] = slot;
      connecting++;
      return new Task() {
        @Override
        public void run() {
          connect(slot);
        }
      };
    }
    return null;
  }

  public int size() {
      return size;
  }

  private void connect(Slot<C> slot) {
    connector.connect(slot.context, slot).onComplete(ar -> {
      if (ar.succeeded()) {
        execute(new ConnectSuccess<>(slot, ar.result()));
      } else {
        execute(new ConnectFailed<>(slot, ar.cause()));
      }
    });
  }

  private static class ConnectSuccess<C> implements Executor.Action<DefaultConnectionPool<C>> {

    private final Slot<C> slot;
    private final ConnectResult<C> result;

    private ConnectSuccess(Slot<C> slot, ConnectResult<C> result) {
      this.slot = slot;
      this.result = result;
    }

    @Override
    public Task execute(DefaultConnectionPool<C> pool) {

      int capacity = pool.capacityFactors[(int)result.weight()];

      int initialCapacity = slot.capacity;
      slot.connection = result.connection();
      slot.concurrency = result.concurrency();
      slot.capacity = capacity;
      slot.usage = 0;
      pool.connecting--;
      pool.capacity += (capacity - initialCapacity);
      pool.connectCount++;
      if (pool.closed) {
        return new Task() {
          @Override
          public void run() {
            slot.result.complete(slot.connection);
          }
        };
      }
      return pool.progress();
    }
  }

  private static class ConnectFailed<C> implements Executor.Action<DefaultConnectionPool<C>> {

    private final Slot<C> removed;
    private final Throwable cause;

    public ConnectFailed(Slot<C> removed, Throwable cause) {
      this.removed = removed;
      this.cause = cause;
    }

    public Task execute(DefaultConnectionPool<C> pool) {
      pool.connecting--;
      Task task = new Task() {
        @Override
        public void run() {
          removed.result.fail(cause);
        }
      };
      if (pool.closed) {
        return task;
      }
      pool.removeSlot(removed);
      pool.failureCause = cause;
      Task head = pool.progress();
      if (head == null) {
        head = task;
      } else {
        head.last().next(task);
      }
      return head;
    }
  }

  private static class Remove<C> implements Executor.Action<DefaultConnectionPool<C>> {

    protected final Slot<C> removed;

    private Remove(Slot<C> removed) {
      this.removed = removed;
    }

    @Override
    public Task execute(DefaultConnectionPool<C> pool) {
      if (pool.closed || pool.slots[removed.index] != removed) {
        return null;
      }
      pool.removeSlot(removed);
      return pool.progress();
    }
  }

  private static class SetConcurrency<C> implements Executor.Action<DefaultConnectionPool<C>> {

    private final Slot<C> slot;
    private final long concurrency;

    SetConcurrency(Slot<C> slot, long concurrency) {
      this.slot = slot;
      this.concurrency = concurrency;
    }

    @Override
    public Task execute(DefaultConnectionPool<C> pool) {
      if (slot.connection != null) {
        slot.concurrency = concurrency;
        return pool.progress();
      }
      return null;
    }
  }

  private void setConcurrency(Slot<C> slot, long concurrency) {
    execute(new SetConcurrency<>(slot, concurrency));
  }

  private void remove(Slot<C> removed) {
    execute(new Remove<>(removed));
  }

  private static class Evict<C> implements Executor.Action<DefaultConnectionPool<C>> {

    private final Predicate<C> predicate;
    private final Completable<List<C>> handler;

    public Evict(Predicate<C> predicate, Completable<List<C>> handler) {
      this.predicate = predicate;
      this.handler = handler;
    }

    @Override
    public Task execute(DefaultConnectionPool<C> pool) {
      if (pool.closed) {
        return new Task() {
          @Override
          public void run() {
            handler.fail(POOL_CLOSED_EXCEPTION);
          }
        };
      }
      List<C> res = new ArrayList<>();
      for (int i = pool.size - 1;i >= 0;i--) {
        Slot<C> slot = pool.slots[i];
        if (slot.connection != null && slot.usage == 0 && predicate.test(slot.connection)) {
          res.add(slot.connection);
          pool.removeSlot(slot);
        }
      }
      TaskList tasks = new TaskList();
      tasks.append(new Task() {
        @Override
        public void run() {
          handler.succeed(res);
        }
      });
      Task next = pool.progress();
      if (next != null) {
        tasks.append(next);
      }
      return tasks.first();
    }
  }

  @Override
  public void evict(Predicate<C> predicate, Completable<List<C>> handler) {
    execute(new Evict<>(predicate, handler));
  }

  private static class Acquire<C> extends PoolWaiter<C> implements Executor.Action<DefaultConnectionPool<C>> {

    public Acquire(ContextInternal context, PoolWaiter.Listener<C> listener, int capacity, Duration timeout, Completable<Lease<C>> handler) {
      super(listener, context, capacity, handler);
      if (timeout != null) {
        deadlineNanos = System.nanoTime() + timeout.toNanos();
      }
    }

    @Override
    public Task execute(DefaultConnectionPool<C> pool) {
      if (pool.closed) {
        return new Task() {
          @Override
          public void run() {
            Future<Lease<C>> fut = context.failedFuture("Pool closed");
            fut.onComplete(handler);
          }
        };
      }
      pool.submitted.addLast(this);
      Task head = pool.progress();
      assert pool.submitted.size() == 0;
      return head;
    }
  }

  @Override
  public void refresh() {
    sync.trySubmit(new Refresh<>());
  }

  private static class Refresh<C> implements Executor.Action<DefaultConnectionPool<C>> {

    @Override
    public Task execute(DefaultConnectionPool<C> pool) {
      if (pool.closed) {
        return null;
      }
      return pool.progress();
    }
  }

  @Override
  public void acquire(ContextInternal context, int kind, Completable<Lease<C>> handler) {
    acquire(context, PoolWaiter.NULL_LISTENER, kind, null, handler);
  }

  @Override
  public void acquire(ContextInternal context, PoolWaiter.Listener<C> listener, int kind, Completable<Lease<C>> handler) {
    acquire(context, listener, kind, null, handler);
  }

  @Override
  public void acquire(ContextInternal context, int kind, Duration timeout, Completable<Lease<C>> handler) {
    acquire(context, PoolWaiter.NULL_LISTENER, kind, timeout, handler);
  }

  @Override
  public void acquire(ContextInternal context, PoolWaiter.Listener<C> listener, int kind, Duration timeout, Completable<Lease<C>> handler) {
    execute(new Acquire<>(context, listener, capacityFactors[kind], timeout, handler));
  }

  @Override
  public void cancel(PoolWaiter<C> waiter, Completable<Boolean> handler) {
    execute(new Cancel<>(waiter, handler));
  }

  private static class Cancel<C> extends Task implements Executor.Action<DefaultConnectionPool<C>> {

    private final PoolWaiter<C> waiter;
    private final Completable<Boolean> handler;
    private boolean cancelled;

    public Cancel(PoolWaiter<C> waiter, Completable<Boolean> handler) {
      this.waiter = waiter;
      this.handler = handler;
    }

    @Override
    public Task execute(DefaultConnectionPool<C> pool) {
      if (pool.closed) {
        return new Task() {
          @Override
          public void run() {
            handler.fail(POOL_CLOSED_EXCEPTION);
          }
        };
      }
      cancelled = pool.waiters.remove(waiter) || !waiter.disposed;
      waiter.disposed = true;
      return this;
    }

    @Override
    public void run() {
      handler.succeed(cancelled);
    }
  }

  static class LeaseImpl<C> implements Lease<C> {

    private final Completable<Lease<C>> handler;
    private final Slot<C> slot;
    private final C connection;
    private boolean recycled;

    public LeaseImpl(Slot<C> slot, Completable<Lease<C>> handler) {
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
      Future<Lease<C>> fut = slot.context.succeededFuture(this);
      fut.onComplete(handler);
    }
  }

  private static class Recycle<C> implements Executor.Action<DefaultConnectionPool<C>> {

    private final Slot<C> slot;

    public Recycle(Slot<C> slot) {
      this.slot = slot;
    }

    @Override
    public Task execute(DefaultConnectionPool<C> pool) {
      if (!pool.closed && slot.connection != null) {
        slot.usage--;
        return pool.progress();
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
    return Math.max(0, waiters.size() - connecting);
  }

  public int capacity() {
    return capacity;
  }

  @Override
  public int requests() {
    return connecting;
  }

  private static class Close<C> implements Executor.Action<DefaultConnectionPool<C>> {

    private final Completable<List<Future<C>>> handler;

    private Close(Completable<List<Future<C>>> handler) {
      this.handler = handler;
    }

    @Override
    public Task execute(DefaultConnectionPool<C> pool) {
      if (pool.closed) {
        return new Task() {
          @Override
          public void run() {
            handler.fail(POOL_CLOSED_EXCEPTION);
          }
        };
      }
      pool.closed = true;
      List<PoolWaiter<C>> waiters = pool.waiters.clear();
      List<Future<C>> list = new ArrayList<>();
      for (int i = 0;i < pool.size;i++) {
        Slot<C> slot = pool.slots[i];
        pool.slots[i] = null;
        pool.capacity -= slot.capacity;
        if (slot.result != null) {
          list.add(slot.result.future());
        } else {
          list.add(slot.context.succeededFuture(slot.connection));
        }
      }
      pool.size = 0;
      return new Task() {
        @Override
        public void run() {
          waiters.forEach(w -> {
            w.handler.fail(POOL_CLOSED_EXCEPTION);
          });
          handler.succeed(list);
        }
      };
    }
  }

  @Override
  public void close(Completable<List<Future<C>>> handler) {
    execute(new Close<>(handler));
  }

  private static class Waiters<C> implements Iterable<PoolWaiter<C>> {

    private final PoolWaiter<C> head;
    private int size;

    public Waiters() {
      head = new PoolWaiter<>(null, null, 0, null);
      head.next = head.prev = head;
    }

    PoolWaiter<C> peek() {
      return head.next == head ? null : head.next;
    }

    PoolWaiter<C> next(PoolWaiter<C> node) {
      return node.next == head ? null : node.next;
    }

    PoolWaiter<C> peekLast() {
      return head.prev == head ? null : head.prev;
    }

    PoolWaiter<C> poll() {
      if (head.next == head) {
        return null;
      }
      PoolWaiter<C> node = head.next;
      remove(node);
      return node;
    }

    void addLast(PoolWaiter<C> node) {
      if (node.next != null) {
        throw new IllegalStateException();
      }
      node.prev = head.prev;
      node.next = head;
      head.prev.next = node;
      head.prev = node;
      size++;
    }

    void addFirst(PoolWaiter<C> node) {
      if (node.next != null) {
        throw new IllegalStateException();
      }
      node.prev = head;
      node.next = head.next;
      head.next.prev = node;
      head.next = node;
      size++;
    }

    boolean remove(PoolWaiter<C> node) {
      if (node.next == null) {
        return false;
      }
      node.next.prev = node.prev;
      node.prev.next = node.next;
      node.next = node.prev = null;
      size--;
      return true;
    }

    List<PoolWaiter<C>> clear() {
      List<PoolWaiter<C>> lst = new ArrayList<>(size);
      this.forEach(lst::add);
      for (PoolWaiter<C> node : lst) {
        node.next = node.prev = null;
      }
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

  private static class TaskList {

    private Task head;
    private Task tail;

    void append(Task task) {
      if (head == null) {
        head = task;
      } else {
        tail.next(task);
      }
      tail = task;
    }

    Task first() {
      return head;
    }

    void clear() {
      head = null;
      tail = null;
    }
  }
}
