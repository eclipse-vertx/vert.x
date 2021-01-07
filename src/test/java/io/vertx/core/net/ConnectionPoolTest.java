/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import io.vertx.core.*;
import io.vertx.core.net.impl.clientconnection.ConnectResult;
import io.vertx.core.net.impl.clientconnection.ConnectionListener;
import io.vertx.core.net.impl.clientconnection.ConnectionProvider;
import io.vertx.core.net.impl.clientconnection.Lease;
import io.vertx.core.net.impl.clientconnection.Pool;
import io.vertx.core.impl.ContextInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ConnectionPoolTest extends VertxTestBase {

  class FakeConnectionManager {

    private final Context context = vertx.getOrCreateContext();
    private final ConnectionProvider<FakeConnection> connector;
    private final int queueMaxSize;
    private final int poolMaxSize;
    private Pool<FakeConnection> pool;
    private Set<FakeConnection> active = new HashSet<>();
    private boolean closed = true;
    private int closeCount;
    private int seq;
    private final boolean fifo;

    FakeConnectionManager(int queueMaxSize, int poolMaxSize, ConnectionProvider<FakeConnection> connector) {
      this(queueMaxSize, poolMaxSize, connector, false);
    }

    FakeConnectionManager(int queueMaxSize, int poolMaxSize, ConnectionProvider<FakeConnection> connector, boolean fifo) {
      this.queueMaxSize = queueMaxSize;
      this.poolMaxSize = poolMaxSize;
      this.connector = connector;
      this.fifo = fifo;
    }

    synchronized int sequence() {
      return seq;
    }

    synchronized boolean closed() {
      return closed;
    }

    synchronized boolean contains(FakeConnection conn) {
      return active.contains(conn);
    }

    synchronized int size() {
      return active.size();
    }

    void removeExpired() {
      pool.closeIdle();
    }

    synchronized Pool<FakeConnection> pool() {
      return pool;
    }

    synchronized int closeCount() {
      return closeCount;
    }

    void getConnection(FakeWaiter waiter) {
      synchronized (this) {
        if (closed) {
          seq++;
          closed = false;
          pool = new Pool<>(
            context,
            connector,
            queueMaxSize,
            1,
            poolMaxSize,
            conn -> {
            synchronized (FakeConnectionManager.this) {
              active.add(conn.get());
            }
          }, conn -> {
            synchronized (FakeConnectionManager.this) {
              active.remove(conn.get());
            }
          }, fifo
          );
        }
      }
      pool.getConnection(ar -> {
        if (ar.succeeded()) {
          waiter.handleConnection(ar.result());
        } else {
          waiter.handleFailure(ar.cause());
        }
      });
    }
  }

  @Test
  public void testConnectSuccess() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 4, connector);
    AtomicReference<Boolean> handleLock = new AtomicReference<>();
    FakeWaiter waiter = new FakeWaiter() {
      @Override
      protected void onSuccess(FakeConnection conn) {
        assertSame(conn.context, mgr.context);
        Pool<FakeConnection> pool = mgr.pool();
        handleLock.set(Thread.holdsLock(pool));
      }
    };
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter::isComplete);
    assertEquals(Boolean.FALSE, handleLock.get());
    waiter.assertSuccess(conn);
    waiter.recycle();
    assertEquals(1, mgr.size());
    // assertWaitUntil(() -> mgr.closed());
  }

  @Test
  public void testConnectFailure() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 4, connector);
    AtomicReference<Boolean> holdsLock = new AtomicReference<>();
    FakeWaiter waiter = new FakeWaiter() {
      @Override
      protected void onFailure() {
        assertNull(Vertx.currentContext());
        Pool<FakeConnection> pool = mgr.pool();
        holdsLock.set(Thread.holdsLock(pool));
      }
    };
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    Throwable failure = new Throwable();
    conn.fail(failure);
    assertWaitUntil(waiter::isComplete);
    assertEquals(Boolean.FALSE, holdsLock.get());
    waiter.assertFailure(failure);
  }

  @Test
  public void testConnectPoolEmptyWaiterCancelledAfterConnectRequest() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 3, connector);
    FakeWaiter waiter = new FakeWaiter();
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    waiter.cancel();
    conn.connect();
    assertWaitUntil(waiter::isComplete);
    assertFalse(waiter.isSuccess());
    assertFalse(waiter.isFailure());
    assertTrue(mgr.contains(conn));
  }

  @Test
  public void testConnectionFailure() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 3, connector);
    FakeWaiter waiter = new FakeWaiter();
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    Exception expected = new Exception();
    conn.fail(expected);
    assertWaitUntil(waiter::isComplete);
    waiter.assertFailure(expected);
    assertTrue(waiter.isFailure());
    // assertWaitUntil(mgr::closed);
  }

  @Test
  public void testSynchronousConnectionFailure() {
    Throwable cause = new Throwable();
    ConnectionProvider<FakeConnection> connector = new FakeConnectionProviderBase() {
      @Override
      public void connect(ConnectionListener<FakeConnection> listener, ContextInternal context, Handler<AsyncResult<ConnectResult<FakeConnection>>> handler) {
        handler.handle(Future.failedFuture(cause));
      }
    };
    FakeConnectionManager mgr = new FakeConnectionManager(3, 3, connector);
    for (int i = 0;i < 4;i++) {
      FakeWaiter waiter = new FakeWaiter();
      mgr.getConnection(waiter);
      waitUntil(waiter::isFailure);
      waiter.assertFailure(cause);
      assertEquals(0, mgr.pool().weight());
    }
    // assertTrue(mgr.closed());
  }

  @Test
  public void testRecycleConnection() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isComplete);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    connector.assertRequests(0);
    waiter1.recycle();
    assertWaitUntil(waiter2::isComplete);
    waiter2.assertSuccess(conn);
  }

  @Test
  public void testRecycleDiscardedConnection() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isComplete);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    conn.close();
    waiter1.recycle();
    assertWaitUntil(() -> connector.requests() == 1);
    assertFalse(mgr.closed());
    FakeConnection conn2 = connector.assertRequest();
    conn2.connect();
    assertWaitUntil(waiter2::isSuccess);
  }

  /*
  @Test
  public void testWaiterThrowsException() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 1, connector);
    Exception failure = new Exception();
    FakeWaiter waiter = new FakeWaiter() {
      @Override
      public synchronized boolean handleConnection(ContextInternal ctx, FakeConnection conn) throws Exception {
        throw failure;
      }
    };
    mgr.getConnection(waiter);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertEquals(0, mgr.size());
  }
  */

  @Ignore
  @Test
  public void testEndpointLifecycle() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isSuccess);
    conn.close();
    assertWaitUntil(mgr::closed);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    assertEquals(2, mgr.sequence());
  }

  @Test
  public void testDontCloseEndpointWithInflightRequest() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(3, 2, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isComplete);
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    conn.close();
    assertWaitUntil(() -> !mgr.contains(conn));
    assertFalse(mgr.closed());
  }

  @Test
  public void testInitialConcurrency() {
    int n = 10;
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 1, connector);
    List<FakeWaiter> waiters = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      FakeWaiter waiter = new FakeWaiter();
      mgr.getConnection(waiter);
      waiters.add(waiter);
    }
    FakeConnection conn = connector.assertRequest();
    conn.concurrency(n).connect();
    waiters.forEach(waiter -> {
      assertWaitUntil(waiter::isSuccess);
    });
    waiters.forEach(FakeWaiter::recycle);
  }

  @Test
  public void testInitialNoConcurrency() {
    int n = 10;
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 1, connector);
    List<FakeWaiter> waiters = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      FakeWaiter waiter = new FakeWaiter();
      mgr.getConnection(waiter);
      waiters.add(waiter);
    }
    FakeConnection conn = connector.assertRequest();
    conn.concurrency(0).connect().awaitConnected();
    conn.concurrency(n - 1);
    assertWaitUntil(() -> waiters.stream().filter(FakeWaiter::isSuccess).count() == n - 1);
    waiters.stream().filter(FakeWaiter::isSuccess).findFirst().get().recycle();
    waiters.forEach(waiter -> {
      assertWaitUntil(waiter::isSuccess);
    });
  }

  @Test
  public void testRecycleWithoutDispose() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isSuccess);
    waiter1.recycle();
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    assertWaitUntil(waiter2::isSuccess);
    waiter2.assertSuccess(conn);
    waiter2.recycle();
    conn.valid(false);
    mgr.removeExpired();
    assertWaitUntil(() -> mgr.size() == 0);
  }

  @Test
  public void testRecycleFIFO() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 2, connector, true);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection firstInConnection = connector.assertRequest();
    firstInConnection.connect();
    assertWaitUntil(waiter1::isSuccess);

    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    FakeConnection lastInConnection = connector.assertRequest();
    lastInConnection.connect();
    assertWaitUntil(waiter2::isSuccess);
    waiter2.assertSuccess(lastInConnection);
    waiter1.recycle();
    waiter2.recycle();
    assertEquals(2, mgr.size());

    FakeWaiter waiter3 = new FakeWaiter();
    mgr.getConnection(waiter3);
    assertWaitUntil(waiter3::isSuccess);
    waiter3.assertSuccess(firstInConnection);
  }

  @Test
  public void testRecycleLIFO() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 2, connector, false);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection firstInConnection = connector.assertRequest();
    firstInConnection.connect();
    assertWaitUntil(waiter1::isSuccess);

    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    FakeConnection lastInConnection = connector.assertRequest();
    lastInConnection.connect();
    assertWaitUntil(waiter2::isSuccess);
    waiter2.assertSuccess(lastInConnection);
    waiter1.recycle();
    waiter2.recycle();
    assertEquals(2, mgr.size());

    FakeWaiter waiter3 = new FakeWaiter();
    mgr.getConnection(waiter3);
    assertWaitUntil(waiter3::isSuccess);
    waiter3.assertSuccess(lastInConnection);
  }

  @Test
  public void testDiscardWaiterWhenFull() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(2, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    FakeWaiter waiter2 = new FakeWaiter();
    mgr.getConnection(waiter2);
    FakeWaiter waiter3 = new FakeWaiter();
    mgr.getConnection(waiter3);
    FakeWaiter waiter4 = new FakeWaiter();
    mgr.getConnection(waiter4);
    assertWaitUntil(waiter4::isFailure); // Full
  }

  /*
  @Test
  public void testDiscardConnectionDuringInit() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(2, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter() {
      @Override
      public synchronized void initConnection(ContextInternal ctx, FakeConnection conn) {
        super.initConnection(ctx, conn);
        conn.close(); // Close during init
      }
    };
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(() -> connector.requests() == 1); // Connection close during init - reattempt to connect
    assertFalse(mgr.closed());
  }
  */

  @Test
  public void testDiscardExpiredConnections() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(2, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isSuccess);
    waiter1.recycle();
    assertEquals(1, mgr.size());
    mgr.removeExpired();
    assertWaitUntil(() -> mgr.size() == 1);
    conn.valid(false);
    mgr.removeExpired();
    waitUntil(() -> mgr.size() == 0);
    assertEquals(0, mgr.size());
  }

  @Test
  public void testCloseRecycledConnection() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(2, 1, connector);
    FakeWaiter waiter1 = new FakeWaiter();
    mgr.getConnection(waiter1);
    FakeConnection conn = connector.assertRequest();
    conn.connect();
    assertWaitUntil(waiter1::isSuccess);
    waiter1.recycle();
    FakeWaiter waiter2 = new FakeWaiter();
    // Recycle connection
    mgr.getConnection(waiter2);
    // Then close it
    conn.close();
    // Either the waiter acquires the recycled connection before it's closed
    // Or a connection request happens
    assertWaitUntil(() -> waiter2.isComplete() || connector.hasRequests());
  }

  @Test
  public void testQueueMaxSize() {
    checkQueueMaxSize(2, 3);
    checkQueueMaxSize(0, 3);
  }

  @Test
  public void testRecycleInCallback() {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 2, connector);
    FakeWaiter waiter1 = new FakeWaiter() {
      @Override
      protected void onSuccess(FakeConnection conn) {
        conn.valid(false);
        recycle();
      }
    };
    FakeWaiter waiter2 = new FakeWaiter() {
      @Override
      protected void onSuccess(FakeConnection conn) {
        conn.valid(false);
        recycle();
      }
    };
    mgr.getConnection(waiter1);
    mgr.getConnection(waiter2);
    FakeConnection req1 = connector.assertRequest();
    FakeConnection req2 = connector.assertRequest();
    assertNotSame(req1, req2);
    req1.connect();
    req2.connect();
    assertWaitUntil(waiter1::isSuccess);
    assertWaitUntil(waiter2::isSuccess);
    // assertWaitUntil(mgr::closed);
  }

  @Test
  public void testConnectionInitializer() {
    AtomicReference<FakeConnection> ref = new AtomicReference<>();
    FakeConnectionProvider connector = new FakeConnectionProvider() {
      @Override
      public void init(FakeConnection conn) {
        ref.set(conn);
      }
    };
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 2, connector);
    FakeWaiter waiter = new FakeWaiter();
    mgr.getConnection(waiter);
    FakeConnection req = connector.assertRequest();
    req.connect();
    waitUntil(() -> ref.get() != null);
    waitUntil(waiter::isSuccess);
  }

  @Test
  public void testCloseConnectionIniInitializer() {
    AtomicInteger count = new AtomicInteger();
    FakeConnectionProvider connector = new FakeConnectionProvider() {
      @Override
      public void init(FakeConnection conn) {
        if (count.getAndIncrement() == 0) {
          conn.close();
        }
      }
    };
    FakeConnectionManager mgr = new FakeConnectionManager(-1, 2, connector);
    FakeWaiter waiter = new FakeWaiter();
    mgr.getConnection(waiter);
    FakeConnection req1 = connector.assertRequest();
    req1.connect();
    FakeConnection req2 = connector.assertRequest();
    req2.connect();
    assertWaitUntil(() -> count.get() == 2);
    assertWaitUntil(waiter::isSuccess);
  }

  private void checkQueueMaxSize(int queueMaxSize, int poolMaxSize) {
    FakeConnectionProvider connector = new FakeConnectionProvider();
    FakeConnectionManager mgr = new FakeConnectionManager(queueMaxSize, poolMaxSize, connector);
    FakeWaiter[] waiters = new FakeWaiter[poolMaxSize + queueMaxSize];
    for (int i = 0;i < poolMaxSize + queueMaxSize;i++) {
      FakeWaiter waiter = new FakeWaiter();
      waiters[i] = waiter;
      mgr.getConnection(waiter);
    }
    FakeWaiter waiter = new FakeWaiter();
    mgr.getConnection(waiter);
    assertWaitUntil(waiter::isFailure);
    for (int i = 0;i < poolMaxSize + queueMaxSize;i++) {
      assertFalse("Was not expecting connection no=" + i + " to be failed", waiters[i].isFailure());
    }
  }

  @Test
  public void testStress() {
    int numActors = 16;
    int numConnections = 1000;

    FakeConnectionProvider connector = new FakeConnectionProvider() {
      @Override
      public void connect(ConnectionListener<FakeConnection> listener, ContextInternal context, Handler<AsyncResult<ConnectResult<FakeConnection>>> handler) {
        int i = ThreadLocalRandom.current().nextInt(100);
        Promise<ConnectResult<FakeConnection>> promise = Promise.promise();
        Future<ConnectResult<FakeConnection>> future = promise.future();
        future.onComplete(handler);
        FakeConnection conn = new FakeConnection(context, listener, promise);
        if (i < 10) {
          conn.fail(new Exception("Could not connect"));
        } else {
          conn.connect();
        }
      }
    };
    FakeConnectionManager mgr = new FakeConnectionManager(-1, numActors, connector);

    Thread[] actors = new Thread[numActors];
    for (int i = 0; i < numActors; i++) {
      actors[i] = new Thread(() -> {
        CountDownLatch latch = new CountDownLatch(numConnections);
        for (int i1 = 0; i1 < numConnections; i1++) {
          mgr.getConnection(new FakeWaiter() {
            @Override
            protected void onFailure() {
              latch.countDown();
            }
            @Override
            protected void onSuccess(FakeConnection conn) {
              int action = ThreadLocalRandom.current().nextInt(100);
              if (action < -1) {
                recycle();
                latch.countDown();
              } else {
                vertx.setTimer(10, id -> {
                  if (action < 15) {
                    conn.close();
                  } else {
                    recycle();
                  }
                  latch.countDown();
                });
              }
            }
          });
        }
        try {
          latch.await();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }, "actor-" + i);
      actors[i].start();
    }

    for (int i = 0; i < actors.length; i++) {
      try {
        actors[i].join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

/*
    try {
      assertWaitUntil(() -> mgr.closed());
    } catch (Exception e) {
      System.out.println(mgr.pool.toString());;
      throw e;
    }
    assertEquals(1, mgr.closeCount());
*/
    // This is synchronous
    new HashSet<>(mgr.active).forEach(FakeConnection::close);
    // Check state at the end
    assertEquals(0, mgr.size());
    mgr.pool.checkInvariants();
    assertEquals(0, mgr.pool.weight());
    assertEquals(0, mgr.pool.capacity());
    assertEquals(0, mgr.pool.waitersInQueue());
  }

  class FakeWaiter {

    protected final ContextInternal context;
    private boolean cancelled;
    private Object result;

    FakeWaiter() {
      context = (ContextInternal) vertx.getOrCreateContext();
    }

    synchronized boolean cancel() {
      if (result != null) {
        return false;
      } else {
        cancelled = true;
        return true;
      }
    }

    synchronized void assertSuccess(FakeConnection conn) {
      assertTrue(result instanceof Lease<?>);
      assertSame(conn, ((Lease<?>)result).get());
    }

    synchronized void assertFailure(Throwable failure) {
      assertSame(failure, result);
    }

    synchronized boolean isComplete() {
      return result != null;
    }

    synchronized boolean isSuccess() {
      return result != null && result instanceof Lease<?>;
    }

    synchronized boolean isFailure() {
      return result != null  && result instanceof Throwable;
    }

    protected void onSuccess(FakeConnection conn) {

    }

    protected void onFailure() {

    }

    private synchronized void handleFailure(Throwable failure) {
      synchronized (this) {
        assertNull(result);
        result = failure;
      }
      onFailure();
    }

    private synchronized void handleConnection(Lease<FakeConnection> lease) {
      boolean recycle;
      synchronized (this) {
        assertNull(result);
        if (!cancelled) {
          result = lease;
          recycle = false;
        } else {
          result = new Object();
          recycle = true;
        }
      }
      if (recycle) {
        lease.recycle();
      } else {
        onSuccess(lease.get());
      }
    }

    void recycle() {
      Lease<?> conn = (Lease<?>) result;
      conn.recycle();
    }
  }

  /*
    class FakeConnnectionPool implements ConnectionPool<FakeConnection>, Function<SocketAddress, ConnectionPool<FakeConnection>> {

      private final SocketAddress address = SocketAddress.inetSocketAddress(8080, "localhost");
      private final int maxSize;
      private final ArrayDeque<FakeConnection> available = new ArrayDeque<>();
      private final Set<FakeConnection> all = new HashSet<>();
      private boolean closed = true;
      private int sequence;

      FakeConnnectionPool(int maxSize) {
        this.maxSize = maxSize;
      }

      @Override
      public Deque<FakeConnection> available() {
        return available;
      }

      @Override
      public Set<FakeConnection> all() {
        return all;
      }

      synchronized int size() {
        return available.size();
      }

      synchronized boolean contains(FakeConnection conn) {
        Deque<ConnectionHolder<FakeConnection>> a = (Deque<ConnectionHolder<FakeConnection>>)(Deque) available;
        for (ConnectionHolder<FakeConnection> b : a) {
          if (b.connection() == conn) {
            return true;
          }
        }
        return false;
      }

      synchronized int sequence() {
        return sequence;
      }

      @Override
      public synchronized FakeConnnectionPool apply(SocketAddress socketAddress) {
        if (!socketAddress.equals(address)) {
          throw new AssertionError();
        }
        if (!closed) {
          throw new AssertionError();
        }
        closed = false;
        sequence++;
        return this;
      }

      @Override
      public synchronized int maxSize() {
        if (closed) {
          throw new AssertionError();
        }
        return maxSize;
      }

      @Override
      public synchronized boolean canBorrow(int connCount) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized FakeConnection pollConnection() {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized boolean canCreateConnection(int connCount) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized boolean initConnection(FakeConnection conn) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized void recycleConnection(FakeConnection conn) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized void evictConnection(FakeConnection conn) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized boolean isValid(FakeConnection conn) {
        throw new UnsupportedOperationException();
      }

      @Override
      public synchronized ContextImpl getContext(FakeConnection conn) {
        throw new UnsupportedOperationException();
      }

      public synchronized void close() {
        if (closed) {
          throw new AssertionError();
        }
        closed = true;
        available.clear();
        all.clear();
      }

      synchronized boolean isClosed() {
        return closed;
      }
    }
  */
  private static class FakeConnection {

    private static final int DISCONNECTED = 0;
    private static final int CONNECTING = 1;
    private static final int CONNECTED = 2;
    private static final int CLOSED = 3;

    private final ContextInternal context;
    private final ConnectionListener<FakeConnection> listener;
    private final Promise<ConnectResult<FakeConnection>> future;

    private long concurrency = 1;
    private int status = DISCONNECTED;
    private boolean valid;

    FakeConnection(ContextInternal context, ConnectionListener<FakeConnection> listener, Promise<ConnectResult<FakeConnection>> future) {
      this.context = context;
      this.listener = listener;
      this.future = future;
      this.valid = true;
    }

    synchronized void close() {
      if (status != CONNECTED) {
        throw new IllegalStateException();
      }
      status = CLOSED;
      listener.onEvict();
    }

    synchronized FakeConnection valid(boolean valid) {
      this.valid = valid;
      return this;
    }

    synchronized FakeConnection concurrency(long value) {
      if (value < 0) {
        throw new IllegalArgumentException("Invalid concurrency");
      }
      if (status == CONNECTED) {
        if (concurrency != value) {
          concurrency = value;
          listener.onConcurrencyChange(value);
        }
      } else {
        concurrency = value;
      }
      return this;
    }

    FakeConnection awaitConnected() {
      waitUntil(() -> {
        synchronized (FakeConnection.this) {
          return status == CONNECTED;
        }
      });
      return this;
    }

    synchronized FakeConnection connect() {
      if (status != DISCONNECTED) {
        throw new IllegalStateException();
      }
      status = CONNECTING;
      context.nettyEventLoop().execute(() -> {
        synchronized (FakeConnection.this) {
          status = CONNECTED;
          future.complete(new ConnectResult<>(this, concurrency, 1));
        }
      });
      return this;
    }

    void fail(Throwable err) {
      context.nettyEventLoop().execute(() -> future.tryFail(err));
    }
  }

  abstract class FakeConnectionProviderBase implements ConnectionProvider<FakeConnection> {

    @Override
    public void init(FakeConnection conn) {
    }

    @Override
    public void close(FakeConnection conn) {
      conn.listener.onEvict();
    }

  }

  class FakeConnectionProvider extends FakeConnectionProviderBase {

    private final Deque<FakeConnection> pendingRequests = new ConcurrentLinkedDeque<>();

    void assertRequests(int expectedSize) {
      assertEquals(expectedSize, pendingRequests.size());
    }

    int requests() {
      return pendingRequests.size();
    }

    boolean hasRequests() {
      return pendingRequests.size() > 0;
    }

    @Override
    public boolean isValid(FakeConnection conn) {
      return conn.valid;
    }

    FakeConnection assertRequest() {
      waitUntil(() -> pendingRequests.size() > 0);
      FakeConnection request = pendingRequests.poll();
      assertNotNull(request);
      return request;
    }

    @Override
    public void connect(ConnectionListener<FakeConnection> listener, ContextInternal context, Handler<AsyncResult<ConnectResult<FakeConnection>>> handler) {
      Promise<ConnectResult<FakeConnection>> promise = Promise.promise();
      promise.future().onComplete(handler);
      pendingRequests.add(new FakeConnection(context, listener, promise));
    }
  }
}
